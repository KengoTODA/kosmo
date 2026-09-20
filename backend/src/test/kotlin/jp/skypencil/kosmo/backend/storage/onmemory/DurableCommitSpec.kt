package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.CommitOutcomeUnknownException
import jp.skypencil.kosmo.backend.value.CommitResult
import jp.skypencil.kosmo.backend.value.DatabaseUnavailableException
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.wal.LogReader
import jp.skypencil.kosmo.backend.wal.LogReplayer
import jp.skypencil.kosmo.backend.wal.LogWriter
import jp.skypencil.kosmo.backend.wal.TransactionLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.io.IOException

class DurableCommitSpec :
    DescribeSpec({
        it("recovers committed database changes across rotations with values and IDs intact") {
            val directory = tempdir().toPath()
            val rows = (1..1_001).map { Row(RowId.create(), "value_$it") }
            LogWriter(directory).use { log ->
                val db = OnMemoryDatabase(log)
                val manager = TransactionManager(db)
                val tx = manager.create()
                val first = db.createTable(tx, "first")
                val second = db.createTable(tx, "second")
                rows.forEach { first.insert(tx, it) }
                second.insert(tx, rows.first())
                manager.commit(tx) shouldBe CommitResult.Committed
                val change = manager.create()
                first.update(change, rows.first().copy(value = "updated"))
                first.delete(change, rows.last().id) shouldBe true
                manager.commit(change) shouldBe CommitResult.Committed
                val aborted = manager.create()
                first.delete(aborted, rows.first().id)
                db.createTable(aborted, "aborted")
                manager.rollback(aborted)
            }
            val recovered = LogReplayer().replay(LogReader().readDirectory(directory))
            recovered.committedTransactions shouldBe 2
            recovered.discardedTransactions shouldBe emptySet()
            val reader = recovered.transactions.create()
            val expected = listOf(rows.first().copy(value = "updated")) + rows.drop(1).dropLast(1)
            recovered.database
                .findTable(reader, "first")
                .tableScan(reader)
                .toList() shouldBe expected
            recovered.database
                .findTable(reader, "second")
                .tableScan(reader)
                .toList() shouldBe listOf(rows.first())
            shouldThrow<IllegalStateException> { recovered.database.findTable(reader, "aborted") }
        }

        it("logs successful operations in order but emits nothing for rejected or rolled-back work") {
            val batches = mutableListOf<List<LogEntry>>()
            val db = OnMemoryDatabase(TransactionLog { _, operations -> batches.add(operations) })
            val setup = db.beginTransaction()
            val table = db.createTable(setup, "example")
            val row = Row(RowId.create(), "initial")
            table.insert(setup, row)
            db.commit(setup)
            val winner = db.beginTransaction()
            val loser = db.beginTransaction()
            table.update(winner, row.copy(value = "winner"))
            table.delete(loser, row.id)
            db.commit(winner) shouldBe CommitResult.Committed
            (db.commit(loser) is CommitResult.Aborted) shouldBe true
            val aborted = db.beginTransaction()
            table.delete(aborted, row.id)
            db.rollback(aborted)
            val read = db.beginTransaction()
            table.delete(read, RowId.create()) shouldBe false
            shouldThrow<IllegalStateException> { table.insert(read, row) }
            db.commit(read) shouldBe CommitResult.Committed
            batches shouldBe
                listOf(
                    listOf(LogEntry.CreateTable(setup.id, "example"), LogEntry.Insert(setup.id, "example", row)),
                    listOf(LogEntry.Update(winner.id, "example", row.copy(value = "winner"))),
                )
        }

        it("preserves delete and reinsert operation order even when the final row matches") {
            val directory = tempdir().toPath()
            val row = Row(RowId.create(), "same")
            LogWriter(directory).use { log ->
                val db = OnMemoryDatabase(log)
                val tx = db.beginTransaction()
                val table = db.createTable(tx, "example")
                table.insert(tx, row)
                table.delete(tx, row.id)
                table.insert(tx, row)
                db.commit(tx) shouldBe CommitResult.Committed
            }
            val entries = LogReader().readDirectory(directory).toList()
            entries.size shouldBe 5
            (entries[2] is LogEntry.Delete) shouldBe true
            (entries[3] is LogEntry.Insert) shouldBe true
            val result = LogReplayer().replay(LogReader().readDirectory(directory))
            val read = result.transactions.create()
            result.database.findTable(read, "example").find(read, row.id) shouldBe row
        }

        it("waits for durability before publication and completes commit after request cancellation") {
            val entered = CompletableDeferred<Unit>()
            val durable = CompletableDeferred<Unit>()
            val db =
                OnMemoryDatabase(
                    TransactionLog { _, _ ->
                        entered.complete(Unit)
                        durable.await()
                    },
                )
            val tx = db.beginTransaction()
            db.createTable(tx, "example")
            coroutineScope {
                val commit = launch { db.commit(tx) }
                entered.await()
                tx.isCommitted() shouldBe false
                val reader = async(start = CoroutineStart.UNDISPATCHED) { db.beginTransaction() }
                reader.isCompleted shouldBe false
                commit.cancel()
                durable.complete(Unit)
                commit.join()
                tx.isCommitted() shouldBe true
                db.findTable(reader.await(), "example").getName() shouldBe "example"
            }
        }

        it("does not log a request cancelled while waiting for another commit") {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val db =
                OnMemoryDatabase(
                    TransactionLog { _, _ ->
                        calls++
                        entered.complete(Unit)
                        release.await()
                    },
                )
            val first = db.beginTransaction()
            val second = db.beginTransaction()
            db.createTable(first, "first")
            db.createTable(second, "second")
            coroutineScope {
                val committing = launch { db.commit(first) }
                entered.await()
                val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { db.commit(second) }
                cancelled.cancel()
                cancelled.join()
                release.complete(Unit)
                committing.join()
            }
            calls shouldBe 1
            second.isActive() shouldBe true
            db.rollback(second)
            shouldThrow<IllegalStateException> { db.findTable(db.beginTransaction(), "second") }
        }

        listOf(false, true).forEach { commitWasWritten ->
            it("requires recovery after an uncertain log failure (Commit written=$commitWasWritten)") {
                val directory = tempdir().toPath()
                val ioFailure = IOException("simulated storage failure")
                LogWriter(directory).use { writer ->
                    val log =
                        TransactionLog { txId, operations ->
                            if (commitWasWritten) {
                                writer.appendTransaction(txId, operations)
                            } else {
                                operations.forEach { writer.write(it) }
                            }
                            throw ioFailure
                        }
                    val db = OnMemoryDatabase(log)
                    val tx = db.beginTransaction()
                    val other = db.beginTransaction()
                    db.createTable(tx, "example")
                    val failure = shouldThrow<CommitOutcomeUnknownException> { db.commit(tx) }
                    failure.txId shouldBe tx.id
                    failure.cause shouldBe ioFailure
                    tx.isCommitted() shouldBe false
                    tx.isActive() shouldBe false
                    shouldThrow<DatabaseUnavailableException> { db.beginTransaction() }
                    shouldThrow<DatabaseUnavailableException> { db.findTable(other, "example") }
                    shouldThrow<DatabaseUnavailableException> { db.rollback(tx) }
                }
                val recovered = LogReplayer().replay(LogReader().readDirectory(directory))
                recovered.committedTransactions shouldBe if (commitWasWritten) 1 else 0
                val reader = recovered.transactions.create()
                if (commitWasWritten) {
                    recovered.database.findTable(reader, "example").getName() shouldBe "example"
                } else {
                    shouldThrow<IllegalStateException> { recovered.database.findTable(reader, "example") }
                }
            }
        }
    })
