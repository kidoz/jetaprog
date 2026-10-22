package su.kidoz.jetaprog.app.ui.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Root project instructions are copied ahead of the prompt and left unexpanded. */
class AgentInstructionsTest {
    @Test
    fun aBlankFileAddsNothing() {
        assertNull(instructionText("  \n"))
        assertEquals("Explain this", promptWithInstructions(null, "Explain this"))
        assertNull(instructionChip(emptyList(), included = false))
    }

    @Test
    fun importLinesStayLiteralAndALongFileIsCut() {
        val raw = "Use spaces.\n@import ./src/AGENTS.md\n"
        assertEquals("Use spaces.\n@import ./src/AGENTS.md", instructionText(raw))
        assertEquals(
            "Project instructions (AGENTS.md):\nUse spaces.\n@import ./src/AGENTS.md\n\nExplain this",
            promptWithInstructions(instructionText(raw), "Explain this"),
        )
        val cut = instructionText("a".repeat(AGENTS_MAX_CHARS + 20))
        assertEquals(AGENTS_MAX_CHARS, cut?.length)
        assertTrue(cut.orEmpty().all { it == 'a' })
    }

    @Test
    fun theChipIsOmittedWhenTheFileIsAlreadyAttached() {
        val attached = listOf(ComposerAttachment(label = "AGENTS.md", path = "docs/AGENTS.md"))
        assertEquals(AGENTS_FILE, instructionChip(emptyList(), included = true))
        assertNull(instructionChip(attached, included = true))
    }
}
