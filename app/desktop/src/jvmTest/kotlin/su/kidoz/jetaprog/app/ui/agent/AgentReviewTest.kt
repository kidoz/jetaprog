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
        val files = reviewFiles(turns)
        assertEquals(listOf("/proj/src/Reader.kt", "/proj/src/Writer.kt"), files.map { it.key })
        assertEquals(3, files[0].added)
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
        assertEquals("Accepted", accepted.single().statusLabel)
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
        assertEquals("Dismissed", dismissed.single().statusLabel)
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
