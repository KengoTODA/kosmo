package jp.skypencil.kosmo.backend.value

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One operation in a transaction. Only transactions with a [Commit] record may be published during replay.
 * Row images and IDs are supplied by the primary; replay must not generate replacement IDs.
 */
@Serializable
sealed class LogEntry {
    abstract val txId: TransactionId

    fun toJson(): String = Json.encodeToString(Record(version = 1, entry = this))

    companion object {
        fun fromJson(json: String): LogEntry {
            val record = Json.decodeFromString<Record>(json)
            require(record.version == 1) { "Unsupported log version: ${record.version}" }
            return record.entry
        }
    }

    @Serializable
    private data class Record(
        val version: Int,
        val entry: LogEntry,
    )

    @Serializable
    @SerialName("create_table")
    data class CreateTable(
        override val txId: TransactionId,
        val tableName: String,
    ) : LogEntry()

    @Serializable
    @SerialName("insert")
    data class Insert(
        override val txId: TransactionId,
        val tableName: String,
        val row: Row,
    ) : LogEntry()

    /** The complete row after the update, rather than an expression to evaluate again. */
    @Serializable
    @SerialName("update")
    data class Update(
        override val txId: TransactionId,
        val tableName: String,
        val row: Row,
    ) : LogEntry()

    @Serializable
    @SerialName("delete")
    data class Delete(
        override val txId: TransactionId,
        val tableName: String,
        val rowId: RowId,
    ) : LogEntry()

    @Serializable
    @SerialName("commit")
    data class Commit(
        override val txId: TransactionId,
    ) : LogEntry()
}
