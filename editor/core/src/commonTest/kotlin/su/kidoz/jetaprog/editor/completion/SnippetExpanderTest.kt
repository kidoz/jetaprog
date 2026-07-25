package su.kidoz.jetaprog.editor.completion

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetExpanderTest {
    @Test
    fun plainTextIsUnchangedAndCaretGoesToTheEnd() {
        val result = SnippetExpander.expand("printf")

        assertEquals("printf", result.text)
        assertEquals(6, result.caretOffset)
    }

    @Test
    fun functionPlaceholderIsDroppedAndCaretLandsInsideTheParentheses() {
        // clangd with --function-arg-placeholders answers exactly like this. Keeping the
        // default would insert "square(int x)", which does not compile.
        val result = SnippetExpander.expand("square(\${1:int x})")

        assertEquals("square()", result.text)
        assertEquals(7, result.caretOffset)
    }

    @Test
    fun multipleParametersCollapseAndCaretGoesToTheFirst() {
        val result = SnippetExpander.expand("clamp(\${1:int v}, \${2:int lo}, \${3:int hi})")

        assertEquals("clamp(, , )", result.text)
        assertEquals(6, result.caretOffset)
    }

    @Test
    fun explicitFinalStopWins() {
        val result = SnippetExpander.expand("if (\${1:cond}) {\n    \$0\n}")

        assertEquals("if () {\n    \n}", result.text)
        assertEquals(result.text.indexOf('\n') + 5, result.caretOffset)
    }

    @Test
    fun barePlaceholdersAreSupported() {
        val result = SnippetExpander.expand("vector<\$1>\$0")

        assertEquals("vector<>", result.text)
        assertEquals(8, result.caretOffset)
    }

    @Test
    fun lowestNumberedStopWinsWhenThereIsNoFinalStop() {
        val result = SnippetExpander.expand("a\${2:x}b\${1:y}c")

        assertEquals("abc", result.text)
        assertEquals(2, result.caretOffset)
    }

    @Test
    fun nestedPlaceholdersAreConsumedWhole() {
        val result = SnippetExpander.expand("f(\${1:\${2:inner}})")

        assertEquals("f()", result.text)
        assertEquals(2, result.caretOffset)
    }

    @Test
    fun choicePlaceholdersAreDropped() {
        val result = SnippetExpander.expand("access(\${1|public,private|})")

        assertEquals("access()", result.text)
        assertEquals(7, result.caretOffset)
    }

    @Test
    fun escapedDollarIsLiteral() {
        val result = SnippetExpander.expand("cost = \\\$5")

        assertEquals("cost = \$5", result.text)
    }

    @Test
    fun aLoneDollarIsLiteral() {
        val result = SnippetExpander.expand("shell \$ prompt")

        assertEquals("shell \$ prompt", result.text)
    }

    @Test
    fun unterminatedPlaceholderDoesNotLeakBraces() {
        val result = SnippetExpander.expand("f(\${1:oops")

        assertEquals("f(", result.text)
        assertEquals(2, result.caretOffset)
    }

    @Test
    fun twoDigitStopNumbersParse() {
        val result = SnippetExpander.expand("a\${10:x}b\${2:y}")

        assertEquals("ab", result.text)
        // Stop 2 is lower than stop 10, so the caret goes to where stop 2 sits: after "ab".
        assertEquals(2, result.caretOffset)
    }
}
