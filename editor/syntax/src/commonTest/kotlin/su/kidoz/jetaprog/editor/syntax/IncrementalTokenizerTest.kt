package su.kidoz.jetaprog.editor.syntax

import su.kidoz.jetaprog.editor.syntax.kotlin.KotlinLexer
import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalTokenizerTest {
    private val lexer = KotlinLexer()

    private val sample =
        """
        package demo

        import kotlin.math.abs

        /**
         * A sample class.
         */
        class Sample(private val value: Int) {
            fun compute(): Int {
                val doubled = value * 2
                // a line comment
                val text = "result: ${'$'}doubled"
                return abs(doubled)
            }
        }
        """.trimIndent()

    @Test
    fun initialTokenizationMatchesFullLexing() {
        val tokenizer = IncrementalTokenizer(lexer)
        assertSameHighlighting(sample, tokenizer.tokenize(sample))
    }

    @Test
    fun editingASingleLineMatchesFullLexing() {
        val tokenizer = IncrementalTokenizer(lexer)
        tokenizer.tokenize(sample)

        val edited = sample.replace("value * 2", "value * 21 + abs(3)")
        assertSameHighlighting(edited, tokenizer.tokenize(edited))
    }

    @Test
    fun insertingAndDeletingLinesMatchesFullLexing() {
        val tokenizer = IncrementalTokenizer(lexer)
        tokenizer.tokenize(sample)

        val withInsertedLine = sample.replace("fun compute(): Int {", "fun compute(): Int {\n        val extra = 1")
        assertSameHighlighting(withInsertedLine, tokenizer.tokenize(withInsertedLine))

        val withDeletedLine = sample.lines().filterNot { it.contains("line comment") }.joinToString("\n")
        assertSameHighlighting(withDeletedLine, tokenizer.tokenize(withDeletedLine))
    }

    @Test
    fun openingABlockCommentRelexesFollowingLines() {
        val tokenizer = IncrementalTokenizer(lexer)
        tokenizer.tokenize(sample)

        // Opening an unterminated block comment must flip every following
        // line to comment tokens even though their text did not change.
        val commented = sample.replace("fun compute(): Int {", "/* fun compute(): Int {")
        assertSameHighlighting(commented, tokenizer.tokenize(commented))

        // Closing it again must restore normal highlighting.
        assertSameHighlighting(sample, tokenizer.tokenize(sample))
    }

    @Test
    fun editsInsideMultilineStringsMatchFullLexing() {
        val tokenizer = IncrementalTokenizer(lexer)
        val withRawString =
            """
            val query = ${"\"\"\""}
                SELECT *
                FROM table
            ${"\"\"\""}
            val after = 1
            """.trimIndent()
        assertSameHighlighting(withRawString, tokenizer.tokenize(withRawString))

        val edited = withRawString.replace("FROM table", "FROM other")
        assertSameHighlighting(edited, tokenizer.tokenize(edited))
    }

    @Test
    fun repeatedEditsStayConsistent() {
        val tokenizer = IncrementalTokenizer(lexer)
        var content = sample
        tokenizer.tokenize(content)

        val edits =
            listOf<(String) -> String>(
                { it.replace("private val value", "private var value") },
                { it.replace("// a line comment", "/* now a block") },
                { it + "\n// trailing" },
                { it.replace("/* now a block", "// a line comment") },
                { it.replace("class Sample", "internal class Sample") },
            )
        edits.forEach { edit ->
            content = edit(content)
            assertSameHighlighting(content, tokenizer.tokenize(content))
        }
    }

    /**
     * Asserts that [actual] assigns the same token type to every
     * non-whitespace character as a full lex of [content] does.
     *
     * Token boundaries may legitimately differ (the incremental path splits
     * multi-line tokens per line), so equivalence is checked per character.
     */
    private fun assertSameHighlighting(
        content: String,
        actual: TokenList,
    ) {
        val expected = lexer.tokenize(content)
        val expectedTypes = typeByOffset(content, expected)
        val actualTypes = typeByOffset(content, actual)
        content.indices.forEach { offset ->
            if (!content[offset].isWhitespace()) {
                assertEquals(
                    expectedTypes[offset],
                    actualTypes[offset],
                    "Token type mismatch at offset $offset ('${content[offset]}')",
                )
            }
        }
    }

    private fun typeByOffset(
        content: String,
        tokens: TokenList,
    ): Array<TokenType?> {
        val types = arrayOfNulls<TokenType>(content.length)
        tokens.forEach { token ->
            (token.start until minOf(token.end, content.length)).forEach { offset ->
                types[offset] = token.type
            }
        }
        return types
    }
}
