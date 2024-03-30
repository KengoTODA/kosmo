package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnMemoryTable(private val name: String, private val transactionManager: TransactionManager) : Table {
    private val lock = Mutex()
    private val map = mutableMapOf<RowId, MutableMap<TransactionId, Row>>()

    override fun getName(): String = name

    /**
     * @return the effective [Row] for the specified [TransactionId]. null-able.
     */
    private fun snapshotAt(
        current: TransactionId,
        id: RowId,
    ): Row? =
        checkNotNull(map[id]).entries.findLast {
            it.key == current || it.key < current && transactionManager.isCommitted(it.key, current)
        }?.value

    override suspend fun find(
        tx: TransactionId,
        id: RowId,
    ): Row =
        lock.withLock {
            checkNotNull(map[id]) {
                "$this does not contain $id"
            }
            checkNotNull(snapshotAt(tx, id)) {
                "$this does not contain $id"
            }
        }

    override suspend fun tableScan(tx: TransactionId): Sequence<Row> =
        lock.withLock {
            map.entries.mapNotNull {
                snapshotAt(tx, it.key)
            }.asSequence()
        }

    override suspend fun insert(
        tx: TransactionId,
        row: Row,
    ) {
        lock.withLock {
            check(map[row.id] == null) {
                "$this already has $row"
            }
            map[row.id] = mutableMapOf(Pair(tx, row))
        }
    }

    override suspend fun delete(
        tx: TransactionId,
        id: RowId,
    ): Boolean = lock.withLock { map.remove(id) != null }

    override suspend fun update(
        tx: TransactionId,
        row: Row,
    ) {
        lock.withLock {
            val history =
                checkNotNull(map[row.id]) {
                    "$this does not contain ${row.id}"
                }
            history[tx] = row
        }
    }

    override fun toString() = "Table(name=$name)"
}
