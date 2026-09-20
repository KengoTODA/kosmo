package jp.skypencil.kosmo.backend.value

import java.io.IOException

/** The log may contain Commit. Do not retry blindly or report a successful rollback. */
class CommitOutcomeUnknownException(
    val txId: TransactionId,
    cause: Exception,
) : IOException("Commit outcome for $txId is unknown; database recovery is required", cause)

class DatabaseUnavailableException(
    cause: Exception,
) : IllegalStateException("Database is unavailable; recover it from the log before further use", cause)
