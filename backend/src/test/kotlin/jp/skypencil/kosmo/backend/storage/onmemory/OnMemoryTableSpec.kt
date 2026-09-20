package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId

class OnMemoryTableSpec :
    DescribeSpec({
        it("rolls back insert update and delete without changing the committed snapshot") {
            val database = OnMemoryDatabase()
            val manager = TransactionManager(database)
            val setup = manager.create()
            val table = database.createTable(setup, "example")
            val original = Row(RowId.create(), "original")
            table.insert(setup, original)
            manager.commit(setup)

            val reader = manager.create()
            val writer = manager.create()
            table.update(writer, original.copy(value = "changed"))
            table.find(writer, original.id).value shouldBe "changed"
            table.find(reader, original.id) shouldBe original
            table.delete(writer, original.id) shouldBe true
            table.tableScan(writer).toList() shouldBe emptyList()
            table.find(reader, original.id) shouldBe original
            val inserted = Row(RowId.create(), "new")
            table.insert(writer, inserted)
            manager.rollback(writer)

            val next = manager.create()
            table.tableScan(next).toList() shouldBe listOf(original)
            table.insert(next, inserted)
            manager.commit(next)
            table.tableScan(reader).toList() shouldBe listOf(original)
            table.tableScan(manager.create()).toList() shouldBe listOf(original, inserted)
        }

        it("preserves old snapshots across committed updates and deletions") {
            val database = OnMemoryDatabase()
            val manager = TransactionManager(database)
            val setup = manager.create()
            val table = database.createTable(setup, "example")
            val row = Row(RowId.create(), "before")
            table.insert(setup, row)
            manager.commit(setup)
            val old = manager.create()
            val update = manager.create()
            table.update(update, row.copy(value = "after"))
            manager.commit(update)
            val middle = manager.create()
            val delete = manager.create()
            table.delete(delete, row.id) shouldBe true
            manager.commit(delete)
            table.find(old, row.id).value shouldBe "before"
            table.find(middle, row.id).value shouldBe "after"
            table.tableScan(manager.create()).toList() shouldBe emptyList()
        }

        it("rejects transactions owned by another database") {
            val first = OnMemoryDatabase()
            val second = OnMemoryDatabase()
            val foreign = first.beginTransaction()
            shouldThrow<IllegalArgumentException> { second.createTable(foreign, "example") }
            shouldThrow<IllegalArgumentException> { second.commit(foreign) }
            shouldThrow<IllegalArgumentException> { second.rollback(foreign) }
        }

        it("can select committed data") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val row1 = Row(RowId.create())

            table.insert(tx1, row1)
            txManager.commit(tx1)
            table.find(txManager.create(), row1.id) shouldBe row1
        }
        it("can select uncommitted data inserted by the current transaction") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val row1 = Row(RowId.create())

            table.insert(tx1, row1)
            table.find(tx1, row1.id) shouldBe row1
        }
        it("can ignore uncommitted data") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val tx2 = txManager.create()
            val row1 = Row(RowId.create())
            table.insert(tx1, row1)

            val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
            exception.message shouldBe "$table does not contain ${row1.id}"
        }
        it("can ignore committed data that was not committed when the current tx started") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val tx2 = txManager.create()
            val row1 = Row(RowId.create())
            table.insert(tx1, row1)
            txManager.commit(tx1)

            val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
            exception.message shouldBe "$table does not contain ${row1.id}"
        }
        it("can ignore rollbacked data") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val row1 = Row(RowId.create())
            table.insert(tx1, row1)
            txManager.rollback(tx1)

            val tx2 = txManager.create()
            val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
            exception.message shouldBe "$table does not contain ${row1.id}"
        }
        it("throws exception when committed transaction is used") {
            val database = OnMemoryDatabase()
            val txManager = TransactionManager(database)
            val schema = txManager.create()
            val table = database.createTable(schema, "example")
            txManager.commit(schema)
            val tx1 = txManager.create()
            val row1 = Row(RowId.create())
            table.insert(tx1, row1)
            txManager.commit(tx1)

            val exception =
                shouldThrow<IllegalArgumentException> {
                    table.find(tx1, row1.id)
                }
            exception.message shouldBe "Given $tx1 is not active"
        }
    })
