package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId

class OnMemoryTableSpec : DescribeSpec({
    it("can select committed data") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
        val tx1 = txManager.create()
        val row1 = Row(RowId.create())

        table.insert(tx1, row1)
        txManager.commit(tx1)
        table.find(txManager.create(), row1.id) shouldBe row1
    }
    it("can select uncommitted data inserted by the current transaction") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
        val tx1 = txManager.create()
        val row1 = Row(RowId.create())

        table.insert(tx1, row1)
        table.find(tx1, row1.id) shouldBe row1
    }
    it("can ignore uncommitted data") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
        val tx1 = txManager.create()
        val tx2 = txManager.create()
        val row1 = Row(RowId.create())
        table.insert(tx1, row1)

        val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
        exception.message shouldBe "$table does not contain ${row1.id}"
    }
    it("can ignore committed data that was not committed when the current tx started") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
        val tx1 = txManager.create()
        val tx2 = txManager.create()
        val row1 = Row(RowId.create())
        table.insert(tx1, row1)
        txManager.commit(tx1)

        val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
        exception.message shouldBe "$table does not contain ${row1.id}"
    }
    it("can ignore rollbacked data") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
        val tx1 = txManager.create()
        val row1 = Row(RowId.create())
        table.insert(tx1, row1)
        txManager.rollback(tx1)

        val tx2 = txManager.create()
        val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
        exception.message shouldBe "$table does not contain ${row1.id}"
    }
    it("throws exception when committed transaction is used") {
        val txManager = TransactionManager()
        val table = OnMemoryTable("example")
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
