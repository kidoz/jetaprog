package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.acp.protocol.AcpPatch
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.app.ui.agent.AgentSessionTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCommunicationTest {
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
