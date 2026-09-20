package jp.skypencil.kosmo.backend.wal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.storage.onmemory.OnMemoryDatabase
import jp.skypencil.kosmo.backend.storage.shared.Database
import jp.skypencil.kosmo.backend.value.CommitFailure
import jp.skypencil.kosmo.backend.value.CommitResult
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import kotlin.io.path.writeText

class LogReplayerSpec :
    DescribeSpec({
        it("reports typed commit failures with the original source transaction ID and stops") {
            val source = TransactionId.create()
            val reason = CommitFailure.TableNameConflict("example")
            var reachedNext = false
            val replayer =
                LogReplayer {
                    val database = OnMemoryDatabase()
                    object : Database by database {
                        override suspend fun commit(tx: Transaction): CommitResult {
                            database.rollback(tx)
                            return CommitResult.Aborted(reason)
                        }
                    }
                }
            val input =
                flow {
                    emit(LogEntry.CreateTable(source, "example"))
                    emit(LogEntry.Commit(source))
                    reachedNext = true
                    emit(LogEntry.Commit(TransactionId.create()))
                }
            val failure = shouldThrow<LogReplayException> { replayer.replay(input) }
            failure.txId shouldBe source
            failure.commitFailure shouldBe reason
            reachedNext shouldBe false
        }

        it("reconstructs row values across multiple committed updates and rollback tails") {
            val first = TransactionId.create()
            val second = TransactionId.create()
            val tail = TransactionId.create()
            val row = Row(RowId.create(), "initial")
            val entries =
                listOf(
                    LogEntry.CreateTable(first, "example"),
                    LogEntry.Insert(first, "example", row),
                    LogEntry.Commit(first),
                    LogEntry.Update(second, "example", row.copy(value = "committed")),
                    LogEntry.Commit(second),
                    LogEntry.Update(tail, "example", row.copy(value = "unfinished")),
                )
            val result = LogReplayer().replay(entries.asFlow())
            val tx = result.transactions.create()
            result.database
                .findTable(tx, "example")
                .find(tx, row.id)
                .value shouldBe "committed"
            result.transactions.commit(tx) shouldBe CommitResult.Committed
            result.discardedTransactions shouldBe setOf(tail)
        }

        it("recovers a closed log across rotations preserving original row IDs") {
            val directory = tempdir().toPath()
            val txId = TransactionId.create()
            val rows = (1..1_001).map { Row(RowId.create()) }
            LogWriter(directory).use { writer ->
                writer.write(LogEntry.CreateTable(txId, "example"))
                rows.forEach { writer.write(LogEntry.Insert(txId, "example", it)) }
                writer.write(LogEntry.Update(txId, "example", rows.first()))
                writer.write(LogEntry.Delete(txId, "example", rows.last().id))
                writer.write(LogEntry.Commit(txId))
            }

            val result = LogReplayer().replay(LogReader().readDirectory(directory))
            result.committedTransactions shouldBe 1
            result.discardedTransactions shouldBe emptySet()
            val reader = result.transactions.create()
            result.database
                .findTable(reader, "example")
                .tableScan(reader)
                .toList() shouldBe rows.dropLast(1)
        }

        it("replays interleaved transactions in commit order, not transaction ID order") {
            val older = TransactionId.create()
            val newer = TransactionId.create()
            val row = Row(RowId.create())
            val entries =
                listOf(
                    LogEntry.CreateTable(newer, "example"),
                    LogEntry.Insert(older, "example", row),
                    LogEntry.Commit(newer),
                    LogEntry.Commit(older),
                )
            val result = LogReplayer().replay(entries.asFlow())
            result.committedTransactions shouldBe 2
            val reader = result.transactions.create()
            result.database.findTable(reader, "example").find(reader, row.id) shouldBe row
        }

        it("drops uncommitted changes without modifying committed data") {
            val committed = TransactionId.create()
            val unfinished = TransactionId.create()
            val row = Row(RowId.create())
            val entries =
                listOf(
                    LogEntry.CreateTable(committed, "example"),
                    LogEntry.Insert(committed, "example", row),
                    LogEntry.Delete(unfinished, "example", row.id),
                    LogEntry.CreateTable(unfinished, "invisible"),
                    LogEntry.Commit(committed),
                )
            val result = LogReplayer().replay(entries.asFlow())
            result.discardedTransactions shouldBe setOf(unfinished)
            val reader = result.transactions.create()
            result.database.findTable(reader, "example").find(reader, row.id) shouldBe row
            shouldThrow<IllegalStateException> { result.database.findTable(reader, "invisible") }
        }

        it("drops a transaction whose final Commit is incomplete") {
            val file = tempdir().toPath().resolve("tail.json")
            val txId = TransactionId.create()
            file.writeText(LogEntry.CreateTable(txId, "example").toJson() + "\n" + LogEntry.Commit(txId).toJson().dropLast(2))
            val result = LogReplayer().replay(LogReader().read(listOf(file)))
            result.committedTransactions shouldBe 0
            result.discardedTransactions shouldBe setOf(txId)
            shouldThrow<IllegalStateException> { result.database.findTable(result.transactions.create(), "example") }
        }

        it("accepts empty input and empty committed transactions") {
            val replayer = LogReplayer()
            replayer.replay(emptyFlow()).committedTransactions shouldBe 0
            replayer.replay(flowOf(LogEntry.Commit(TransactionId.create()))).committedTransactions shouldBe 1
        }

        it("does not return a partially recovered database when a committed transaction fails") {
            val first = TransactionId.create()
            val broken = TransactionId.create()
            val row = Row(RowId.create())
            val entries =
                listOf(
                    LogEntry.CreateTable(first, "example"),
                    LogEntry.Insert(first, "example", row),
                    LogEntry.Commit(first),
                    LogEntry.Delete(broken, "example", row.id),
                    LogEntry.Insert(broken, "missing", row),
                    LogEntry.Commit(broken),
                )
            val replayer = LogReplayer()
            val exception = shouldThrow<LogReplayException> { replayer.replay(entries.asFlow()) }
            exception.txId shouldBe broken

            // A fresh attempt starts from an empty database, not the failed attempt's partial state.
            val recovered = replayer.replay(entries.take(3).asFlow())
            val reader = recovered.transactions.create()
            recovered.database.findTable(reader, "example").find(reader, row.id) shouldBe row
        }

        it("rejects duplicate commits and operations after a commit") {
            val txId = TransactionId.create()
            shouldThrow<IllegalStateException> {
                LogReplayer().replay(flowOf(LogEntry.Commit(txId), LogEntry.Commit(txId)))
            }
            shouldThrow<IllegalStateException> {
                LogReplayer().replay(flowOf(LogEntry.Commit(txId), LogEntry.CreateTable(txId, "late")))
            }
        }

        it("rejects updates and deletes of missing rows in committed transactions") {
            val txId = TransactionId.create()
            val row = Row(RowId.create())
            val changes = listOf(LogEntry.Update(txId, "example", row), LogEntry.Delete(txId, "example", row.id))
            changes.forEach { change ->
                shouldThrow<LogReplayException> {
                    LogReplayer().replay(flowOf(LogEntry.CreateTable(txId, "example"), change, LogEntry.Commit(txId)))
                }
            }
        }

        it("propagates upstream errors instead of treating a broken stream as successful completion") {
            val failure = IOException("input failed")
            val entries =
                flow {
                    emit(LogEntry.CreateTable(TransactionId.create(), "unfinished"))
                    throw failure
                }
            shouldThrow<IOException> { LogReplayer().replay(entries) } shouldBe failure
        }

        it("propagates cancellation instead of returning a recovered database") {
            val cancelled = CancellationException("cancelled")
            val entries =
                flow {
                    emit(LogEntry.Commit(TransactionId.create()))
                    throw cancelled
                }
            shouldThrow<CancellationException> { LogReplayer().replay(entries) } shouldBe cancelled
        }
    })
