package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.value.Transaction

/** Entry points delegate transaction protocols to the selected database implementation. */
class TransactionManager(
    private val database: Database,
) {
    suspend fun create(): Transaction = database.beginTransaction()

    suspend fun commit(tx: Transaction) = database.commit(tx)

    suspend fun rollback(tx: Transaction) = database.rollback(tx)
}
