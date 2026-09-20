package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.CommitFailure
import jp.skypencil.kosmo.backend.value.CommitResult
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

class CommitSpec :
    DescribeSpec({
        it("allows only one concurrent writer to commit the same row") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val table = db.createTable(setup, "example")
            val row = Row(RowId.create(), "original")
            table.insert(setup, row)
            db.commit(setup) shouldBe CommitResult.Committed
            val writers = (1..2).map { db.beginTransaction() }
            writers.forEachIndexed { i, tx -> table.update(tx, row.copy(value = "$i")) }
            val results =
                coroutineScope {
                    writers.map { tx -> async(Dispatchers.Default) { db.commit(tx) } }.awaitAll()
                }
            results.count { it == CommitResult.Committed } shouldBe 1
            results.count { it == CommitResult.Aborted(CommitFailure.WriteConflict("example", row.id)) } shouldBe 1
            val winner = results.indexOf(CommitResult.Committed)
            table.find(db.beginTransaction(), row.id).value shouldBe "$winner"
            writers.forEach { it.isActive() shouldBe false }
            shouldThrow<IllegalArgumentException> { db.commit(writers[1 - winner]) }
        }

        it("merges non-overlapping concurrent changes into the latest state") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val table = db.createTable(setup, "example")
            val rows = (1..2).map { Row(RowId.create(), "old") }
            rows.forEach { table.insert(setup, it) }
            db.commit(setup)
            val first = db.beginTransaction()
            val second = db.beginTransaction()
            table.update(first, rows[0].copy(value = "first"))
            table.update(second, rows[1].copy(value = "second"))
            db.commit(first) shouldBe CommitResult.Committed
            db.commit(second) shouldBe CommitResult.Committed
            table.tableScan(db.beginTransaction()).map { it.value }.toList() shouldBe listOf("first", "second")
        }

        it("aborts all tables and schema changes when one row conflicts") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val one = db.createTable(setup, "one")
            val two = db.createTable(setup, "two")
            val row = Row(RowId.create(), "old")
            one.insert(setup, row)
            two.insert(setup, row)
            db.commit(setup)
            val winner = db.beginTransaction()
            val loser = db.beginTransaction()
            one.update(winner, row.copy(value = "winner"))
            two.update(loser, row.copy(value = "must not publish"))
            one.delete(loser, row.id)
            db.createTable(loser, "must_not_exist")
            db.commit(winner) shouldBe CommitResult.Committed
            db.commit(loser) shouldBe CommitResult.Aborted(CommitFailure.WriteConflict("one", row.id))
            val reader = db.beginTransaction()
            two.find(reader, row.id) shouldBe row
            one.find(reader, row.id).value shouldBe "winner"
            shouldThrow<IllegalStateException> { db.findTable(reader, "must_not_exist") }
        }

        it("detects duplicate inserts and update versus delete conflicts") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val table = db.createTable(setup, "example")
            db.commit(setup)
            val row = Row(RowId.create(), "same")
            val insert1 = db.beginTransaction()
            val insert2 = db.beginTransaction()
            table.insert(insert1, row)
            table.insert(insert2, row)
            db.commit(insert1) shouldBe CommitResult.Committed
            db.commit(insert2) shouldBe CommitResult.Aborted(CommitFailure.WriteConflict("example", row.id))
            val update = db.beginTransaction()
            val delete = db.beginTransaction()
            table.update(update, row.copy(value = "update"))
            table.delete(delete, row.id)
            db.commit(delete) shouldBe CommitResult.Committed
            db.commit(update) shouldBe CommitResult.Aborted(CommitFailure.WriteConflict("example", row.id))
        }

        it("detects insert-delete and delete-reinsert ABA even when the final value matches") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val table = db.createTable(setup, "example")
            db.commit(setup)
            val row = Row(RowId.create(), "same")
            val staleInsert = db.beginTransaction()
            table.insert(staleInsert, row)
            val insert = db.beginTransaction()
            table.insert(insert, row)
            db.commit(insert)
            val delete = db.beginTransaction()
            table.delete(delete, row.id)
            db.commit(delete)
            db.commit(staleInsert) shouldBe CommitResult.Aborted(CommitFailure.WriteConflict("example", row.id))

            val restore = db.beginTransaction()
            table.insert(restore, row)
            db.commit(restore)
            val staleUpdate = db.beginTransaction()
            table.update(staleUpdate, row.copy(value = "stale"))
            val replace = db.beginTransaction()
            table.delete(replace, row.id)
            table.insert(replace, row)
            db.commit(replace)
            db.commit(staleUpdate) shouldBe CommitResult.Aborted(CommitFailure.WriteConflict("example", row.id))
        }

        it("never exposes a mixed multi-table commit to concurrent readers") {
            val db = OnMemoryDatabase()
            val setup = db.beginTransaction()
            val left = db.createTable(setup, "left")
            val right = db.createTable(setup, "right")
            val row = Row(RowId.create(), "0")
            left.insert(setup, row)
            right.insert(setup, row)
            db.commit(setup)
            coroutineScope {
                val writer =
                    async(Dispatchers.Default) {
                        repeat(50) { i ->
                            val tx = db.beginTransaction()
                            left.update(tx, row.copy(value = "$i"))
                            yield()
                            right.update(tx, row.copy(value = "$i"))
                            db.commit(tx) shouldBe CommitResult.Committed
                        }
                    }
                val reader =
                    async(Dispatchers.Default) {
                        repeat(100) {
                            val tx = db.beginTransaction()
                            val a = left.find(tx, row.id)
                            yield()
                            right.find(tx, row.id) shouldBe a
                            db.commit(tx) shouldBe CommitResult.Committed
                        }
                    }
                awaitAll(writer, reader)
            }
        }
    })
