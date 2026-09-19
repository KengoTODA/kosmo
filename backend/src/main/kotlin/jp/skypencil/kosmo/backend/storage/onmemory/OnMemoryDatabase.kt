package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Transaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
class OnMemoryDatabase : Database {
    private data class CreatedTable(
        val transaction: Transaction,
        val table: OnMemoryTable,
    )

    private val lock = Mutex()
    private val tables = mutableMapOf<String, CreatedTable>()

    override suspend fun findTable(
        tx: Transaction,
        name: String,
    ): Table =
        lock.withLock {
            require(tx.isActive()) { "Given $tx is not active" }
            val created = checkNotNull(tables[name]) { "Table $name does not exist" }
            check(created.transaction.isVisibleFor(tx)) { "Table $name is not visible for $tx" }
            created.table
        }

    override suspend fun createTable(
        tx: Transaction,
        name: String,
    ): Table =
        lock.withLock {
            require(tx.isActive()) { "Given $tx is not active" }
            val previous = tables[name]
            require(previous == null || (!previous.transaction.isActive() && !previous.transaction.isCommitted())) {
                "Table $name already exists"
            }
            OnMemoryTable(name, tx).also {
                tables[name] = CreatedTable(tx, it)
            }
        }
}
