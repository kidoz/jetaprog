package su.kidoz.jetaprog.acp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Independent fixtures for additive tool-call fields and nullable wire values. */
class AcpToolCallWireFormatTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun toolCallStartRawOutputMatchesIndependentFixture() {
        val update =
            SessionUpdate.ToolCallStart(
                toolCallId = "call-1",
                title = "Run tests",
                rawOutput = json.parseToJsonElement("""{"exitCode":0}"""),
            )

        assertWireEquals(
            """
            {
              "sessionUpdate":"tool_call",
              "toolCallId":"call-1",
              "title":"Run tests",
              "rawOutput":{"exitCode":0}
            }
            """,
            json.encodeToString(SessionUpdate.serializer(), update),
        )

        val decoded =
            json.decodeFromString(
                SessionUpdate.serializer(),
                """{"sessionUpdate":"tool_call","toolCallId":"call-1","title":"Run tests","rawOutput":{"exitCode":0}}""",
            )
        assertEquals(json.parseToJsonElement("""{"exitCode":0}"""), (decoded as SessionUpdate.ToolCallStart).rawOutput)
    }

    @Test
    fun toolCallProgressAdditiveFieldsMatchIndependentFixture() {
        val update =
            SessionUpdate.ToolCallProgress(
                toolCallId = "call-1",
                kind = ToolKind.EXECUTE,
                content = listOf(ToolCallContent(type = "terminal", terminalId = "terminal-1")),
                locations = listOf(ToolCallLocation(path = "/tmp/output.txt", line = 3)),
                rawInput = json.parseToJsonElement("""{"command":"pwd"}"""),
                rawOutput = json.parseToJsonElement("""{"output":"/tmp"}"""),
            )

        val fixture =
            """
            {
              "sessionUpdate":"tool_call_update",
              "toolCallId":"call-1",
              "kind":"execute",
              "content":[{"type":"terminal","terminalId":"terminal-1"}],
              "locations":[{"path":"/tmp/output.txt","line":3}],
              "rawInput":{"command":"pwd"},
              "rawOutput":{"output":"/tmp"}
            }
            """
        assertWireEquals(fixture, json.encodeToString(SessionUpdate.serializer(), update))

        val decoded = json.decodeFromString(SessionUpdate.serializer(), fixture) as SessionUpdate.ToolCallProgress
        assertEquals(ToolKind.EXECUTE, decoded.kind)
        assertEquals("terminal-1", decoded.content?.single()?.terminalId)
        assertEquals(ToolCallLocation("/tmp/output.txt", 3), decoded.locations.single())
        assertEquals(json.parseToJsonElement("""{"command":"pwd"}"""), decoded.rawInput)
        assertEquals(json.parseToJsonElement("""{"output":"/tmp"}"""), decoded.rawOutput)
    }

    @Test
    fun additiveFieldsAcceptMissingAndExplicitNullValues() {
        val progress =
            json.decodeFromString(
                SessionUpdate.serializer(),
                """
                {
                  "sessionUpdate":"tool_call_update",
                  "toolCallId":"call-1",
                  "kind":null,
                  "content":null,
                  "rawInput":null,
                  "rawOutput":null
                }
                """,
            ) as SessionUpdate.ToolCallProgress
        assertNull(progress.kind)
        assertNull(progress.content)
        assertNull(progress.rawInput)
        assertNull(progress.rawOutput)
        assertEquals(emptyList(), progress.locations)

        val start =
            json.decodeFromString(
                SessionUpdate.serializer(),
                """{"sessionUpdate":"tool_call","toolCallId":"call-1","title":"Run","rawOutput":null}""",
            ) as SessionUpdate.ToolCallStart
        assertNull(start.rawOutput)

        val content = json.decodeFromString(ToolCallContent.serializer(), """{"type":"terminal","terminalId":null}""")
        assertNull(content.terminalId)
    }

    private fun assertWireEquals(
        expected: String,
        actual: String,
    ) {
        assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(actual))
    }
}
