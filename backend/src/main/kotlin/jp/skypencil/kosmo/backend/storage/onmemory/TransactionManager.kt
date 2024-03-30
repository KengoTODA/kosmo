package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.value.TransactionId

class TransactionManager {
    private val committed: MutableSet<TransactionId> = mutableSetOf()

    fun isCommitted(tx: TransactionId): Boolean = committed.contains(tx)

    fun commit(tx: TransactionId) {
        committed.add(tx)
    }
}
