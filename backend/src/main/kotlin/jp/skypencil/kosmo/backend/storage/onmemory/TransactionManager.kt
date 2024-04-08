package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId

class TransactionManager {
    private val activeTransactions: MutableSet<TransactionId> = sortedSetOf()

    /**
     * key: the committed transaction
     * value: the newest transaction at the commit
     */
    private val committed: MutableMap<TransactionId, TransactionId> = mutableMapOf()

    private fun newestActiveTransactions() = activeTransactions.last()

    fun create(): Transaction {
        val id =
            TransactionId.create().also {
                activeTransactions.add(it)
            }
        return Transaction(id, this)
    }

    fun isCommitted(
        target: TransactionId,
        current: TransactionId,
    ): Boolean = current > target && committed.contains(target) && committed[target]!! < current

    fun commit(tx: Transaction) {
        committed[tx.id] = newestActiveTransactions()
        activeTransactions.remove(tx.id)
    }

    fun rollback(tx: Transaction) {
        activeTransactions.remove(tx.id)
    }
}
