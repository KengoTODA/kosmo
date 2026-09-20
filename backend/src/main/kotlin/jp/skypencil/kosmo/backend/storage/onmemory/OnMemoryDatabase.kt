package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.CommitFailure
import jp.skypencil.kosmo.backend.value.CommitOutcomeUnknownException
import jp.skypencil.kosmo.backend.value.CommitResult
import jp.skypencil.kosmo.backend.value.DatabaseUnavailableException
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId
import jp.skypencil.kosmo.backend.wal.TransactionLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class OnMemoryDatabase(
    private val log: TransactionLog? = null,
) : Database {
    private data class TableData(
        val table: OnMemoryTable,
        val rows: Map<RowId, Row> = emptyMap(),
        val revisions: Map<RowId, Long> = emptyMap(),
    )

    private data class Snapshot(
        val revision: Long = 0,
        val tables: Map<String, TableData> = emptyMap(),
    )

    private class Workspace(
        val snapshot: Snapshot,
    ) {
        val operations = mutableListOf<LogEntry>()
        val created = mutableMapOf<String, TableData>()

        // A present null value is a deletion, not an absent change.
        val writes = mutableMapOf<OnMemoryTable, MutableMap<RowId, Row?>>()
    }

    private val lock = Mutex()
    private var current = Snapshot()
    private var failure: Exception? = null

    private fun requireAvailable() {
        failure?.let { throw DatabaseUnavailableException(it) }
    }

    private val active = mutableMapOf<Transaction, Workspace>()

    override suspend fun beginTransaction(): Transaction =
        lock.withLock {
            requireAvailable()
            Transaction(TransactionId.create(), this).also { active[it] = Workspace(current) }
        }

    private fun workspace(tx: Transaction): Workspace {
        requireAvailable()
        require(tx.owner === this) { "Transaction belongs to another database" }
        require(tx.isActive()) { "Given $tx is not active" }
        return checkNotNull(active[tx])
    }

    private fun tableData(
        work: Workspace,
        table: OnMemoryTable,
    ): TableData {
        val data = work.created[table.getName()] ?: work.snapshot.tables[table.getName()]
        check(data?.table === table) { "$table is not visible in this transaction" }
        return checkNotNull(data)
    }

    override suspend fun findTable(
        tx: Transaction,
        name: String,
    ): Table =
        lock.withLock {
            val work = workspace(tx)
            checkNotNull(work.created[name] ?: work.snapshot.tables[name]) { "Table $name does not exist" }.table
        }

    override suspend fun createTable(
        tx: Transaction,
        name: String,
    ): Table =
        lock.withLock {
            val work = workspace(tx)
            require(name !in work.snapshot.tables && name !in work.created) { "Table $name already exists" }
            OnMemoryTable(name, this).also {
                work.created[name] = TableData(it)
                work.operations.add(LogEntry.CreateTable(tx.id, name))
            }
        }

    private fun row(
        work: Workspace,
        table: OnMemoryTable,
        id: RowId,
    ): Row? {
        val base = tableData(work, table)
        val changes = work.writes[table]
        return if (changes != null && changes.containsKey(id)) changes[id] else base.rows[id]
    }

    internal suspend fun find(
        tx: Transaction,
        table: OnMemoryTable,
        id: RowId,
    ): Row =
        lock.withLock {
            checkNotNull(row(workspace(tx), table, id)) { "$table does not contain $id" }
        }

    internal suspend fun scan(
        tx: Transaction,
        table: OnMemoryTable,
    ): Sequence<Row> =
        lock.withLock {
            val work = workspace(tx)
            val rows = tableData(work, table).rows.toMutableMap()
            work.writes[table]?.forEach { (id, row) -> if (row == null) rows.remove(id) else rows[id] = row }
            rows.values.toList().asSequence()
        }

    internal suspend fun insert(
        tx: Transaction,
        table: OnMemoryTable,
        row: Row,
    ): Unit =
        lock.withLock {
            val work = workspace(tx)
            check(row(work, table, row.id) == null) { "$table already has $row" }
            work.writes.getOrPut(table) { mutableMapOf() }[row.id] = row
            work.operations.add(LogEntry.Insert(tx.id, table.getName(), row))
        }

    internal suspend fun update(
        tx: Transaction,
        table: OnMemoryTable,
        row: Row,
    ): Unit =
        lock.withLock {
            val work = workspace(tx)
            checkNotNull(row(work, table, row.id)) { "$table does not contain ${row.id}" }
            work.writes.getOrPut(table) { mutableMapOf() }[row.id] = row
            work.operations.add(LogEntry.Update(tx.id, table.getName(), row))
        }

    internal suspend fun delete(
        tx: Transaction,
        table: OnMemoryTable,
        id: RowId,
    ): Boolean =
        lock.withLock {
            val work = workspace(tx)
            if (row(work, table, id) == null) return@withLock false
            work.writes.getOrPut(table) { mutableMapOf() }[id] = null
            work.operations.add(LogEntry.Delete(tx.id, table.getName(), id))
            true
        }

    override suspend fun commit(tx: Transaction): CommitResult =
        lock.withLock {
            val work = workspace(tx)
            val conflict = conflict(work)
            if (conflict != null) {
                active.remove(tx)
                tx.state = Transaction.State.ABORTED
                return@withLock CommitResult.Aborted(conflict)
            }
            val revision = Math.incrementExact(current.revision)
            val tables = current.tables.toMutableMap()
            tables.putAll(work.created)
            work.writes.forEach { (table, changes) ->
                val data = checkNotNull(tables[table.getName()])
                val rows = data.rows.toMutableMap()
                val revisions = data.revisions.toMutableMap()
                changes.forEach { (id, row) ->
                    if (row == null) rows.remove(id) else rows[id] = row
                    // Retain revisions for deletions to detect insert/delete ABA changes.
                    revisions[id] = revision
                }
                tables[table.getName()] = data.copy(rows = rows.toMap(), revisions = revisions.toMap())
            }
            val next = Snapshot(revision, tables.toMap())
            currentCoroutineContext().ensureActive()
            // Once log I/O starts, finish publication even if the request is cancelled.
            withContext(NonCancellable) {
                try {
                    if (work.operations.isNotEmpty()) log?.appendTransaction(tx.id, work.operations.toList())
                } catch (cause: Exception) {
                    failure = cause
                    active.keys.forEach { it.state = Transaction.State.ABORTED }
                    active.clear()
                    tx.state = Transaction.State.IN_DOUBT
                    if (cause is CancellationException) throw cause
                    throw CommitOutcomeUnknownException(tx.id, cause)
                }
                current = next
                active.remove(tx)
                tx.state = Transaction.State.COMMITTED
                CommitResult.Committed
            }
        }

    private fun conflict(work: Workspace): CommitFailure? {
        work.created.keys.forEach { name ->
            if (name in current.tables) return CommitFailure.TableNameConflict(name)
        }
        work.writes.forEach { (table, changes) ->
            if (table.getName() !in work.created) {
                val latest = checkNotNull(current.tables[table.getName()])
                changes.keys.forEach { id ->
                    if ((latest.revisions[id] ?: 0) > work.snapshot.revision) {
                        return CommitFailure.WriteConflict(table.getName(), id)
                    }
                }
            }
        }
        return null
    }

    override suspend fun rollback(tx: Transaction) =
        lock.withLock {
            workspace(tx)
            active.remove(tx)
            tx.state = Transaction.State.ABORTED
        }
}
