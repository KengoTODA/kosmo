package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnMemoryTable(private val name: String) : Table {
    private val lock = Mutex()
    private val map = mutableMapOf<RowId, MutableMap<Transaction, Row>>()

    override fun getName(): String = name

    /**
     * @return the effective [Row] for the specified [TransactionId]. null-able.
     */
    private fun snapshotAt(
        current: Transaction,
        id: RowId,
    ): Row? =
        checkNotNull(map[id]).entries.findLast {
            it.key.isVisibleFor(current)
        }?.value

    override suspend fun find(
        tx: Transaction,
        id: RowId,
    ): Row =
        lock.withLock {
            requireActiveTransaction(tx)
            checkNotNull(map[id]) {
                "$this does not contain $id"
            }
            checkNotNull(snapshotAt(tx, id)) {
                "$this does not contain $id"
            }
        }

    override suspend fun tableScan(tx: Transaction): Sequence<Row> =
        lock.withLock {
            requireActiveTransaction(tx)
            map.entries.mapNotNull {
                snapshotAt(tx, it.key)
            }.asSequence()
        }

    override suspend fun insert(
        tx: Transaction,
        row: Row,
    ) {
        lock.withLock {
            requireActiveTransaction(tx)
            check(map[row.id] == null) {
                "$this already has $row"
            }
            map[row.id] = mutableMapOf(Pair(tx, row))
        }
    }

    override suspend fun delete(
        tx: Transaction,
        id: RowId,
    ): Boolean =
        lock.withLock {
            requireActiveTransaction(tx)
            map.remove(id) != null
        }

    override suspend fun update(
        tx: Transaction,
        row: Row,
    ) {
        lock.withLock {
            requireActiveTransaction(tx)
            val history =
                checkNotNull(map[row.id]) {
                    "$this does not contain ${row.id}"
                }
            history[tx] = row
        }
    }

    override fun toString() = "Table(name=$name)"

    private fun requireActiveTransaction(tx: Transaction) {
        require(tx.isActive()) {
            "Given $tx is not active"
        }
    }
}
