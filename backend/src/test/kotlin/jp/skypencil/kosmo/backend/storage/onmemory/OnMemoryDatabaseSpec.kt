package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId

class OnMemoryDatabaseSpec :
    DescribeSpec({
        it("exposes a new table and its rows to its creator and later committed snapshots") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val creator = manager.create()
            val table = database.createTable(creator, "example")
            val row = Row(RowId.create())
            table.insert(creator, row)
            database.findTable(creator, "example") shouldBe table
            table.find(creator, row.id) shouldBe row

            val earlierReader = manager.create()
            shouldThrow<IllegalStateException> { database.findTable(earlierReader, "example") }
            manager.commit(creator)
            shouldThrow<IllegalStateException> { database.findTable(earlierReader, "example") }

            val reader = manager.create()
            database.findTable(reader, "example").find(reader, row.id) shouldBe row
        }

        it("hides rolled back tables and allows their names to be reused without retaining rows") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val creator = manager.create()
            val oldTable = database.createTable(creator, "example")
            oldTable.insert(creator, Row(RowId.create()))
            manager.rollback(creator)

            val next = manager.create()
            shouldThrow<IllegalStateException> { database.findTable(next, "example") }
            val newTable = database.createTable(next, "example")
            newTable.tableScan(next).toList() shouldBe emptyList()
            shouldThrow<IllegalStateException> { oldTable.tableScan(next) }
            manager.commit(next)
            database.findTable(manager.create(), "example") shouldBe newTable
        }

        it("reserves table names for active and committed creators") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val creator = manager.create()
            database.createTable(creator, "example")
            shouldThrow<IllegalArgumentException> { database.createTable(creator, "example") }

            val other = manager.create()
            shouldThrow<IllegalArgumentException> { database.createTable(other, "example") }
            manager.commit(creator)
            shouldThrow<IllegalArgumentException> { database.createTable(other, "example") }
            shouldThrow<IllegalArgumentException> { database.createTable(manager.create(), "example") }
        }

        it("checks visibility even when the caller retains the table object") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val creator = manager.create()
            val table = database.createTable(creator, "example")
            val other = manager.create()
            val row = Row(RowId.create())
            shouldThrow<IllegalStateException> { table.insert(other, row) }
            shouldThrow<IllegalStateException> { table.tableScan(other) }
            manager.commit(creator)
            shouldThrow<IllegalStateException> { table.insert(other, row) }
        }

        it("rejects database access through committed transactions") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val tx = manager.create()
            database.createTable(tx, "example")
            manager.commit(tx)
            shouldThrow<IllegalArgumentException> { database.createTable(tx, "another") }
            shouldThrow<IllegalArgumentException> { database.findTable(tx, "example") }
            shouldThrow<IllegalArgumentException> { manager.commit(tx) }
            shouldThrow<IllegalArgumentException> { manager.rollback(tx) }
        }

        it("cannot publish a rolled back table by committing its old transaction") {
            val manager = TransactionManager()
            val database = OnMemoryDatabase()
            val tx = manager.create()
            database.createTable(tx, "example")
            manager.rollback(tx)
            shouldThrow<IllegalArgumentException> { database.createTable(tx, "another") }
            shouldThrow<IllegalArgumentException> { database.findTable(tx, "example") }
            shouldThrow<IllegalArgumentException> { manager.commit(tx) }
            shouldThrow<IllegalStateException> { database.findTable(manager.create(), "example") }
        }
    })
