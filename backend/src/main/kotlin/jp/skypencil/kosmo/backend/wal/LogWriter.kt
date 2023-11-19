package jp.skypencil.kosmo.backend.wal

import jp.skypencil.kosmo.backend.value.LogEntry

/**
 * Write Ahead Log（WAL）を記録する責務を負う。
 */
class LogWriter {
    suspend fun write(logEntry: LogEntry) {

    }
}