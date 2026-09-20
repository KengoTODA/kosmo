package jp.skypencil.kosmo.backend.wal

import jp.skypencil.kosmo.backend.storage.onmemory.OnMemoryDatabase
import jp.skypencil.kosmo.backend.storage.onmemory.TransactionManager
import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

/**
 * Rebuilds a fresh database from a finite stream, publishing it only on successful completion.
 * Stream completion means the end of recovery, not a temporary network disconnection.
 */
class LogReplayer {
    private val logger = LoggerFactory.getLogger(LogReplayer::class.java)

    suspend fun replay(entries: Flow<LogEntry>): ReplayResult {
        val database = OnMemoryDatabase()
        val transactions = TransactionManager(database)
        val pending = mutableMapOf<TransactionId, MutableList<LogEntry>>()
        val committed = mutableSetOf<TransactionId>()

        entries.collect { entry ->
            check(entry.txId !in committed) { "Record after Commit for ${entry.txId}" }
            when (entry) {
                is LogEntry.Commit -> {
                    val tx = transactions.create()
                    try {
                        pending.remove(entry.txId).orEmpty().forEach { apply(database, tx, it) }
                        transactions.commit(tx)
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (cause: Exception) {
                        // The database is private to this replay; never expose partially applied recovery.
                        throw LogReplayException(entry.txId, cause)
                    }
                    committed.add(entry.txId)
                }

                is LogEntry.CreateTable,
                is LogEntry.Insert,
                is LogEntry.Update,
                is LogEntry.Delete,
                -> {
                    pending.getOrPut(entry.txId) { mutableListOf() }.add(entry)
                }
            }
        }

        pending.forEach { (txId, operations) ->
            logger.warn("Discarding transaction {} with {} operations and no Commit at end of log", txId, operations.size)
        }
        return ReplayResult(database, transactions, committed.size, pending.keys.toSet())
    }

    private suspend fun apply(
        database: Database,
        tx: Transaction,
        entry: LogEntry,
    ) {
        when (entry) {
            is LogEntry.CreateTable -> {
                database.createTable(tx, entry.tableName)
            }

            is LogEntry.Insert -> {
                database.findTable(tx, entry.tableName).insert(tx, entry.row)
            }

            is LogEntry.Update -> {
                database.findTable(tx, entry.tableName).update(tx, entry.row)
            }

            is LogEntry.Delete -> {
                check(database.findTable(tx, entry.tableName).delete(tx, entry.rowId)) {
                    "Cannot delete missing row ${entry.rowId} from ${entry.tableName}"
                }
            }

            is LogEntry.Commit -> {
                error("Commit cannot appear inside a transaction's operations")
            }
        }
    }
}

data class ReplayResult(
    val database: Database,
    val transactions: TransactionManager,
    val committedTransactions: Int,
    val discardedTransactions: Set<TransactionId>,
)

class LogReplayException(
    val txId: TransactionId,
    cause: Exception,
) : IllegalStateException("Cannot replay transaction $txId", cause)
