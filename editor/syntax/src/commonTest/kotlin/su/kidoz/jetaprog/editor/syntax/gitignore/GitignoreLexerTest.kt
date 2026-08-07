package su.kidoz.jetaprog.editor.syntax.gitignore

import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitignoreLexerTest {
    private val lexer = GitignoreLexer()

    private fun tokensOf(source: String): List<Pair<TokenType, String>> =
        lexer.tokenize(source).map { token -> token.type to source.substring(token.start, token.end) }

    private fun typeOf(
        source: String,
        text: String,
    ): TokenType {
        val match = tokensOf(source).firstOrNull { it.second == text }
        assertNotNull(match, "expected a token with text '$text' in: $source")
        return match.first
    }

    @Test
    fun highlightsCommentsToTheEndOfTheLine() {
        val source = "# build output\nbuild/"
        assertEquals(TokenType.COMMENT_LINE, typeOf(source, "# build output"))
        assertEquals(TokenType.IDENTIFIER, typeOf(source, "build"))
    }

    @Test
    fun treatsHashAsALiteralAwayFromTheLineStart() {
        assertEquals(TokenType.IDENTIFIER, typeOf("notes#1.txt", "notes#1.txt"))
    }

    @Test
    fun highlightsNegationAsAKeyword() {
        val source = "!keep.log"
        assertEquals(TokenType.KEYWORD, typeOf(source, "!"))
        assertEquals(TokenType.IDENTIFIER, typeOf(source, "keep.log"))
    }

    @Test
    fun highlightsWildcards() {
        val source = "*.log\n**/tmp\nfile?.txt"
        assertEquals(TokenType.OPERATOR, typeOf(source, "*"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "**"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "?"))
    }

    @Test
    fun highlightsSeparatorsAsPunctuation() {
        assertEquals(TokenType.PUNCTUATION, typeOf("/app/build/", "/"))
    }

    @Test
    fun highlightsCharacterClassesAsASingleToken() {
        assertEquals(TokenType.CHARACTER, typeOf("file[0-9].txt", "[0-9]"))
        assertEquals(TokenType.CHARACTER, typeOf("[!ab].txt", "[!ab]"))
        // An unterminated '[' is just a literal.
        assertEquals(TokenType.IDENTIFIER, typeOf("[abc.txt", "["))
    }

    @Test
    fun highlightsEscapes() {
        assertEquals(TokenType.STRING_ESCAPE, typeOf("""\#notes""", """\#"""))
        assertEquals(TokenType.STRING_ESCAPE, typeOf("""a\ b""", """\ """))
    }

    @Test
    fun coversEveryCharacterOfALineExactlyOnce() {
        val source = "!/app/**/*.log"
        val tokens = lexer.tokenize(source)
        assertEquals(source.length, tokens.sumOf { it.length })
        var expectedStart = 0
        tokens.forEach { token ->
            assertEquals(expectedStart, token.start)
            expectedStart = token.end
        }
    }

    @Test
    fun reportsLineNumbersAndOffsetsAcrossLines() {
        val source = "build/\n*.log"
        val star = lexer.tokenize(source).first { it.type == TokenType.OPERATOR }
        assertEquals(1, star.line)
        assertEquals(source.indexOf('*'), star.start)
    }

    @Test
    fun ignoresLeadingIndentation() {
        val tokens = tokensOf("   build/")
        assertTrue(tokens.none { it.second.isBlank() }, "indentation should not produce tokens: $tokens")
        assertEquals(TokenType.IDENTIFIER, typeOf("   build/", "build"))
    }
}
