package jp.skypencil.kosmo.backend.storage.shared

import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction

interface Table {
    fun getName(): String

    suspend fun find(
        tx: Transaction,
        id: RowId,
    ): Row

    suspend fun tableScan(tx: Transaction): Sequence<Row>

    suspend fun insert(
        tx: Transaction,
        row: Row,
    )

    suspend fun delete(
        tx: Transaction,
        id: RowId,
    ): Boolean

    suspend fun update(
        tx: Transaction,
        row: Row,
    )
}
