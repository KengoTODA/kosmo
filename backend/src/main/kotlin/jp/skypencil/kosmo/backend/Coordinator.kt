package jp.skypencil.kosmo.backend

import jp.skypencil.kosmo.backend.storage.onmemory.OnMemoryDatabase
import jp.skypencil.kosmo.backend.wal.LogWriter

/**
 * フロントエンドからの要求を受け取り、それぞれのクラスを組み合わせて実現する。
 */
class Coordinator {
    private val database = OnMemoryDatabase()
    private val logWriter = LogWriter()
}
