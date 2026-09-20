package jp.skypencil.kosmo.backend.wal

import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.TransactionId

/** Returns only after operations and the Commit record have been forced to storage. */
fun interface TransactionLog {
    suspend fun appendTransaction(
        txId: TransactionId,
        operations: List<LogEntry>,
    )
}
