package jp.skypencil.kosmo.backend.wal

import com.github.f4b6a3.uuid.UuidCreator
import jp.skypencil.kosmo.backend.value.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.bufferedWriter

/**
 * Write Ahead Log（WAL）を記録する責務を負う。
 */
@Single
class LogWriter(
    private val logDir: Path,
) : Closeable {
    companion object {
        private const val MAX_LINES = 1_000
    }

    private val mutex = Mutex()
    private var writer = logDir.resolve(nameFile()).bufferedWriter()
    private var isClosed = AtomicBoolean(false)
    private val lines = AtomicInteger(0)

    private fun nameFile(): String =
        with(
            UuidCreator.getTimeOrdered(),
        ) {
            "wal_$this.json"
        }

    suspend fun write(logEntry: LogEntry) {
        check(!isClosed.get())
        withContext(Dispatchers.IO) {
            mutex.withLock {
                writer.write(logEntry.toJson())
                writer.newLine()
                if (lines.incrementAndGet() >= MAX_LINES) {
                    rotate()
                }
            }
        }
    }

    private suspend fun rotate() {
        check(!isClosed.get())
        check(mutex.isLocked)
        withContext(Dispatchers.IO) {
            writer.close()
            writer = logDir.resolve(nameFile()).bufferedWriter()
        }
    }

    override fun close() {
        check(!isClosed.get())
        runBlocking {
            mutex.withLock {
                this@LogWriter.isClosed.set(true)
                writer.close()
            }
        }
    }
}
