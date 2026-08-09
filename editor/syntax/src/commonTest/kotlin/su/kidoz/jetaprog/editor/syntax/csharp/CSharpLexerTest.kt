package su.kidoz.jetaprog.editor.syntax.csharp

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CSharpLexerTest {
    private val lexer = CSharpLexer()

    @Test
    fun `recognises modern declarations and literal constants`() {
        val source = "public sealed record User(bool Active) { required string Name { get; init; } = null; }"

        assertEquals(TokenType.MODIFIER, typeOf(source, "public"))
        assertEquals(TokenType.MODIFIER, typeOf(source, "sealed"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "record"))
        assertEquals(TokenType.MODIFIER, typeOf(source, "required"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "init"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "null"))
    }

    @Test
    fun `carries verbatim and raw string state across lines`() {
        val (_, verbatimState) = lexer.tokenizeLine("var path = @\"first", 0, 0, LexerState.Initial)
        assertTrue(verbatimState.inMultilineString)

        val (verbatimTokens, closedVerbatimState) = lexer.tokenizeLine("second\";", 1, 20, verbatimState)
        assertEquals(TokenType.STRING, verbatimTokens.first().type)
        assertTrue(!closedVerbatimState.inMultilineString)

        val (_, rawState) = lexer.tokenizeLine("var json = \"\"\"{", 0, 0, LexerState.Initial)
        assertTrue(rawState.inMultilineString)

        val (rawTokens, closedRawState) = lexer.tokenizeLine("}\"\"\";", 1, 20, rawState)
        assertEquals(TokenType.STRING, rawTokens.first().type)
        assertTrue(!closedRawState.inMultilineString)
    }

    @Test
    fun `recognises documentation comments and preprocessor directives`() {
        val source = "/// docs\n#if DEBUG\n#endif"

        assertEquals(TokenType.COMMENT_DOC, lexer.tokenize(source).first().type)
        assertEquals(TokenType.KEYWORD, typeOf(source, "#if"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "#endif"))
    }

    @Test
    fun `keeps arithmetic operators separate from numeric literals`() {
        val source = "var total = 1.5e+2m + 0xFFu - 0b1010;"

        assertEquals(TokenType.NUMBER, typeOf(source, "1.5e+2m"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "+"))
        assertEquals(TokenType.NUMBER, typeOf(source, "0xFFu"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "-"))
        assertEquals(TokenType.NUMBER, typeOf(source, "0b1010"))
    }

    private fun typeOf(
        source: String,
        tokenText: String,
    ): TokenType {
        val token = lexer.tokenize(source).firstOrNull { source.substring(it.start, it.end) == tokenText }
        assertNotNull(token, "expected token '$tokenText' in: $source")
        return token.type
    }
}
