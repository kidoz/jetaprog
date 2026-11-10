package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.acp.protocol.AcpPatch
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.app.ui.agent.AgentSessionTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCommunicationTest {
    @Test
    fun formDefaultsAndPrimitiveConstraintsAreValidatedBeforeSubmission() {
        val schema =
            su.kidoz.jetaprog.acp.protocol.AcpConnection.defaultJson.parseToJsonElement(
                """{
              "type":"object",
              "properties":{
                "count":{"type":"integer","minimum":1,"maximum":3,"default":2},
                "enabled":{"type":"boolean","default":false},
                "targets":{"type":"array","items":{"type":"string","enum":["a","b"]},"minItems":1},
                "name":{"type":"string","minLength":2,"maxLength":3}
              },
              "required":["count","enabled","name"]
            }""",
            ) as kotlinx.serialization.json.JsonObject
        val form = AgentQuestionForm(schema)
        val question = form.question("q", "Configure")
        assertEquals(listOf("2"), question.answers["count"])
        assertEquals(listOf("false"), question.answers["enabled"])
        assertFailsWith<IllegalArgumentException> { form.content(question) }
        val answered = question.copy(answers = question.answers + ("name" to listOf("😀a")))
        assertEquals(kotlinx.serialization.json.JsonPrimitive(false), form.content(answered)["enabled"])
        assertFailsWith<IllegalArgumentException> {
            form.content(answered.copy(answers = answered.answers + ("count" to listOf("4"))))
        }
        assertFailsWith<IllegalArgumentException> {
            form.content(answered.copy(answers = answered.answers + ("targets" to listOf("unknown"))))
        }
    }

    @Test
    fun compactionPatchesRetainIdentityAndClearExplicitly() {
        var state = AgentSessionTelemetry()
        state = state.applyTelemetry(SessionUpdate.SessionInfoUpdate(title = AcpPatch.Value("Title")))
        state = state.applyTelemetry(SessionUpdate.SessionInfoUpdate(updatedAt = AcpPatch.Value("now")))
        assertEquals("Title", state.title)
        state = state.applyTelemetry(SessionUpdate.SessionInfoUpdate(title = AcpPatch.Value(null)))
        assertNull(state.title)
        state = state.applyTelemetry(SessionUpdate.CompactionSummaryChunk("c", ContentBlock.Text("first")))
        state = state.applyTelemetry(SessionUpdate.CompactionSummaryChunk("c", ContentBlock.Text(" second")))
        state = state.applyTelemetry(SessionUpdate.CompactionUpdate("c", "completed"))
        assertEquals("first second", state.compactions.single().summary)
        state = state.applyTelemetry(SessionUpdate.CompactionUpdate("c", "completed", summary = AcpPatch.Value(null)))
        assertEquals("", state.compactions.single().summary)
    }
}
