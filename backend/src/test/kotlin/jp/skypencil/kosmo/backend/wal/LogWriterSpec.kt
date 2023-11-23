package jp.skypencil.kosmo.backend.wal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import jp.skypencil.kosmo.backend.value.LogEntry
import kotlinx.coroutines.runBlocking

class DummyLogEntry(private val int: Int) : LogEntry {
    override fun toJson(): String = "{\"int\": $int}"
}

class LogWriterSpec : DescribeSpec({
    describe("LogWriter") {
        it("creates a log file under the given dir") {
            val logDir = tempdir()
            LogWriter(logDir.toPath()).use {
                // nothing to do
            }
            logDir.listFiles { file -> file.isFile }!!.size shouldBe 1
        }
        it("rotates log file when many lines had been written") {
            val logDir = tempdir()
            LogWriter(logDir.toPath()).use { logWriter ->
                runBlocking {
                    (1..1_000).forEach {
                        logWriter.write(DummyLogEntry(it))
                    }
                }
            }
            logDir.listFiles { file -> file.isFile }!!.size shouldBe 2
        }
    }
})
