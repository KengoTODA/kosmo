package jp.skypencil.kosmo.backend.value

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

data class RowId(
    private val uuid: UUID,
) : Comparable<RowId> {
    init {
        check(uuid.version() == 6) {
            "RowIdはTime-basedなUUIDである必要があります"
        }
    }

    override fun compareTo(other: RowId): Int = this.uuid.compareTo(other.uuid)

    companion object {
        fun create() = RowId(UuidCreator.getTimeOrdered())
    }
}
