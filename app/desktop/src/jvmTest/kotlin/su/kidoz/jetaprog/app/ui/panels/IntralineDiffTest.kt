package su.kidoz.jetaprog.app.ui.panels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the word-level (intraline) diff used by the VCS diff view. */
class IntralineDiffTest {
    @Test
    fun marksChangedWordOnBothSides() {
        val ranges = intralineChangeRanges(base = "fun foo() {}", changed = "fun bar() {}")
        val result = requireNotNull(ranges)
        assertEquals(listOf(IntRange(4, 6)), result.baseRanges)
        assertEquals(listOf(IntRange(4, 6)), result.changedRanges)
    }

    @Test
    fun identicalLinesProduceNoRanges() {
        assertNull(intralineChangeRanges(base = "val x = 1", changed = "val x = 1"))
    }

    @Test
    fun emptyLinesAreSkipped() {
        assertNull(intralineChangeRanges(base = "", changed = "something"))
    }

    @Test
    fun changedWordsAreMarkedPerSide() {
        val result = requireNotNull(intralineChangeRanges(base = "a + b", changed = "x + y"))
        // The shared "+" stays unmarked; "a"/"b" and "x"/"y" are each marked.
        assertEquals(listOf(IntRange(0, 0), IntRange(4, 4)), result.baseRanges)
        assertEquals(listOf(IntRange(0, 0), IntRange(4, 4)), result.changedRanges)
        assertTrue(result.baseRanges.size == 2)
    }
}
