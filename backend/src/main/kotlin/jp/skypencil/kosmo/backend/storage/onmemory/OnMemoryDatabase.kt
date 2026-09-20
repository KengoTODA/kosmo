package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
class OnMemoryDatabase : Database {
    private data class TableData(
        val table: OnMemoryTable,
        val rows: Map<RowId, Row> = emptyMap(),
    )

    private data class Snapshot(
        val tables: Map<String, TableData> = emptyMap(),
    )

    private class Workspace(
        val snapshot: Snapshot,
    ) {
        val created = mutableMapOf<String, TableData>()

        // A present null value is a deletion, not an absent change.
        val writes = mutableMapOf<OnMemoryTable, MutableMap<RowId, Row?>>()
    }

    private val lock = Mutex()
    private var current = Snapshot()
    private val active = mutableMapOf<Transaction, Workspace>()

    override suspend fun beginTransaction(): Transaction =
        lock.withLock {
            Transaction(TransactionId.create(), this).also { active[it] = Workspace(current) }
        }

    private fun workspace(tx: Transaction): Workspace {
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
            require(name !in current.tables && active.values.none { name in it.created }) { "Table $name already exists" }
            OnMemoryTable(name, this).also { work.created[name] = TableData(it) }
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
    ) = lock.withLock {
        val work = workspace(tx)
        check(row(work, table, row.id) == null) { "$table already has $row" }
        work.writes.getOrPut(table) { mutableMapOf() }[row.id] = row
    }

    internal suspend fun update(
        tx: Transaction,
        table: OnMemoryTable,
        row: Row,
    ) = lock.withLock {
        val work = workspace(tx)
        checkNotNull(row(work, table, row.id)) { "$table does not contain ${row.id}" }
        work.writes.getOrPut(table) { mutableMapOf() }[row.id] = row
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
            true
        }

    override suspend fun commit(tx: Transaction) =
        lock.withLock {
            val work = workspace(tx)
            val tables = current.tables.toMutableMap()
            tables.putAll(work.created)
            work.writes.forEach { (table, changes) ->
                val data = checkNotNull(tables[table.getName()])
                val rows = data.rows.toMutableMap()
                changes.forEach { (id, row) -> if (row == null) rows.remove(id) else rows[id] = row }
                tables[table.getName()] = data.copy(rows = rows.toMap())
            }
            current = Snapshot(tables.toMap())
            active.remove(tx)
            tx.state = Transaction.State.COMMITTED
        }

    override suspend fun rollback(tx: Transaction) =
        lock.withLock {
            workspace(tx)
            active.remove(tx)
            tx.state = Transaction.State.ABORTED
        }
}
