package su.kidoz.jetaprog.editor.syntax.java

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaLexerTest {
    private val lexer = JavaLexer()

    @Test
    fun `recognises modules records and literal constants`() {
        val source =
            "open module demo { requires java.base; exports demo.api; } " +
                "record User(boolean active) { boolean enabled = true; }"

        assertEquals(TokenType.KEYWORD, typeOf(source, "module"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "requires"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "exports"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "record"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "true"))
    }

    @Test
    fun `recognises non sealed as one modifier`() {
        val source = "public non-sealed class Child implements Parent {}"

        assertEquals(TokenType.MODIFIER, typeOf(source, "non-sealed"))
    }

    @Test
    fun `carries text block state across lines`() {
        val (_, firstState) = lexer.tokenizeLine("String json = \"\"\"{", 0, 0, LexerState.Initial)
        assertTrue(firstState.inMultilineString)

        val (tokens, finalState) = lexer.tokenizeLine("}\"\"\";", 1, 20, firstState)
        assertEquals(TokenType.STRING, tokens.first().type)
        assertTrue(!finalState.inMultilineString)
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
