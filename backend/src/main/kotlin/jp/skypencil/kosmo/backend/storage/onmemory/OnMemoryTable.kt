package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlin.streams.asSequence

// TODO トランザクション
// TODO スレッドセーフティ
class OnMemoryTable(private val name: String) : Table {
    private val map = mutableMapOf<RowId, Row>()

    override fun getName(): String = name

    override suspend fun find(
        transactionId: TransactionId,
        id: RowId,
    ): Row = checkNotNull(map[id])

    override suspend fun tableScan(transactionId: TransactionId): Sequence<Row> = map.values.stream().asSequence()

    override suspend fun insert(
        transactionId: TransactionId,
        row: Row,
    ) {
        check(map[row.id] == null)
        map[row.id] = row
    }

    override suspend fun delete(
        transactionId: TransactionId,
        id: RowId,
    ): Boolean = map.remove(id) != null

    override suspend fun update(
        transactionId: TransactionId,
        row: Row,
    ) {
        checkNotNull(map[row.id])
        map[row.id] = row
    }
}
