# Change log records

`LogEntry` is a sealed class whose operations are immutable data classes. This
keeps the operation set explicit and permits exhaustive `when` expressions in a
future replayer. A sealed interface could also provide exhaustiveness; the class
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

Operations from different transactions may be interleaved. The future replayer
must group them by transaction ID and publish a transaction only after its
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

## Next integration steps

This change defines records and transactional catalog behavior. Storage methods
do not yet emit logs automatically. Coordinator integration must order operations
and durable commit records consistently; a reader/applier must implement replay
and atomic publication. Log positions, delivery, recovery, and broader transaction
concurrency semantics remain separate work.
