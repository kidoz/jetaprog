package su.kidoz.jetaprog.app.ui.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `/bring` is a local command, and only undismissed worktree files are copied. */
class AgentBringTest {
    @Test
    fun onlyABareBringCommandIsLocal() {
        assertTrue(parseBringCommand("/bring"))
        assertTrue(parseBringCommand("  /BRING  "))
        assertFalse(parseBringCommand("/bring the changes"))
        assertFalse(parseBringCommand("Explain /bring"))
        assertFalse(parseBringCommand("/review"))
    }

    @Test
    fun dismissedFilesAndPathsOutsideTheCheckoutAreLeftOut() {
        val turns =
            listOf(
                Turn.Agent(
                    id = "a",
                    timeLabel = "",
                    blocks =
                        listOf(
                            diff("keep", "/wt/src/Reader.kt", DiffDecision.PROPOSED),
                            diff("gone", "/wt/src/Old.kt", DiffDecision.REJECTED),
                            diff("out", "/other/src/Writer.kt", DiffDecision.ACCEPTED),
                            diff("up", "/wt/../secret.txt", DiffDecision.PROPOSED),
                        ),
                ),
            )

        assertEquals(listOf("src/Reader.kt"), bringableRelativePaths("/wt", turns))
        assertNull(relativeCheckoutPath("/wt", "/wt/../secret.txt"))
        assertFalse(isSafeRelative("../secret.txt"))
        assertTrue(isSafeRelative("src/Reader.kt"))
    }

    private fun diff(
        id: String,
        path: String,
        decision: DiffDecision,
    ): Block.Diff =
        Block.Diff(
            id = id,
            fileName = path.substringAfterLast('/'),
            path = path.substringBeforeLast('/'),
            added = 1,
            removed = 0,
            lines = emptyList(),
            decision = decision,
            absolutePath = path,
            oldText = "old",
        )
}
