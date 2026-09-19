package jp.skypencil.kosmo.backend.wal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import java.nio.file.NoSuchFileException
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class LogReaderSpec :
    DescribeSpec({
        it("reads closed writer files in order across rotations") {
            val directory = tempdir().toPath()
            val txId = TransactionId.create()
            val expected = (1..2_001).map { LogEntry.CreateTable(txId, "table_$it\n日本語") }
            LogWriter(directory).use { writer -> expected.forEach { writer.write(it) } }
            directory.resolve("unrelated.txt").writeText("not a log")

            LogReader().readDirectory(directory).toList() shouldBe expected
        }

        it("preserves explicitly supplied file order and supports CRLF") {
            val directory = tempdir().toPath()
            val first = LogEntry.CreateTable(TransactionId.create(), "example")
            val second = LogEntry.Commit(first.txId)
            val z = directory.resolve("z.json").also { it.writeText(first.toJson() + "\r\n") }
            val a = directory.resolve("a.json").also { it.writeText(second.toJson() + "\n") }
            LogReader().read(listOf(z, a)).toList() shouldBe listOf(first, second)
        }

        it("accepts empty input and empty files") {
            val directory = tempdir().toPath()
            LogReader().readDirectory(directory).toList() shouldBe emptyList()
            val file = directory.resolve("empty.json").also { it.writeText("") }
            LogReader().read(listOf(file)).toList() shouldBe emptyList()
        }

        it("drops an incomplete final record while retaining complete records") {
            val file = tempdir().toPath().resolve("tail.json")
            val entry = LogEntry.CreateTable(TransactionId.create(), "example")
            file.writeText(entry.toJson() + "\n" + "{\"version\":1,\"entry\":")
            LogReader().read(listOf(file)).toList() shouldBe listOf(entry)
        }

        it("requires a newline even for a syntactically complete final Commit") {
            val file = tempdir().toPath().resolve("tail.json")
            val entry = LogEntry.CreateTable(TransactionId.create(), "example")
            file.writeText(entry.toJson() + "\n" + LogEntry.Commit(entry.txId).toJson())
            LogReader().read(listOf(file)).toList() shouldBe listOf(entry)
        }

        it("drops incomplete UTF-8 bytes only at the unterminated final tail") {
            val file = tempdir().toPath().resolve("tail.json")
            file.writeBytes(byteArrayOf(0xe3.toByte(), 0x81.toByte()))
            LogReader().read(listOf(file)).toList() shouldBe emptyList()

            file.writeBytes(byteArrayOf(0xe3.toByte(), 0x81.toByte(), '\n'.code.toByte()))
            shouldThrow<LogReadException> { LogReader().read(listOf(file)).toList() }
        }

        it("rejects an incomplete record before a later file even if that file is empty") {
            val directory = tempdir().toPath()
            val first = directory.resolve("first.json").also { it.writeText("{") }
            val second = directory.resolve("second.json").also { it.writeText("") }
            val exception = shouldThrow<LogReadException> { LogReader().read(listOf(first, second)).toList() }
            exception.path shouldBe first
            exception.lineNumber shouldBe 1L
        }

        it("rejects newline-terminated corruption and reports its file and line") {
            val file = tempdir().toPath().resolve("broken.json")
            val entry = LogEntry.CreateTable(TransactionId.create(), "example")
            file.writeText(entry.toJson() + "\n{\n" + LogEntry.Commit(entry.txId).toJson() + "\n")
            val exception = shouldThrow<LogReadException> { LogReader().read(listOf(file)).toList() }
            exception.path shouldBe file
            exception.lineNumber shouldBe 2L
        }

        it("rejects unknown record versions even on the final terminated line") {
            val file = tempdir().toPath().resolve("version.json")
            val json = LogEntry.Commit(TransactionId.create()).toJson().replace("\"version\":1", "\"version\":2")
            file.writeText(json + "\n")
            shouldThrow<LogReadException> { LogReader().read(listOf(file)).toList() }
        }

        it("rejects blank lines rather than treating them as end of input") {
            val file = tempdir().toPath().resolve("blank.json").also { it.writeText("\n") }
            shouldThrow<LogReadException> { LogReader().read(listOf(file)).toList() }
        }

        it("does not swallow file access failures") {
            val file = tempdir().toPath().resolve("missing.json")
            shouldThrow<NoSuchFileException> { LogReader().read(listOf(file)).toList() }
        }

        it("supports early collection termination and subsequent reads") {
            val file = tempdir().toPath().resolve("entries.json")
            val entries = (1..3).map { LogEntry.Commit(TransactionId.create()) }
            file.writeText(entries.joinToString(separator = "\n", postfix = "\n") { it.toJson() })
            val reader = LogReader()
            reader.read(listOf(file)).take(1).toList() shouldBe entries.take(1)
            reader.read(listOf(file)).toList() shouldBe entries
        }
    })
