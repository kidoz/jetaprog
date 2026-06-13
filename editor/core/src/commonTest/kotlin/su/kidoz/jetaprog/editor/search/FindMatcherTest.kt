package su.kidoz.jetaprog.editor.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindMatcherTest {
    @Test
    fun emptyQueryReturnsNoMatches() {
        assertTrue(FindMatcher.findMatches("hello world", "").isEmpty())
    }

    @Test
    fun literalSearchIsCaseInsensitiveByDefault() {
        val matches = FindMatcher.findMatches("Foo foo FOO", "foo")
        assertEquals(3, matches.size)
        assertEquals(FindMatch(0, 3), matches[0])
        assertEquals(FindMatch(4, 7), matches[1])
        assertEquals(FindMatch(8, 11), matches[2])
    }

    @Test
    fun caseSensitiveSearchMatchesExactCaseOnly() {
        val matches =
            FindMatcher.findMatches(
                "Foo foo FOO",
                "foo",
                FindOptions(caseSensitive = true),
            )
        assertEquals(listOf(FindMatch(4, 7)), matches)
    }

    @Test
    fun literalSearchEscapesRegexMetacharacters() {
        val matches = FindMatcher.findMatches("a.c abc a.c", "a.c")
        assertEquals(listOf(FindMatch(0, 3), FindMatch(8, 11)), matches)
    }

    @Test
    fun wholeWordMatchesWordBoundariesOnly() {
        val matches =
            FindMatcher.findMatches(
                "cat catalog concat cat",
                "cat",
                FindOptions(wholeWord = true),
            )
        assertEquals(listOf(FindMatch(0, 3), FindMatch(19, 22)), matches)
    }

    @Test
    fun regexSearchFindsPatternMatches() {
        val matches =
            FindMatcher.findMatches(
                "x1 y22 z333",
                "[a-z]\\d+",
                FindOptions(regex = true),
            )
        assertEquals(listOf(FindMatch(0, 2), FindMatch(3, 6), FindMatch(7, 11)), matches)
    }

    @Test
    fun invalidRegexReturnsNoMatches() {
        val matches =
            FindMatcher.findMatches(
                "anything",
                "[unclosed",
                FindOptions(regex = true),
            )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun zeroLengthRegexMatchesAreDropped() {
        val matches =
            FindMatcher.findMatches(
                "abc",
                "x*",
                FindOptions(regex = true),
            )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun matchesSpanMultipleLines() {
        val matches = FindMatcher.findMatches("foo\nbar\nfoo", "foo")
        assertEquals(listOf(FindMatch(0, 3), FindMatch(8, 11)), matches)
    }

    @Test
    fun matchIndexAtOrAfterFindsNextMatch() {
        val matches = listOf(FindMatch(0, 3), FindMatch(10, 13), FindMatch(20, 23))
        assertEquals(0, FindMatcher.matchIndexAtOrAfter(matches, 0))
        assertEquals(1, FindMatcher.matchIndexAtOrAfter(matches, 5))
        assertEquals(2, FindMatcher.matchIndexAtOrAfter(matches, 14))
    }

    @Test
    fun matchIndexAtOrAfterWrapsToFirstMatch() {
        val matches = listOf(FindMatch(0, 3), FindMatch(10, 13))
        assertEquals(0, FindMatcher.matchIndexAtOrAfter(matches, 50))
    }

    @Test
    fun matchIndexAtOrAfterReturnsMinusOneForNoMatches() {
        assertEquals(-1, FindMatcher.matchIndexAtOrAfter(emptyList(), 0))
    }
}
