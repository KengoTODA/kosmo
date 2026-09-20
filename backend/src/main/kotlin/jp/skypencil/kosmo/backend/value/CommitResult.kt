package jp.skypencil.kosmo.backend.value

/** Aborted guarantees that no changes from this transaction were published. */
sealed interface CommitResult {
    data object Committed : CommitResult

    data class Aborted(
        val reason: CommitFailure,
    ) : CommitResult
}

sealed interface CommitFailure {
    data class WriteConflict(
        val tableName: String,
        val rowId: RowId,
    ) : CommitFailure

    data class TableNameConflict(
        val tableName: String,
    ) : CommitFailure
}
