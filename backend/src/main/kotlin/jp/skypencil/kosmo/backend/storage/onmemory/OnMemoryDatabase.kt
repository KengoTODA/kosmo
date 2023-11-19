package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.storage.shared.Database

class OnMemoryDatabase : Database {
    private val tables = mutableMapOf<String, OnMemoryTable>()
    override suspend fun findTable(name: String): Table =
        checkNotNull(tables[name])

    override suspend fun createTable(name: String): Table {
        require(!tables.containsKey(name))
        return OnMemoryTable(name).also {
            tables[name] = it
        }
    }
}
