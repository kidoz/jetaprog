package su.kidoz.jetaprog.common.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the shared CamelHump matcher: hump matching, middle matching via
 * `*`, fragment ranges, and the scoring order between candidates.
 */
class CamelHumpMatcherTest {
    @Test
    fun matchesHumpInitials() {
        val matcher = CamelHumpMatcher("FIF")
        assertTrue(matcher.matches("findInFiles"))
        assertEquals(listOf(IntRange(0, 0), IntRange(4, 4), IntRange(6, 6)), matcher.matchingFragments("findInFiles"))
    }

    @Test
    fun matchesPrefixContinuation() {
        val matcher = CamelHumpMatcher("findF")
        assertTrue(matcher.matches("findInFiles"))
        assertEquals(listOf(0..3, 6..6), matcher.matchingFragments("findInFiles"))
    }

    @Test
    fun matchesInsideWordAsFallback() {
        val matcher = CamelHumpMatcher("iles")
        assertTrue(matcher.matches("findInFiles"))
    }

    @Test
    fun starEnablesOrderedMiddleMatching() {
        val matcher = CamelHumpMatcher("f*Files")
        assertTrue(matcher.matches("findInFiles"))
        assertNull(matcher.matchingFragments("filesInOut"))
    }

    @Test
    fun acronymBoundaryIsAWordStart() {
        // "HT" should match the HTTP hump, not scattered letters.
        val matcher = CamelHumpMatcher("HTTPR")
        assertTrue(matcher.matches("parseHTTPResponse"))
        val fragments = requireNotNull(matcher.matchingFragments("parseHTTPResponse"))
        assertEquals(5, fragments.sumOf { it.count() })
    }

    @Test
    fun wordStartMatchOutranksMiddleMatch() {
        val matcher = CamelHumpMatcher("te")
        val startScore = matcher.matchingScore("terminalPanel")
        val middleScore = matcher.matchingScore("updateEditor")
        assertTrue(requireNotNull(startScore) > requireNotNull(middleScore))
    }

    @Test
    fun fewerGapsOutrankMoreGaps() {
        val matcher = CamelHumpMatcher("fi")
        val contiguous = matcher.matchingScore("findInFiles")
        val gappy = matcher.matchingScore("forEachItem")
        assertTrue(requireNotNull(contiguous) > requireNotNull(gappy))
    }

    @Test
    fun exactCaseFirstLetterOutranksDifferentCase() {
        val exact = CamelHumpMatcher("File").matchingScore("FileBadge")
        val lower = CamelHumpMatcher("file").matchingScore("FileBadge")
        assertTrue(requireNotNull(exact) > requireNotNull(lower))
    }

    @Test
    fun nonMatchesReturnNull() {
        val matcher = CamelHumpMatcher("zzz")
        assertNull(matcher.matchingFragments("findInFiles"))
        assertNull(matcher.matchingScore("findInFiles"))
    }

    @Test
    fun wordStartsSplitOnCamelUnderscoresAndDigits() {
        assertEquals(setOf(0, 5, 8), CamelHumpMatcher.wordStartIndexes("find_in_files"))
        assertEquals(setOf(0), CamelHumpMatcher.wordStartIndexes("abc"))
        assertEquals(setOf(0, 5, 9), CamelHumpMatcher.wordStartIndexes("parseHTTPResponse"))
    }
}
