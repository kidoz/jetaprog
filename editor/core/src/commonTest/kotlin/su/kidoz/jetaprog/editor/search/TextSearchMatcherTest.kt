package su.kidoz.jetaprog.editor.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSearchMatcherTest {
    private val sample =
        """
        val name = "value"
        fun name() = Unit
        // another NAME here
        """.trimIndent()

    @Test
    fun literalMatchIsCaseInsensitiveByDefault() {
        val matches = TextSearchMatcher.matchesInText(sample, TextSearchQuery("name"))

        // "name" on lines 0 and 1, "NAME" on line 2
        assertEquals(listOf(0, 1, 2), matches.map { it.line })
        assertTrue(matches.all { it.endColumn > it.startColumn })
    }

    @Test
    fun caseSensitiveExcludesDifferentCase() {
        val matches =
            TextSearchMatcher.matchesInText(sample, TextSearchQuery("NAME", caseSensitive = true))

        assertEquals(listOf(2), matches.map { it.line })
    }

    @Test
    fun wholeWordDoesNotMatchSubstrings() {
        val text = "rename names name"
        val matches = TextSearchMatcher.matchesInText(text, TextSearchQuery("name", wholeWord = true))

        // only the standalone "name" (last token), not "rename" or "names"
        assertEquals(1, matches.size)
        assertEquals(text.lastIndexOf("name"), matches.single().startColumn)
    }

    @Test
    fun regexMatchesPattern() {
        val matches =
            TextSearchMatcher.matchesInText(sample, TextSearchQuery("fun\\s+\\w+", regex = true))

        assertEquals(listOf(1), matches.map { it.line })
    }

    @Test
    fun emptyOrInvalidQueryYieldsNoMatches() {
        assertEquals(emptyList(), TextSearchMatcher.matchesInText(sample, TextSearchQuery("")))
        assertEquals(emptyList(), TextSearchMatcher.matchesInText(sample, TextSearchQuery("(", regex = true)))
    }

    @Test
    fun reportsColumnOfMatch() {
        val matches = TextSearchMatcher.matchesInText("xx target yy", TextSearchQuery("target"))

        assertEquals(3, matches.single().startColumn)
        assertEquals(9, matches.single().endColumn)
    }
}
