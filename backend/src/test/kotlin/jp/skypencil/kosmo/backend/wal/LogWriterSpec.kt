package jp.skypencil.kosmo.backend.wal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.runBlocking

class LogWriterSpec :
    DescribeSpec({
        describe("LogWriter") {
            it("persists all operation types in transaction order") {
                val logDir = tempdir()
                val txId = TransactionId.create()
                val row = Row(RowId.create())
                val tableName = "example\n日本語"
                val expected =
                    listOf(
                        LogEntry.CreateTable(txId, tableName),
                        LogEntry.Insert(txId, tableName, row),
                        LogEntry.Update(txId, tableName, row),
                        LogEntry.Delete(txId, tableName, row.id),
                        LogEntry.Commit(txId),
                    )
                LogWriter(logDir.toPath()).use { writer ->
                    expected.forEach { writer.write(it) }
                }
                val lines = logDir.listFiles()!!.single().readLines()
                lines.size shouldBe expected.size
                lines.map { LogEntry.fromJson(it) } shouldBe expected
            }
            it("creates a log file under the given dir") {
                val logDir = tempdir()
                LogWriter(logDir.toPath()).use {
                    // nothing to do
                }
                logDir.listFiles { file -> file.isFile }!!.size shouldBe 1
            }
            it("rotates log file when many lines had been written") {
                val logDir = tempdir()
                val txId = TransactionId.create()
                LogWriter(logDir.toPath()).use { logWriter ->
                    runBlocking {
                        (1..1_000).forEach {
                            logWriter.write(LogEntry.CreateTable(txId, "table_$it"))
                        }
                    }
                }
                logDir.listFiles { file -> file.isFile }!!.size shouldBe 2
            }
            it("preserves entries across multiple log rotations") {
                val logDir = tempdir()
                val txId = TransactionId.create()
                val expected = (1..2_001).map { LogEntry.CreateTable(txId, "table_$it") }
                LogWriter(logDir.toPath()).use { logWriter ->
                    runBlocking {
                        expected.forEach {
                            logWriter.write(it)
                        }
                    }
                }

                val files = logDir.listFiles { file -> file.isFile }!!.sortedBy { it.name }
                files.size shouldBe 3
                val entries = files.map { it.readLines() }
                entries.map { it.size } shouldBe listOf(1_000, 1_000, 1)
                entries.flatten().map { LogEntry.fromJson(it) } shouldBe expected
            }
        }
    })
