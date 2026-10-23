package su.kidoz.jetaprog.app.ui.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `/review` groups the session diff and stays local when it has no argument. */
class AgentReviewTest {
    @Test
    fun onlyABareReviewCommandIsLocal() {
        assertTrue(parseReviewCommand("/review"))
        assertTrue(parseReviewCommand("  /review  "))
        assertFalse(parseReviewCommand("/review the diff"))
        assertFalse(parseReviewCommand("Explain /review"))
        assertFalse(parseWorktreeCommand("/worktree now"))
        assertTrue(parseWorktreeCommand("/worktree"))
    }

    @Test
    fun filesAreGroupedAndPendingHunksStayOpen() {
        val turns =
            listOf(
                Turn.Agent(
                    id = "agent",
                    timeLabel = "",
                    blocks =
                        listOf(
                            diff("a", "/proj/src/Reader.kt", "src", added = 1, removed = 1),
                            diff(
                                "b",
                                "/proj/src/Reader.kt",
                                "src",
                                added = 2,
                                removed = 0,
                                decision = DiffDecision.ACCEPTED,
                            ),
                            diff("c", "/proj/src/Writer.kt", "src", added = 4, removed = 1),
                        ),
                ),
            )
        val files = reviewFiles(turns, mapOf("/proj/src/Reader.kt" to ReviewDelta(2, 1)))
        assertEquals(listOf("/proj/src/Reader.kt", "/proj/src/Writer.kt"), files.map { it.key })
        assertEquals(2, files[0].added)
        assertEquals(1, files[0].removed)
        assertEquals(listOf("a"), files[0].pendingBlockIds)
        assertNull(files[0].statusLabel)
        assertEquals("src", files[1].pathLabel)
        assertEquals(listOf("c"), files[1].pendingBlockIds)
    }

    @Test
    fun aSettledFileNamesTheDecision() {
        val accepted =
            reviewFiles(
                listOf(
                    Turn.Agent(
                        id = "agent",
                        timeLabel = "",
                        blocks = listOf(diff("a", "/proj/src/Reader.kt", "src", 1, 0, DiffDecision.ACCEPTED)),
                    ),
                ),
            )
        assertEquals("Kept", accepted.single().statusLabel)
        val dismissed =
            reviewFiles(
                listOf(
                    Turn.Agent(
                        id = "agent",
                        timeLabel = "",
                        blocks = listOf(diff("a", "/proj/src/Reader.kt", "src", 1, 0, DiffDecision.REJECTED)),
                    ),
                ),
            )
        assertEquals("Reverted", dismissed.single().statusLabel)
    }

    @Test
    fun netCountsExcludeUnchangedInteriorLinesAndHandleFileLifecycle() {
        assertEquals(ReviewDelta(2, 2), netReviewDelta("a\nstable\nb\n", "x\nstable\ny\n"))
        assertEquals(ReviewDelta(0, 0), netReviewDelta("same\n", "same\n"))
        assertEquals(ReviewDelta(2, 0), netReviewDelta(null, "a\nb\n"))
        assertEquals(ReviewDelta(0, 2), netReviewDelta("a\nb\n", null))
        assertEquals(ReviewDelta(0, 0), netReviewDelta(null, ""))
        assertEquals(ReviewDelta(1, 1), netReviewDelta("line", "line\n"))
        assertNull(netReviewDelta("a\n".repeat(1001), "b\n".repeat(1001)))
    }

    @Test
    fun fragmentOnlyDiffsNeverPretendToBeNetTotals() {
        val turns = listOf(Turn.Agent("agent", "", listOf(diff("a", "/proj/a", "", 9, 7))))
        val file = reviewFiles(turns).single()
        assertNull(file.added)
        assertNull(file.removed)
        assertEquals(1, AgentUiState(turns = turns).sessionChanges.size)
    }

    private fun diff(
        id: String,
        path: String,
        parent: String,
        added: Int,
        removed: Int,
        decision: DiffDecision = DiffDecision.PROPOSED,
    ): Block.Diff =
        Block.Diff(
            id = id,
            fileName = path.substringAfterLast('/'),
            path = parent,
            added = added,
            removed = removed,
            lines = emptyList(),
            decision = decision,
            absolutePath = path,
            oldText = null,
        )
}
