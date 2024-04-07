package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.value.TransactionId

class TransactionManager {
    private val activeTransactions: MutableSet<TransactionId> = sortedSetOf()

    /**
     * key: the committed transaction
     * value: the newest transaction at the commit
     */
    private val committed: MutableMap<TransactionId, TransactionId> = mutableMapOf()

    private fun newestActiveTransactions() = activeTransactions.last()

    fun create() =
        TransactionId.create().also {
            activeTransactions.add(it)
        }

    fun isCommitted(
        target: TransactionId,
        current: TransactionId,
    ): Boolean = current > target && committed.contains(target) && committed[target]!! < current

    fun commit(tx: TransactionId) {
        committed[tx] = newestActiveTransactions()
        activeTransactions.remove(tx)
    }

    fun rollback(tx: TransactionId) {
        activeTransactions.remove(tx)
    }
}
