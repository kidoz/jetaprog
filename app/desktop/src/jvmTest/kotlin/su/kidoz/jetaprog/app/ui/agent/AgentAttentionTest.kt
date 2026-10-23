package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentAttentionTest {
    private val approval = Block.Approval("permission", "Run tests", "./gradlew test")
    private val diff =
        Block.Diff(
            "diff",
            "File.kt",
            "src",
            1,
            1,
            emptyList(),
            DiffDecision.PROPOSED,
            "/proj/src/File.kt",
            null,
        )

    private fun turns(vararg blocks: Block) = listOf(Turn.Agent("turn", "", blocks.toList()))

    @Test
    fun approvalIsAttentionWhileExecutionRemainsRunning() {
        val state = agentAttention(turns(approval, diff), true, null)
        assertEquals(AgentExecution.RUNNING, state.execution)
        assertEquals(AgentSessionStatus.WAITING_APPROVAL, state.status)
        assertEquals("permission", state.approval?.id)
        assertEquals(1, state.pendingFiles)
    }

    @Test
    fun settledOrHistoricalApprovalsCannotRequestPermissionAgain() {
        assertNull(agentAttention(turns(approval.copy(resolved = true)), true, null).approval)
        assertNull(agentAttention(turns(approval), false, null).approval)
        assertEquals(AgentSessionStatus.REVIEW_READY, agentAttention(turns(approval, diff), false, null).status)
    }

    @Test
    fun failuresAndInterruptionsRetainTheSeparateReviewRequirement() {
        val failed = agentAttention(turns(diff, diff.copy(id = "again")), false, "failed")
        assertEquals(AgentSessionStatus.FAILED, failed.status)
        assertEquals(1, failed.pendingFiles)
        val interrupted = agentAttention(turns(diff), false, null, interrupted = true)
        assertEquals(AgentSessionStatus.INTERRUPTED, interrupted.status)
        assertEquals(1, interrupted.pendingFiles)
        assertEquals(AgentSessionStatus.RUNNING, agentAttention(turns(diff), true, null).status)
    }

    @Test
    fun messageTextDoesNotInventAQuestionOrVerifiedCompletion() {
        val attention = agentAttention(turns(Block.Text("text", "Done! Shall I deploy?")), false, null)
        assertEquals(AgentSessionStatus.IDLE, attention.status)
        assertNull(attention.approval)
    }
}
