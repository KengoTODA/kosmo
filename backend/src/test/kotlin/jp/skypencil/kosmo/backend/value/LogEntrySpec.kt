package jp.skypencil.kosmo.backend.value

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class LogEntrySpec :
    DescribeSpec({
        val txUuid = "1ef00000-0000-6000-8000-000000000001"
        val rowUuid = "1ef00000-0000-6000-8000-000000000002"
        val txId = TransactionId(UUID.fromString(txUuid))
        val row = Row(RowId(UUID.fromString(rowUuid)))
        val tableName = "table\"\\\n\t日本語"
        val entries =
            mapOf(
                "create_table" to LogEntry.CreateTable(txId, tableName),
                "insert" to LogEntry.Insert(txId, tableName, row),
                "update" to LogEntry.Update(txId, tableName, row),
                "delete" to LogEntry.Delete(txId, tableName, row.id),
                "commit" to LogEntry.Commit(txId),
            )

        entries.forEach { (type, entry) ->
            it("round trips $type in a single line with stable operation and transaction IDs") {
                val json = entry.toJson()
                json.contains('\n') shouldBe false
                json.contains('\r') shouldBe false
                val record = Json.parseToJsonElement(json).jsonObject
                record.getValue("version").jsonPrimitive.content shouldBe "1"
                val payload = record.getValue("entry").jsonObject
                payload.getValue("type").jsonPrimitive.content shouldBe type
                payload
                    .getValue("txId")
                    .jsonObject
                    .getValue("uuid")
                    .jsonPrimitive.content shouldBe txUuid
                LogEntry.fromJson(json) shouldBe entry
            }
        }

        it("reads a fixed insert record without generating new IDs") {
            val json =
                """{"version":1,"entry":{"type":"insert","txId":{"uuid":"$txUuid"},"tableName":"example","row":{"id":{"uuid":"$rowUuid"}}}}"""
            LogEntry.fromJson(json) shouldBe LogEntry.Insert(txId, "example", row)
        }

        it("rejects unsupported record versions") {
            val json = """{"version":2,"entry":{"type":"commit","txId":{"uuid":"$txUuid"}}}"""
            shouldThrow<IllegalArgumentException> { LogEntry.fromJson(json) }
        }

        it("rejects unknown operations rather than silently skipping changes") {
            val json = """{"version":1,"entry":{"type":"unknown","txId":{"uuid":"$txUuid"}}}"""
            shouldThrow<SerializationException> { LogEntry.fromJson(json) }
        }

        it("rejects missing operation data") {
            val json = """{"version":1,"entry":{"type":"insert","txId":{"uuid":"$txUuid"},"tableName":"example"}}"""
            shouldThrow<SerializationException> { LogEntry.fromJson(json) }
        }

        it("rejects missing transaction IDs") {
            shouldThrow<SerializationException> {
                LogEntry.fromJson("""{"version":1,"entry":{"type":"commit"}}""")
            }
        }

        it("rejects truncated records") {
            shouldThrow<SerializationException> { LogEntry.fromJson(LogEntry.Commit(txId).toJson().dropLast(1)) }
        }

        it("validates transaction UUID versions when decoding") {
            val json = LogEntry.Commit(txId).toJson().replace(txUuid, "1ef00000-0000-4000-8000-000000000001")
            shouldThrow<IllegalStateException> { LogEntry.fromJson(json) }
        }

        it("validates row UUID versions when decoding") {
            val json = LogEntry.Delete(txId, "example", row.id).toJson().replace(rowUuid, "1ef00000-0000-4000-8000-000000000002")
            shouldThrow<IllegalStateException> { LogEntry.fromJson(json) }
        }
    })
