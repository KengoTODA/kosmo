package jp.skypencil.kosmo.backend.value

import jp.skypencil.kosmo.backend.storage.onmemory.TransactionManager

data class Transaction(
    val id: TransactionId,
    private val transactionManager: TransactionManager,
) {
    fun isVisibleFor(another: Transaction): Boolean {
        check(another.transactionManager == transactionManager) {
            "$another should be managed by the same TransactionManager with $this"
        }
        return id == another.id || (id < another.id && transactionManager.isCommitted(id, another.id))
    }

    fun isActive(): Boolean = transactionManager.checkActive(this)

    override fun toString(): String = "Transaction(id=$id)"
}
