package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.TransactionId
import kotlin.streams.asSequence

// TODO トランザクション
// TODO スレッドセーフティ
class OnMemoryTable(private val name: String, private val transactionManager: TransactionManager) : Table {
    private val map = mutableMapOf<RowId, MutableMap<TransactionId, Row>>()

    override fun getName(): String = name

    /**
     * 指定された[RowId]の歴史を遡り、指定されたトランザクション開始時点の[Row]データ
     * あるいは指定されたトランザクションによって更新された[Row]データを返す。
     */
    private fun snapshotAt(
        tx: TransactionId,
        id: RowId,
    ): Row? =
        checkNotNull(map[id]).entries.findLast {
                entry ->
            entry.key == tx || entry.key < tx && transactionManager.isCommitted(entry.key)
        }?.value

    override suspend fun find(
        tx: TransactionId,
        id: RowId,
    ): Row {
        checkNotNull(map[id]) {
            "指定された $id は $this に存在しません"
        }
        return checkNotNull(snapshotAt(tx, id)) {
            "指定された $id は $this に存在しません"
        }
    }

    override suspend fun tableScan(tx: TransactionId): Sequence<Row> =
        map.values.map {
            it.entries.findLast { entry -> entry.key == tx || entry.key < tx && transactionManager.isCommitted(entry.key) }?.value
        }.filterNotNull().stream().asSequence()

    override suspend fun insert(
        tx: TransactionId,
        row: Row,
    ) {
        check(map[row.id] == null) {
            "指定された $row は $this にすでに存在します"
        }
        map[row.id] = mutableMapOf(Pair(tx, row))
    }

    override suspend fun delete(
        tx: TransactionId,
        id: RowId,
    ): Boolean = map.remove(id) != null

    override suspend fun update(
        tx: TransactionId,
        row: Row,
    ) {
        val history =
            checkNotNull(map[row.id]) {
                "指定された ${row.id} は $this に存在しません"
            }
        history[tx] = row
    }

    override fun toString() = "Table(name=$name)"
}
