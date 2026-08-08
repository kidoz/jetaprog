package su.kidoz.jetaprog.editor.syntax.go

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoLexerTest {
    private val lexer = GoLexer()

    @Test
    fun `recognises declarations built in types constants and operators`() {
        val source = "func load[T any](input chan string) error { value := nil; return value }"

        assertEquals(TokenType.KEYWORD, typeOf(source, "func"))
        assertEquals(TokenType.TYPE, typeOf(source, "any"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "chan"))
        assertEquals(TokenType.TYPE, typeOf(source, "string"))
        assertEquals(TokenType.TYPE, typeOf(source, "error"))
        assertEquals(TokenType.OPERATOR, typeOf(source, ":="))
        assertEquals(TokenType.CONSTANT, typeOf(source, "nil"))
    }

    @Test
    fun `recognises based floating and imaginary numbers`() {
        val source = "var values = []complex128{0xff, 0b1010, 0o755, 1.25e-2, 2i}"
        val numbers =
            lexer
                .tokenize(source)
                .filter { it.type == TokenType.NUMBER }
                .map { source.substring(it.start, it.end) }

        assertTrue("0xff" in numbers)
        assertTrue("0b1010" in numbers)
        assertTrue("0o755" in numbers)
        assertTrue("1.25e-2" in numbers)
        assertTrue("2i" in numbers)
    }

    @Test
    fun `carries raw strings and block comments across lines`() {
        val (_, stringState) = lexer.tokenizeLine("query := `select", 0, 0, LexerState.Initial)
        assertTrue(stringState.inMultilineString)

        val (stringTokens, finalStringState) = lexer.tokenizeLine("value`", 1, 16, stringState)
        assertEquals(TokenType.STRING, stringTokens.first().type)
        assertTrue(!finalStringState.inMultilineString)

        val (_, commentState) = lexer.tokenizeLine("/* comment", 0, 0, LexerState.Initial)
        assertTrue(commentState.inBlockComment)
        val (commentTokens, finalCommentState) = lexer.tokenizeLine(" */", 1, 10, commentState)
        assertEquals(TokenType.COMMENT_BLOCK, commentTokens.first().type)
        assertTrue(!finalCommentState.inBlockComment)
    }

    private fun typeOf(
        source: String,
        tokenText: String,
    ): TokenType {
        val token =
            lexer
                .tokenize(source)
                .firstOrNull { source.substring(it.start, it.end) == tokenText }
        assertNotNull(token, "expected token '$tokenText' in: $source")
        return token.type
    }
}
