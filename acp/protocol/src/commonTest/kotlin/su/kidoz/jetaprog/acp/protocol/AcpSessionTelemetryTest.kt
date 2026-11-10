package su.kidoz.jetaprog.acp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AcpSessionTelemetryTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun metadataDistinguishesOmissionClearAndReplacement() {
        val absent = decode("""{"sessionUpdate":"session_info_update"}""")
        assertEquals(AcpPatch.Absent, assertIs<SessionUpdate.SessionInfoUpdate>(absent).title)
        val cleared = decode("""{"sessionUpdate":"session_info_update","title":null}""")
        assertEquals(AcpPatch.Value<String>(null), assertIs<SessionUpdate.SessionInfoUpdate>(cleared).title)
        val replacement = decode("""{"sessionUpdate":"session_info_update","title":"Refactor"}""")
        assertEquals(AcpPatch.Value("Refactor"), assertIs<SessionUpdate.SessionInfoUpdate>(replacement).title)
        assertEquals(cleared, decode(json.encodeToString(SessionUpdate.serializer(), cleared)))
    }

    @Test
    fun usageNoticesAndCompactionDecodeIndependentFixtures() {
        val usage =
            decode(
                """{"sessionUpdate":"usage_update","size":100000,"used":4100,"cost":{"amount":1.25,"currency":"credits"}}""",
            )
        assertEquals(SessionUpdate.UsageUpdate(100000, 4100, SessionCost(1.25, "credits")), usage)
        val notice = decode("""{"sessionUpdate":"notice","title":"Rate limit","severity":"_custom"}""")
        assertEquals(SessionUpdate.Notice("Rate limit", "_custom"), notice)
        val compaction =
            decode(
                """{"sessionUpdate":"compaction_update","compactionId":"c","status":"completed","summary":null,"error":null}""",
            )
        assertEquals(
            AcpPatch.Value<List<ContentBlock>>(null),
            assertIs<SessionUpdate.CompactionUpdate>(compaction).summary,
        )
        assertEquals(AcpPatch.Value<String>(null), compaction.error)
        assertEquals(compaction, decode(json.encodeToString(SessionUpdate.serializer(), compaction)))
    }

    private fun decode(text: String): SessionUpdate = json.decodeFromString(SessionUpdate.serializer(), text)
}
