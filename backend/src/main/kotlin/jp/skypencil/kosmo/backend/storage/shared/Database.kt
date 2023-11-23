package jp.skypencil.kosmo.backend.storage.shared

interface Database {
    suspend fun findTable(name: String): Table

    suspend fun createTable(name: String): Table
}
