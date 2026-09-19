package jp.skypencil.kosmo.backend.value

import kotlinx.serialization.Serializable

@Serializable
data class Row(
    val id: RowId,
)
