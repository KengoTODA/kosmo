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

`Row` currently contains only an ID. When values are added to it, inserts and
updates must preserve those values as well. IDs are serialized as UUID strings
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

## Transactional table creation

Both `Database.createTable(tx, name)` and `findTable(tx, name)` require an active
transaction. A table follows the existing transaction visibility rules: its
creator can use it immediately, and readers starting after its commit can see
it. Readers with earlier snapshots cannot. Table handles also check creation
visibility, preventing a retained handle from bypassing the catalog check.

An active or committed creation reserves its table name. A rolled-back creation
is invisible, and a later creation may replace it with an empty table. A finished
transaction cannot be committed again to resurrect a rolled-back table.

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
until the input completes successfully. If a committed batch fails to apply, the
entire private database is abandoned; this is not an implementation of undo for
the storage engine. In particular, the existing destructive delete cannot safely
be rolled back on a live, shared database.

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

## Next integration steps

The code defines records, transactional catalog behavior, and offline recovery. Storage methods
do not yet emit logs automatically. Database implementations should own the commit
and rollback protocols, with `TransactionManager` delegating to them rather than
implementing a storage-specific commit sequence itself. The current in-memory
representation needs review before adding these protocols, especially because
delete destroys row history. Log positions, network delivery, live
replica publication, and broader transaction concurrency semantics remain separate
work. `LogWriter.write()` flushes each complete record, including its newline,
before returning so another reader can observe it without waiting for rotation
or close. This flush does not force data to durable storage. A durable
commit/force operation is still needed; a newline is a framing boundary, not a
guarantee of durability.
