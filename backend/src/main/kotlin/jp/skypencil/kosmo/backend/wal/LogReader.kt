package jp.skypencil.kosmo.backend.wal

import jp.skypencil.kosmo.backend.value.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/** Reads finite, immutable log files. A newline completes a record, as in [LogWriter]. */
class LogReader {
    private val logger = LoggerFactory.getLogger(LogReader::class.java)

    /** Files must be supplied in log order. Only the last file may have an incomplete tail. */
    fun read(files: List<Path>): Flow<LogEntry> {
        val orderedFiles = files.toList()
        return flow {
            orderedFiles.forEachIndexed { index, path ->
                Files.newInputStream(path).buffered().use { input ->
                    val line = ByteArrayOutputStream()
                    var lineNumber = 1L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val byte = input.read()
                        if (byte == -1) break
                        if (byte == '\n'.code) {
                            val entry = decode(path, lineNumber, line.toByteArray())
                            emit(entry)
                            line.reset()
                            lineNumber++
                        } else {
                            line.write(byte)
                        }
                    }
                    if (line.size() > 0) {
                        if (index != orderedFiles.lastIndex) {
                            throw LogReadException(path, lineNumber, IOException("Incomplete record before the end of the log"))
                        }
                        logger.warn("Discarding incomplete final log record at {}:{} ({} bytes)", path, lineNumber, line.size())
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Snapshots filenames in the order used by [LogWriter]; callers must ensure writes have stopped. */
    fun readDirectory(directory: Path): Flow<LogEntry> =
        flow {
            val files =
                directory
                    .listDirectoryEntries("wal_*.json")
                    .filter { it.isRegularFile() }
                    .sortedBy { it.fileName.toString() }
            emitAll(read(files))
        }.flowOn(Dispatchers.IO)

    private fun decode(
        path: Path,
        lineNumber: Long,
        bytes: ByteArray,
    ): LogEntry =
        try {
            val json =
                Charsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            LogEntry.fromJson(json)
        } catch (cause: Exception) {
            throw LogReadException(path, lineNumber, cause)
        }
}

class LogReadException(
    val path: Path,
    val lineNumber: Long,
    cause: Exception,
) : IOException("Invalid log record at $path:$lineNumber", cause)
