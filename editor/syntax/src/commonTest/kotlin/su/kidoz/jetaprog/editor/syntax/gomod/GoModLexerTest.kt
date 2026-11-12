package su.kidoz.jetaprog.editor.syntax.gomod

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoModLexerTest {
    private val lexer = GoModLexer()

    @Test
    fun recognisesDirectivesVersionsReplacementsAndComments() {
        val source = "require example.com/dep v1.2.3 // indirect\nreplace example.com/dep => ../local"

        assertEquals(TokenType.KEYWORD, typeOf(source, "require"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "replace"))
        assertEquals(TokenType.IDENTIFIER, typeOf(source, "example.com/dep"))
        assertEquals(TokenType.NUMBER, typeOf(source, "v1.2.3"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "=>"))
        assertEquals(TokenType.COMMENT_LINE, typeOf(source, "// indirect"))
    }

    @Test
    fun carriesBlockCommentsAcrossLines() {
        val (_, open) = lexer.tokenizeLine("/* note", 0, 0, LexerState.Initial)
        assertTrue(open.inBlockComment)
        val (tokens, closed) = lexer.tokenizeLine(" */ go 1.25.0", 1, 8, open)
        assertEquals(TokenType.COMMENT_BLOCK, tokens.first().type)
        assertEquals(TokenType.KEYWORD, tokens[1].type)
        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertTrue(!closed.inBlockComment)
    }

    @Test
    fun checksumLinesHighlightVersions() {
        val sum = GoModLexer(GoModLexer.SUM_LANGUAGE_ID)
        val source = "github.com/foo/bar v1.2.3 h1:abcdef"
        val numbers =
            sum
                .tokenize(source)
                .filter { it.type == TokenType.NUMBER }
                .map { source.substring(it.start, it.end) }
        assertEquals(listOf("v1.2.3"), numbers)
        assertEquals(GoModLexer.SUM_LANGUAGE_ID, sum.languageId)
    }

    private fun typeOf(
        source: String,
        tokenText: String,
    ): TokenType {
        val token =
            lexer.tokenize(source).first { token ->
                source.substring(token.start, token.end) == tokenText
            }
        return token.type
    }
}
