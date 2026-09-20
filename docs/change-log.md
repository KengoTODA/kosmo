# Change log records

`LogEntry` is a sealed class whose operations are immutable data classes. This
keeps the operation set explicit and permits exhaustive `when` expressions in the
replayer. A sealed interface could also provide exhaustiveness; the class
is useful here because it supplies the final, shared `toJson()` implementation.
Adding an operation requires updating the replayer, rather than allowing an
arbitrary implementation to emit an unknown JSON shape.

Each record describes one operation and carries the originating `TransactionId`.
The transaction manager itself is not serialized.

| Kotlin type | JSON type | Additional fields |
| --- | --- | --- |
| `CreateTable` | `create_table` | `tableName` |
| `Insert` | `insert` | `tableName`, `row` |
| `Update` | `update` | `tableName`, `row` (complete resulting row) |
| `Delete` | `delete` | `tableName`, `rowId` |
| `Commit` | `commit` | None |

`Row` contains an ID and an optional string value. Inserts and updates preserve
both. IDs are serialized as UUID strings
inside their value objects, preserving the existing UUID version validation on
decode. Replay must use the recorded IDs, not generate new ones.

`toJson()` and `LogEntry.fromJson()` use kotlinx.serialization. Every line has a
versioned envelope, for example:

```json
{"version":1,"entry":{"type":"create_table","txId":{"uuid":"1ef00000-0000-6000-8000-000000000001"},"tableName":"example"}}
```

Operation names are explicit serial names, independent of Kotlin class/package
names. JSON escaping keeps names containing newlines in a single log line.
Unsupported versions, unknown operations, missing required fields, and malformed
records are rejected. The old test-only `DummyLogEntry` format is not supported.

Operations from different transactions may be interleaved. The replayer
groups them by transaction ID and applies a transaction only after its
`Commit` record. A transaction without that record must remain invisible. A live
reader cannot infer rollback merely from the current end of the log; it must
wait for more records. An explicit abort operation can be added later.

## In-memory transactions

`TransactionManager(database)` delegates begin, commit, and rollback to `Database`.
The database owns snapshots and workspaces; `Transaction` is an identity handle.
`OnMemoryTable` delegates access to its owning database so all tables share the
same transaction boundary. Standalone tables and foreign transactions are rejected.

At begin, the transaction retains the immutable committed snapshot. Reads overlay
its own pending row writes and deletions on that snapshot. Table creation is also
private until commit. Commit merges only the transaction's changes into the latest
committed state and publishes a new snapshot under the database lock. Rollback
simply discards its workspace. Old snapshots remain valid while readers hold them.
`Row.value` is an optional string, allowing updates and recovery to be verified
with actual values as well as IDs.

Commit validates each written row against the latest commit revision. Deletion
revisions are retained to detect insert/delete/reinsert ABA changes. Different
rows can commit independently; a concurrent same-name table creation conflicts
at commit. A transaction may create a name absent from its own snapshot even if
another active transaction has also created it.

`CommitResult.Committed` reports success. `CommitResult.Aborted` contains a
`WriteConflict` or `TableNameConflict` and guarantees that the whole workspace
was discarded. Retrying requires a new transaction and repeating the reads and
business decisions, not just another commit call. Foreign/finished handles are
API misuse and throw exceptions. This is snapshot isolation with write conflict
checks, not serializable isolation: read/write dependencies and write skew are
not detected. Row deletion revision metadata is not yet garbage collected.

## Offline reading and recovery

`LogReader.read(files)` consumes a complete, ordered list of files that will no
longer be appended to. `readDirectory(directory)` selects `wal_*.json` files and
sorts them by filename, matching the current writer's time-ordered names. Neither
API watches for new files or waits for more bytes. The caller must supply the
complete log: filenames alone cannot detect a missing segment.

Each record must end with LF (CRLF is also accepted). This matches `LogWriter`'s
write-JSON-then-newline protocol. A syntactically complete JSON object without
the terminating newline is still an incomplete record, including a Commit.

| Condition | Recovery behavior |
| --- | --- |
| Unterminated bytes at the end of the last file | Discard the fragment and log WARN |
| Unterminated bytes in an earlier file | Fail with file and line information |
| Invalid newline-terminated JSON, UTF-8, schema, or version | Fail with file and line information |
| Complete operations without Commit at the end of the stream | Discard the transaction and log WARN |
| File access or upstream stream error | Propagate the error; do not return a database |

The reader does not skip corrupted records based on guesses about whether they
belong to an uncommitted transaction. A later Commit might otherwise publish a
transaction with missing operations. Warnings report location, transaction ID,
or operation count, not row contents. A future explicit Rollback record can
discard its pending transaction without warning.

`LogReplayer.replay(Flow<LogEntry>)` is independent of file transport. It buffers
operations by source transaction ID and applies each batch in Commit order to a
fresh, private `OnMemoryDatabase`, using a fresh local transaction for each batch.
Source transaction IDs group records; they are not reused as local MVCC IDs.
Row IDs are retained. Duplicate commits and records after a Commit are errors;
deduplication for network retries requires a future log-position protocol.

The result contains the database, its transaction manager, the committed
transaction count, and the IDs of discarded transactions. No database is returned
until the input completes successfully. If a committed batch fails to apply or its database rejects commit, the
entire private database is abandoned; this is not an implementation of undo for
the storage engine. Normal transaction rollback discards the private workspace. A typed commit rejection
is reported as `LogReplayException` with the original source transaction ID and
`commitFailure`; recovery stops immediately.

For example, from a coroutine after the writer has closed:

```kotlin
val recovered = LogReplayer().replay(LogReader().readDirectory(logDir))
val reader = recovered.transactions.create()
val table = recovered.database.findTable(reader, "example")
val rows = table.tableScan(reader).toList()
recovered.transactions.commit(reader)
```

An adapter for a pipe or WebSocket can eventually produce the same flow, but a
temporary disconnection must not complete it normally: completion means final
end of recovery and discards pending transactions. Recovery buffers in-flight
transactions and tracks committed IDs in memory. Memory bounds and incremental
publication to live replica readers require further work.

## Durable commits

For a fresh database and log directory, pass a writer to the database:

```kotlin
LogWriter(logDir).use { writer ->
    val database = OnMemoryDatabase(writer)
    val transactions = TransactionManager(database)
    val tx = transactions.create()
    val table = database.createTable(tx, "example")
    table.insert(tx, Row(RowId.create(), "value"))
    when (val result = transactions.commit(tx)) {
        CommitResult.Committed -> Unit
        is CommitResult.Aborted -> println(result.reason)
    }
}
```

`TransactionManager` delegates the protocol to `Database`. `OnMemoryDatabase`
records successful operations in its workspace, retaining their original order
separately from the net row changes. This matters for insert/delete/reinsert
sequences. Rollback, rejected commits, and read-only commits emit no WAL records.
All log records still describe individual operations, not a transaction blob.

Under the database lock, commit validates conflicts and prepares the next
snapshot before starting I/O. It then calls `TransactionLog.appendTransaction`
to append the operation records followed by Commit. `LogWriter` flushes each
record, forces rotated segments before closing them, forces the final segment,
and syncs directory entries when creating new segments. The filesystem must
support opening and forcing directories; unsupported environments fail rather
than silently weaken the contract. Guarantees depend on the underlying storage
honoring these force requests. Ordinary `write()` only promises flush (although
rotation also forces its completed segment).

Only after the log append/force succeeds does the database publish its prepared
snapshot and mark the transaction committed. This initial implementation holds
the database lock through I/O, so readers and other transactions wait. There is
no group commit optimization yet.

A log exception does not mean a known rollback: Commit may already be stored.
The database throws `CommitOutcomeUnknownException`, marks the transaction
in-doubt, discards other pending work, and rejects further access with
`DatabaseUnavailableException`. The caller must close the writer and recover
from the log; it must not blindly retry the transaction. A writer that suffered
an I/O failure also rejects further writes. Recovery without a log sink avoids
logging the recovered operations again.

Cancellation while waiting for the database lock leaves the transaction active;
the caller can explicitly roll it back. Once WAL I/O begins, the save-and-publish
section is non-cancellable so request cancellation cannot leave a durable Commit
unpublished in the live database. A cancelled client may still have committed;
it must not interpret cancellation as proof of rollback.

## Remaining integration work

Log positions, network delivery, live replica publication, and startup/resume
wiring remain separate work. The current `Coordinator` is still an unused
bootstrap skeleton. Do not attach a new empty database to an existing history:
startup must recover the history before enabling new writes. `LogReplayer`
currently returns an in-memory recovered database without a log sink. Snapshot
copy costs, unbounded deletion revision metadata, and long-lived transactions
retaining snapshots are accepted limitations of this learning implementation.
