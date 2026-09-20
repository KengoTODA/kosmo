package jp.skypencil.kosmo.backend.wal

import com.github.f4b6a3.uuid.UuidCreator
import jp.skypencil.kosmo.backend.value.LogEntry
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Serializes log appends and owns file rotation and durability. */
@Single
class LogWriter(
    private val logDir: Path,
) : Closeable,
    TransactionLog {
    companion object {
        private const val MAX_LINES = 1_000
    }

    private class Segment(
        val channel: FileChannel,
        val writer: BufferedWriter,
    )

    private val mutex = Mutex()
    private var segment = openSegment()
    private var closed = false
    private var failure: IOException? = null
    private var lines = 0

    private fun openSegment(): Segment {
        val path = logDir.resolve("wal_${UuidCreator.getTimeOrdered()}.json")
        val channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        try {
            // Persist the new directory entry as well as file contents before acknowledging a commit.
            FileChannel.open(logDir, StandardOpenOption.READ).use { it.force(true) }
            return Segment(channel, Channels.newWriter(channel, Charsets.UTF_8).buffered())
        } catch (cause: Exception) {
            channel.close()
            throw cause
        }
    }

    /** Flushes a complete record for readers; use appendTransaction for commit durability. */
    suspend fun write(logEntry: LogEntry) {
        val json = logEntry.toJson()
        withContext(Dispatchers.IO) {
            mutex.withLock { withWritableSegment { writeLine(json) } }
        }
    }

    override suspend fun appendTransaction(
        txId: TransactionId,
        operations: List<LogEntry>,
    ) {
        require(operations.all { it.txId == txId && it !is LogEntry.Commit }) { "Invalid transaction operations" }
        val records = operations.map { it.toJson() } + LogEntry.Commit(txId).toJson()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withWritableSegment {
                    records.forEach { writeLine(it) }
                    segment.channel.force(true)
                }
            }
        }
    }

    private fun writeLine(json: String) {
        segment.writer.write(json)
        segment.writer.newLine()
        segment.writer.flush()
        lines++
        if (lines >= MAX_LINES) {
            // Older segments must be durable too when a transaction spans files.
            segment.channel.force(true)
            segment.writer.close()
            segment = openSegment()
            lines = 0
        }
    }

    private inline fun withWritableSegment(block: () -> Unit) {
        check(!closed) { "LogWriter is closed" }
        failure?.let { throw IOException("LogWriter requires recovery after an I/O failure", it) }
        try {
            block()
        } catch (cause: IOException) {
            failure = cause
            throw cause
        }
    }

    override fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    segment.writer.close()
                }
            }
        }
    }
}
