package jp.skypencil.kosmo.backend.storage.onmemory

import jp.skypencil.kosmo.backend.storage.shared.Table
import jp.skypencil.kosmo.backend.value.Row
import jp.skypencil.kosmo.backend.value.RowId
import jp.skypencil.kosmo.backend.value.Transaction

/** Stable table identity. The owning database controls snapshots and atomic publication. */
class OnMemoryTable internal constructor(
    private val name: String,
    private val database: OnMemoryDatabase,
) : Table {
    override fun getName(): String = name

    override suspend fun find(
        tx: Transaction,
        id: RowId,
    ): Row = database.find(tx, this, id)

    override suspend fun tableScan(tx: Transaction): Sequence<Row> = database.scan(tx, this)

    override suspend fun insert(
        tx: Transaction,
        row: Row,
    ) = database.insert(tx, this, row)

    override suspend fun update(
        tx: Transaction,
        row: Row,
    ) = database.update(tx, this, row)

    override suspend fun delete(
        tx: Transaction,
        id: RowId,
    ): Boolean = database.delete(tx, this, id)

    override fun toString(): String = "Table(name=$name)"
}
