package jp.skypencil.kosmo.backend.storage.onmemory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId

class OnMemoryTableSpec : DescribeSpec({
    it("can select committed data") {
        val table = OnMemoryTable("example", TransactionManager())
        val tx1 = TransactionId.create()
        val row1 = Row(RowId.create())

        table.insert(tx1, row1)
        table.find(tx1, row1.id) shouldBe row1
    }
    it("can ignore uncommitted data") {
        val table = OnMemoryTable("example", TransactionManager())
        val tx1 = TransactionId.create()
        val tx2 = TransactionId.create()
        val row1 = Row(RowId.create())

        println(tx1)
        println(tx2)
        println(tx1 <= tx2)
        table.insert(tx1, row1)

        val exception = shouldThrow<IllegalStateException> { table.find(tx2, row1.id) }
        exception.message shouldBe "指定された ${row1.id} は $table に存在しません"
    }
})
