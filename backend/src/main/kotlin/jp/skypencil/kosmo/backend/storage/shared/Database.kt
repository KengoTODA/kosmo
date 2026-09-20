package jp.skypencil.kosmo.backend.storage.shared

import jp.skypencil.kosmo.backend.value.CommitResult
import jp.skypencil.kosmo.backend.value.Transaction

interface Database {
    suspend fun beginTransaction(): Transaction

    suspend fun commit(tx: Transaction): CommitResult

    suspend fun rollback(tx: Transaction)

    suspend fun findTable(
        tx: Transaction,
        name: String,
    ): Table

    suspend fun createTable(
        tx: Transaction,
        name: String,
    ): Table
}
