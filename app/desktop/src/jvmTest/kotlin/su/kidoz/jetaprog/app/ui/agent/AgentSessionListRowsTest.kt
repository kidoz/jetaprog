package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Session list rows: live status, diff counts, and saved-session fallbacks. */
class AgentSessionListRowsTest {
    @Test
    fun liveStatusPrefersApprovalThenRunningThenReview() {
        val streaming =
            summarizeSession(
                turns = turns(diff(added = 4, removed = 1), approval()),
                isStreaming = true,
                error = null,
                live = true,
            )
        assertEquals(AgentSessionStatus.WAITING_APPROVAL, streaming.status)
        assertEquals(4, streaming.addedLines)
        assertEquals(1, streaming.removedLines)

        val running =
            summarizeSession(
                turns = turns(diff(added = 2, removed = 0, decision = DiffDecision.ACCEPTED)),
                isStreaming = true,
                error = null,
                live = true,
            )
        assertEquals(AgentSessionStatus.RUNNING, running.status)

        val review =
            summarizeSession(
                turns = turns(diff(added = 3, removed = 3)),
                isStreaming = false,
                error = null,
                live = true,
            )
        assertEquals(AgentSessionStatus.REVIEW_READY, review.status)

        val failed =
            summarizeSession(
                turns = turns(diff(added = 1, removed = 1, decision = DiffDecision.ACCEPTED)),
                isStreaming = false,
                error = "The agent run failed",
                live = true,
            )
        assertEquals(AgentSessionStatus.FAILED, failed.status)
    }

    @Test
    fun savedSummaryMarksUnfinishedExecutionAsInterruptedAndKeepsTheCounts() {
        val idle =
            summarizeSession(
                turns = turns(diff(added = 4, removed = 2)),
                isStreaming = true,
                error = null,
                live = false,
            )
        assertEquals(AgentSessionStatus.INTERRUPTED, idle.status)
        assertEquals(4, idle.addedLines)
        assertEquals(2, idle.removedLines)

        val failed =
            summarizeSession(
                turns = turns(),
                isStreaming = false,
                error = "boom",
                live = false,
            )
        assertEquals(AgentSessionStatus.FAILED, failed.status)
    }

    @Test
    fun openChatIsFirstAndSavedCountsStayOnTheOthers() {
        val current =
            AgentSessionRecord(
                id = "current",
                title = "Old title",
                savedAtEpochMillis = 1_000,
                addedLines = 1,
                removedLines = 0,
                status = AgentSessionStatus.IDLE,
            )
        val other =
            AgentSessionRecord(
                id = "other",
                title = "Other",
                savedAtEpochMillis = 5_000,
                addedLines = 9,
                removedLines = 8,
                status = AgentSessionStatus.FAILED,
            )
        val rows =
            sessionListRows(
                history = listOf(other, current),
                activeRecordId = "current",
                turns = turns(diff(added = 4, removed = 2)),
                isStreaming = true,
                error = null,
                now = 9_000,
            )

        assertEquals(listOf("current", "other"), rows.map { it.id })
        assertTrue(rows[0].current)
        assertEquals(AgentSessionStatus.RUNNING, rows[0].status)
        assertEquals(4, rows[0].addedLines)
        assertEquals(2, rows[0].removedLines)
        assertFalse(rows[1].current)
        assertEquals(AgentSessionStatus.FAILED, rows[1].status)
        assertEquals(9, rows[1].addedLines)
        assertEquals(8, rows[1].removedLines)
    }

    @Test
    fun unsavedChatIsStillListed() {
        val rows =
            sessionListRows(
                history = listOf(AgentSessionRecord(id = "other", title = "Other", savedAtEpochMillis = 1)),
                activeRecordId = "fresh",
                turns = turns(diff(added = 1, removed = 0)),
                isStreaming = false,
                error = null,
                now = 9_000,
            )

        assertEquals("fresh", rows[0].id)
        assertEquals("Build it", rows[0].title)
        assertEquals(AgentSessionStatus.REVIEW_READY, rows[0].status)
        assertEquals(9_000, rows[0].savedAtEpochMillis)
        assertEquals("other", rows[1].id)
    }

    @Test
    fun labelsForTimeStatusAndDiffCounts() {
        assertEquals("just now", relativeSessionTime(1_000, 1_000))
        assertEquals("5m", relativeSessionTime(0, 5 * 60_000L))
        assertEquals("2h", relativeSessionTime(0, 2 * 3_600_000L))
        assertEquals("3d", relativeSessionTime(0, 3 * 86_400_000L))
        assertEquals("Idle", sessionStatusLabel(AgentSessionStatus.IDLE))
        assertEquals("Needs you", sessionStatusLabel(AgentSessionStatus.NEEDS_YOU))
        assertNull(sessionDiffCountLabel(0, 0))
        assertEquals("+3", sessionDiffCountLabel(3, 0))
        assertEquals("−2", sessionDiffCountLabel(0, 2))
        assertEquals("+3 −2", sessionDiffCountLabel(3, 2))
        assertEquals("Build it", sessionTitle(turns()))
        assertEquals("1234567890".repeat(4) + "…", sessionTitle(turns(userText = "1234567890".repeat(5))))
    }

    private fun turns(
        vararg blocks: Block,
        userText: String = "Build it",
    ): List<Turn> =
        listOf(
            Turn.User(id = "user", text = userText, timeLabel = "now"),
            Turn.Agent(id = "agent", timeLabel = "now", blocks = blocks.toList()),
        )

    private fun diff(
        added: Int,
        removed: Int,
        decision: DiffDecision = DiffDecision.PROPOSED,
    ): Block.Diff =
        Block.Diff(
            id = "d-$added-$removed",
            fileName = "Reader.kt",
            path = "src",
            added = added,
            removed = removed,
            lines = emptyList(),
            decision = decision,
            absolutePath = "/proj/src/Reader.kt",
            oldText = null,
        )

    private fun approval(): Block.Approval = Block.Approval(id = "call", title = "Run command", command = "gradle test")
}
