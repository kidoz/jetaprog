package su.kidoz.jetaprog.editor.editing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextEditingOpsTest {
    // ------------------------------------------------------------------
    // autoIndentNewline
    // ------------------------------------------------------------------

    @Test
    fun newlineKeepsCurrentIndentation() {
        val text = "    val x = 1"
        val result = TextEditingOps.autoIndentNewline(text, text.length, text.length)
        assertEquals("    val x = 1\n    ", result.text)
        assertEquals(result.text.length, result.selectionStart)
    }

    @Test
    fun newlineAfterOpeningBraceAddsIndent() {
        val text = "fun main() {"
        val result = TextEditingOps.autoIndentNewline(text, text.length, text.length)
        assertEquals("fun main() {\n    ", result.text)
        assertEquals(result.text.length, result.selectionStart)
    }

    @Test
    fun newlineBetweenBracePairExpandsBlock() {
        val text = "fun main() {}"
        val caret = text.length - 1
        val result = TextEditingOps.autoIndentNewline(text, caret, caret)
        assertEquals("fun main() {\n    \n}", result.text)
        assertEquals("fun main() {\n    ".length, result.selectionStart)
    }

    @Test
    fun newlineInNestedIndentationPreservesIt() {
        val text = "fun main() {\n    if (x) {"
        val result = TextEditingOps.autoIndentNewline(text, text.length, text.length)
        assertEquals("fun main() {\n    if (x) {\n        ", result.text)
    }

    @Test
    fun newlineReplacesSelection() {
        val text = "abcdef"
        val result = TextEditingOps.autoIndentNewline(text, 2, 4)
        assertEquals("ab\nef", result.text)
        assertEquals(3, result.selectionStart)
    }

    // ------------------------------------------------------------------
    // autoCloseAfterInsert
    // ------------------------------------------------------------------

    @Test
    fun openParenAutoCloses() {
        // User typed '(' at the end: "foo("
        val result = TextEditingOps.autoCloseAfterInsert("foo(", 4, '(')
        assertEquals("foo()", result?.text)
        assertEquals(4, result?.selectionStart)
    }

    @Test
    fun openBraceBeforeIdentifierDoesNotAutoClose() {
        // "(" typed directly before a word: don't add ")"
        assertNull(TextEditingOps.autoCloseAfterInsert("(word", 1, '('))
    }

    @Test
    fun closingParenSkipsOverExisting() {
        // "foo(|)" then ')' typed -> "foo()|)" should become "foo()|"
        val result = TextEditingOps.autoCloseAfterInsert("foo())", 5, ')')
        assertEquals("foo()", result?.text)
        assertEquals(5, result?.selectionStart)
    }

    @Test
    fun quoteAutoCloses() {
        val result = TextEditingOps.autoCloseAfterInsert("val s = \"", 9, '"')
        assertEquals("val s = \"\"", result?.text)
        assertEquals(9, result?.selectionStart)
    }

    @Test
    fun quoteSkipsOverExistingQuote() {
        // val s = "x|" then '"' typed -> remove the duplicate
        val result = TextEditingOps.autoCloseAfterInsert("val s = \"x\"\"", 11, '"')
        assertEquals("val s = \"x\"", result?.text)
        assertEquals(11, result?.selectionStart)
    }

    @Test
    fun apostropheInsideWordDoesNotAutoClose() {
        // "don'" — apostrophe right after a word character
        assertNull(TextEditingOps.autoCloseAfterInsert("don'", 4, '\''))
    }

    // ------------------------------------------------------------------
    // toggleLineComment
    // ------------------------------------------------------------------

    @Test
    fun commentSingleLine() {
        val result = TextEditingOps.toggleLineComment("val x = 1", 0, 0, "//")
        assertEquals("// val x = 1", result.text)
    }

    @Test
    fun uncommentSingleLine() {
        val result = TextEditingOps.toggleLineComment("// val x = 1", 5, 5, "//")
        assertEquals("val x = 1", result.text)
    }

    @Test
    fun commentPreservesIndentation() {
        val result = TextEditingOps.toggleLineComment("    val x = 1", 4, 4, "//")
        assertEquals("    // val x = 1", result.text)
    }

    @Test
    fun commentMultipleSelectedLines() {
        val text = "a\nb\nc"
        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")
        assertEquals("// a\n// b\n// c", result.text)
    }

    @Test
    fun mixedLinesGetCommented() {
        val text = "// a\nb"
        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")
        assertEquals("// // a\n// b", result.text)
    }

    @Test
    fun allCommentedLinesGetUncommented() {
        val text = "// a\n// b"
        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")
        assertEquals("a\nb", result.text)
    }

    @Test
    fun pythonCommentPrefix() {
        val result = TextEditingOps.toggleLineComment("x = 1", 0, 0, "#")
        assertEquals("# x = 1", result.text)
    }

    // ------------------------------------------------------------------
    // indentLines / dedentLines
    // ------------------------------------------------------------------

    @Test
    fun tabWithoutSelectionInsertsIndentAtCaret() {
        val result = TextEditingOps.indentLines("ab", 1, 1)
        assertEquals("a    b", result.text)
        assertEquals(5, result.selectionStart)
    }

    @Test
    fun indentSelectedLines() {
        val text = "a\nb"
        val result = TextEditingOps.indentLines(text, 0, text.length)
        assertEquals("    a\n    b", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(result.text.length, result.selectionEnd)
    }

    @Test
    fun indentSkipsEmptyLines() {
        val text = "a\n\nb"
        val result = TextEditingOps.indentLines(text, 0, text.length)
        assertEquals("    a\n\n    b", result.text)
    }

    @Test
    fun dedentRemovesOneIndentUnit() {
        val text = "        a\n    b"
        val result = TextEditingOps.dedentLines(text, 0, text.length)
        assertEquals("    a\nb", result.text)
    }

    @Test
    fun dedentHandlesPartialIndent() {
        val result = TextEditingOps.dedentLines("  a", 3, 3)
        assertEquals("a", result.text)
    }

    @Test
    fun dedentLeavesUnindentedLinesAlone() {
        val result = TextEditingOps.dedentLines("a", 1, 1)
        assertEquals("a", result.text)
    }

    // ------------------------------------------------------------------
    // moveLines
    // ------------------------------------------------------------------

    @Test
    fun moveLineUp() {
        val text = "a\nb\nc"
        // Caret on line "b" (offset 2)
        val result = TextEditingOps.moveLines(text, 2, 2, up = true)
        assertEquals("b\na\nc", result?.text)
        assertEquals(0, result?.selectionStart)
    }

    @Test
    fun moveLineDown() {
        val text = "a\nb\nc"
        // Caret on line "b" (offset 2)
        val result = TextEditingOps.moveLines(text, 2, 2, up = false)
        assertEquals("a\nc\nb", result?.text)
        assertEquals(4, result?.selectionStart)
    }

    @Test
    fun moveFirstLineUpReturnsNull() {
        assertNull(TextEditingOps.moveLines("a\nb", 0, 0, up = true))
    }

    @Test
    fun moveLastLineDownReturnsNull() {
        assertNull(TextEditingOps.moveLines("a\nb", 2, 2, up = false))
    }

    @Test
    fun moveMultiLineSelectionDown() {
        val text = "a\nb\nc\nd"
        // Selection covering "a\nb"
        val result = TextEditingOps.moveLines(text, 0, 3, up = false)
        assertEquals("c\na\nb\nd", result?.text)
    }

    // ------------------------------------------------------------------
    // Line ending preservation
    // ------------------------------------------------------------------

    @Test
    fun crlfNewlineBetweenBracesPreservesConvention() {
        val text = "fun main() {\r\n    if (ready) {}\r\n"
        val caret = text.indexOf("{}") + 1

        val result = TextEditingOps.autoIndentNewline(text, caret, caret)

        assertEquals("fun main() {\r\n    if (ready) {\r\n        \r\n    }\r\n", result.text)
        assertEquals("fun main() {\r\n    if (ready) {\r\n        ".length, result.selectionStart)
    }

    @Test
    fun newlineInEmptyDocumentUsesLf() {
        val result = TextEditingOps.autoIndentNewline("", 0, 0)

        assertEquals("\n", result.text)
        assertEquals(1, result.selectionStart)
    }

    @Test
    fun newlineInMixedDocumentUsesFirstSeparatorWithoutNormalizing() {
        val text = "a\r\nb\n    c"

        val result = TextEditingOps.autoIndentNewline(text, text.length, text.length)

        assertEquals("a\r\nb\n    c\r\n    ", result.text)
    }

    @Test
    fun commentPreservesCrlfBlankLinesAndFinalNewline() {
        val text = "a\r\n\r\nb\r\n"

        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")

        assertEquals("// a\r\n\r\n// b\r\n", result.text)
        assertEquals(result.text.length, result.selectionEnd)
    }

    @Test
    fun uncommentPreservesLfWithoutAddingFinalNewline() {
        val text = "// a\n// b"

        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")

        assertEquals("a\nb", result.text)
    }

    @Test
    fun uncommentPreservesMixedLineEndings() {
        val text = "// a\r\n// b\n"

        val result = TextEditingOps.toggleLineComment(text, 0, text.length, "//")

        assertEquals("a\r\nb\n", result.text)
    }

    @Test
    fun indentPreservesCrlfBlankLinesAndFinalNewline() {
        val text = "a\r\n\r\nb\r\n"

        val result = TextEditingOps.indentLines(text, 0, text.length)

        assertEquals("    a\r\n\r\n    b\r\n", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(result.text.length, result.selectionEnd)
    }

    @Test
    fun dedentPreservesCrlfWithoutAddingFinalNewline() {
        val text = "        a\r\n    b"

        val result = TextEditingOps.dedentLines(text, 0, text.length)

        assertEquals("    a\r\nb", result.text)
        assertEquals(result.text.length, result.selectionEnd)
    }

    @Test
    fun dedentPreservesLfFinalNewline() {
        val text = "    a\n    b\n"

        val result = TextEditingOps.dedentLines(text, 0, text.length)

        assertEquals("a\nb\n", result.text)
    }

    @Test
    fun moveDownPreservesCrlfAndSelectionOffsetsWithoutFinalNewline() {
        val text = "a\r\nb\r\nc"
        val caret = text.indexOf('b')

        val result = TextEditingOps.moveLines(text, caret, caret, up = false)

        assertEquals("a\r\nc\r\nb", result?.text)
        assertEquals(result?.text?.indexOf('b'), result?.selectionStart)
    }

    @Test
    fun moveUpPreservesCrlfFinalNewline() {
        val text = "a\r\nb\r\nc\r\n"
        val caret = text.indexOf('c')

        val result = TextEditingOps.moveLines(text, caret, caret, up = true)

        assertEquals("a\r\nc\r\nb\r\n", result?.text)
        assertEquals(result?.text?.indexOf('c'), result?.selectionStart)
    }

    @Test
    fun moveDownPreservesLfFinalNewline() {
        val text = "a\nb\n"

        val result = TextEditingOps.moveLines(text, 0, 0, up = false)

        assertEquals("b\na\n", result?.text)
        assertEquals(2, result?.selectionStart)
    }

    @Test
    fun moveUpPreservesLfWithoutFinalNewline() {
        val text = "a\nb\nc"

        val result = TextEditingOps.moveLines(text, text.length, text.length, up = true)

        assertEquals("a\nc\nb", result?.text)
        assertEquals(3, result?.selectionStart)
    }

    @Test
    fun movePreservesMixedLineEndings() {
        val text = "a\r\nb\nc\r\n"

        val result = TextEditingOps.moveLines(text, 0, 0, up = false)

        assertEquals("b\r\na\nc\r\n", result?.text)
    }

    @Test
    fun moveLastContentLineDownReturnsNullWhenFileHasFinalNewline() {
        val text = "a\r\nb\r\n"
        val caret = text.indexOf('b')

        assertNull(TextEditingOps.moveLines(text, caret, caret, up = false))
    }

    // ------------------------------------------------------------------
    // findMatchingBracket
    // ------------------------------------------------------------------

    @Test
    fun matchesSimplePair() {
        val text = "(abc)"
        assertEquals(0 to 4, TextEditingOps.findMatchingBracket(text, 0))
        assertEquals(4 to 0, TextEditingOps.findMatchingBracket(text, 5))
    }

    @Test
    fun matchesNestedPairs() {
        val text = "{a{b}c}"
        assertEquals(0 to 6, TextEditingOps.findMatchingBracket(text, 0))
        assertEquals(2 to 4, TextEditingOps.findMatchingBracket(text, 3))
    }

    @Test
    fun unmatchedBracketReturnsNull() {
        assertNull(TextEditingOps.findMatchingBracket("(abc", 0))
    }

    @Test
    fun caretNotAtBracketReturnsNull() {
        assertNull(TextEditingOps.findMatchingBracket("abc", 1))
    }

    // ------------------------------------------------------------------
    // CommentSyntax
    // ------------------------------------------------------------------

    @Test
    fun commentSyntaxKnowsCommonLanguages() {
        assertEquals("//", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.KOTLIN))
        assertEquals("//", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.CSHARP))
        assertEquals("--", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.SQL))
        assertEquals("--", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.POSTGRESQL))
        assertEquals("--", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.STARROCKS))
        assertEquals("#", CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.PYTHON))
        assertNull(CommentSyntax.lineCommentPrefix(su.kidoz.jetaprog.editor.document.LanguageId.XML))
    }
}
