package jp.skypencil.kosmo.backend

import jp.skypencil.kosmo.backend.storage.onmemory.OnMemoryDatabase
import jp.skypencil.kosmo.backend.wal.LogWriter
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * フロントエンドからの要求を受け取り、それぞれのクラスを組み合わせて実現する。
 */
class Coordinator {
    private val baseDir = Path.of(".")
    private val logDir =
        baseDir.resolve("logs").also {
            if (!it.isDirectory()) {
                it.toFile().mkdirs()
            }
        }
    private val database = OnMemoryDatabase()
    private val logWriter = LogWriter(logDir)
}
