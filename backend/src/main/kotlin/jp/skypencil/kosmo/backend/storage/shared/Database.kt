package jp.skypencil.kosmo.backend.storage.shared

import jp.skypencil.kosmo.backend.value.Transaction

interface Database {
    suspend fun findTable(
        tx: Transaction,
        name: String,
    ): Table

    suspend fun createTable(
        tx: Transaction,
        name: String,
    ): Table
}
