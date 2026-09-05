package su.kidoz.jetaprog.editor.search

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [TextSearchMatcher.replaceInText] covering option handling
 * and replacement-text expansion.
 */
class TextSearchReplaceTest {
    @Test
    fun replacesLiteralOccurrencesCaseInsensitivelyByDefault() {
        val result =
            TextSearchMatcher.replaceInText(
                "Target target TARGET",
                TextSearchQuery("target"),
                "goal",
            )

        assertEquals(TextReplaceResult("goal goal goal", 3), result)
    }

    @Test
    fun caseSensitiveModeKeepsDifferentlyCasedText() {
        val result =
            TextSearchMatcher.replaceInText(
                "Target target",
                TextSearchQuery("target", caseSensitive = true),
                "goal",
            )

        assertEquals(TextReplaceResult("Target goal", 1), result)
    }

    @Test
    fun wholeWordModeSkipsPartialMatches() {
        val result =
            TextSearchMatcher.replaceInText(
                "class Person://PersonClass",
                TextSearchQuery("Person", wholeWord = true),
                "User",
            )

        assertEquals(TextReplaceResult("class User://PersonClass", 1), result)
    }

    @Test
    fun regexReplacementExpandsNumberedGroups() {
        val result =
            TextSearchMatcher.replaceInText(
                "firstName, lastName",
                TextSearchQuery("(\\w+), (\\w+)", regex = true),
                "$2 $1",
            )

        assertEquals(TextReplaceResult("lastName firstName", 1), result)
    }

    @Test
    fun regexReplacementExpandsBracedGroups() {
        val result =
            TextSearchMatcher.replaceInText(
                "a-b",
                TextSearchQuery("(\\w)-(\\w)", regex = true),
                "\${1}|\${2}",
            )

        assertEquals(TextReplaceResult("a|b", 1), result)
    }

    @Test
    fun regexReplacementExpandsUnknownGroupReferencesToEmpty() {
        val result =
            TextSearchMatcher.replaceInText(
                "abc",
                TextSearchQuery("(a)(b)", regex = true),
                "$2$9",
            )

        assertEquals(TextReplaceResult("bc", 1), result)
    }

    @Test
    fun literalReplacementKeepsDollarSignsVerbatim() {
        val result =
            TextSearchMatcher.replaceInText(
                "sum",
                TextSearchQuery("sum"),
                "$1 and \\$2",
            )

        assertEquals(TextReplaceResult("$1 and \\$2", 1), result)
    }

    @Test
    fun regexEscapesProduceLiteralCharacters() {
        val result =
            TextSearchMatcher.replaceInText(
                "ab",
                TextSearchQuery("(a)(b)", regex = true),
                "\\\\$1\\$2",
            )

        assertEquals(TextReplaceResult("\\a$2", 1), result)
    }

    @Test
    fun invalidRegexYieldsTextUnchanged() {
        val result =
            TextSearchMatcher.replaceInText(
                "abc",
                TextSearchQuery("(", regex = true),
                "x",
            )

        assertEquals(TextReplaceResult("abc", 0), result)
    }

    @Test
    fun emptyMatchesAreNotReplaced() {
        val result =
            TextSearchMatcher.replaceInText(
                "bc",
                TextSearchQuery("x*", regex = true),
                "-",
            )

        assertEquals(TextReplaceResult("bc", 0), result)
    }

    @Test
    fun replacesAcrossLinesPreservingSurroundingContent() {
        val result =
            TextSearchMatcher.replaceInText(
                "val old = 1\nval old = 2\n",
                TextSearchQuery("old"),
                "new",
            )

        assertEquals(TextReplaceResult("val new = 1\nval new = 2\n", 2), result)
    }
}
