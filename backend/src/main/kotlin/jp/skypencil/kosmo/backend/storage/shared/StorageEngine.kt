package jp.skypencil.kosmo.backend.storage.shared

import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId

/**
 * データや索引の管理を抽象化する。
 */
interface StorageEngine {
    suspend fun find(id: RowId): Row
    suspend fun tableScan(): Sequence<Row>
    suspend fun indexScan(): Sequence<Row>
}
