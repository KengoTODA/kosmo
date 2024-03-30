package jp.skypencil.kosmo.backend.value

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

data class TransactionId(private val uuid: UUID) : Comparable<TransactionId> {
    init {
        check(uuid.version() == 6) {
            "TransactionId should be Time-based UUID"
        }
    }

    override fun compareTo(other: TransactionId): Int = uuid.compareTo(other.uuid)

    companion object {
        fun create() = TransactionId(UuidCreator.getTimeOrdered())
    }
}
