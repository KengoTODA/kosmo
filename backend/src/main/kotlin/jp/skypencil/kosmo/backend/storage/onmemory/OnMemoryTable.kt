package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId
import java.util.Collections.synchronizedMap

class OnMemoryTable(private val name: String, private val transactionManager: TransactionManager) : Table {
    private val map = synchronizedMap(mutableMapOf<RowId, MutableMap<TransactionId, Row>>())

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
    ): Row {
        checkNotNull(map[id]) {
            "$this does not contain $id"
        }
        return checkNotNull(snapshotAt(tx, id)) {
            "$this does not contain $id"
        }
    }

    override suspend fun tableScan(tx: TransactionId): Sequence<Row> =
        map.entries.mapNotNull {
            snapshotAt(tx, it.key)
        }.asSequence()

    override suspend fun insert(
        tx: TransactionId,
        row: Row,
    ) {
        check(map[row.id] == null) {
            "$this already has $row"
        }
        map[row.id] = mutableMapOf(Pair(tx, row))
    }

    override suspend fun delete(
        tx: TransactionId,
        id: RowId,
    ): Boolean = map.remove(id) != null

    override suspend fun update(
        tx: TransactionId,
        row: Row,
    ) {
        val history =
            checkNotNull(map[row.id]) {
                "$this does not contain ${row.id}"
            }
        history[tx] = row
    }

    override fun toString() = "Table(name=$name)"
}
