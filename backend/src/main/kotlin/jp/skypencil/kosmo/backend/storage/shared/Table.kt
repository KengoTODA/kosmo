package jp.skypencil.kosmo.backend.storage.shared

import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId

interface Table {
    fun getName(): String

    suspend fun find(
        tx: TransactionId,
        id: RowId,
    ): Row

    suspend fun tableScan(tx: TransactionId): Sequence<Row>

    suspend fun insert(
        tx: TransactionId,
        row: Row,
    )

    suspend fun delete(
        tx: TransactionId,
        id: RowId,
    ): Boolean

    suspend fun update(
        tx: TransactionId,
        row: Row,
    )
}
