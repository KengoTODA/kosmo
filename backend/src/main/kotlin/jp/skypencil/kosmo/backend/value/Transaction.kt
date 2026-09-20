package jp.skypencil.kosmo.backend.value

import jp.skypencil.kosmo.backend.storage.shared.Database

/** A database-owned handle; snapshots and pending changes belong to the database. */
class Transaction internal constructor(
    val id: TransactionId,
    internal val owner: Database,
) {
    internal enum class State { ACTIVE, COMMITTED, ABORTED }

    @Volatile
    internal var state = State.ACTIVE

    fun isActive(): Boolean = state == State.ACTIVE

    fun isCommitted(): Boolean = state == State.COMMITTED

    override fun toString(): String = "Transaction(id=$id)"
}
