package jp.skypencil.kosmo.backend.value

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

class TransactionId(private val uuid: UUID) : Comparable<TransactionId> {
    companion object {
        fun create() = TransactionId(UuidCreator.getTimeOrdered())
    }

    override fun compareTo(other: TransactionId): Int = uuid.compareTo(other.uuid)
}
