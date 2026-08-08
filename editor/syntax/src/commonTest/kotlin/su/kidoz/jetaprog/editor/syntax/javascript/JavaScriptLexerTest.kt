package su.kidoz.jetaprog.editor.syntax.javascript

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaScriptLexerTest {
    @Test
    fun `recognises modern JavaScript keywords constants and operators`() {
        val lexer = JavaScriptLexer()
        val source = "export async function load() { return value?.item ?? null; }"

        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "export"))
        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "async"))
        assertEquals(TokenType.FUNCTION, typeOf(lexer, source, "load"))
        assertEquals(TokenType.OPERATOR, typeOf(lexer, source, "?."))
        assertEquals(TokenType.OPERATOR, typeOf(lexer, source, "??"))
        assertEquals(TokenType.CONSTANT, typeOf(lexer, source, "null"))
    }

    @Test
    fun `keeps arithmetic operators outside numeric literals`() {
        val lexer = JavaScriptLexer()
        val source = "const result = 1 - 2 + 3.5e-2;"
        val rendered = lexer.tokenize(source).map { token -> token.type to source.substring(token.start, token.end) }

        assertTrue(rendered.contains(TokenType.NUMBER to "1"))
        assertTrue(rendered.contains(TokenType.OPERATOR to "-"))
        assertTrue(rendered.contains(TokenType.NUMBER to "3.5e-2"))
    }

    @Test
    fun `recognises TypeScript declarations modifiers and types`() {
        val lexer = TypeScriptLexer()
        val source = "export interface User { readonly name: string; value: unknown }"

        assertEquals(TokenType.KEYWORD, typeOf(lexer, source, "interface"))
        assertEquals(TokenType.MODIFIER, typeOf(lexer, source, "readonly"))
        assertEquals(TokenType.TYPE, typeOf(lexer, source, "string"))
        assertEquals(TokenType.TYPE, typeOf(lexer, source, "unknown"))
    }

    @Test
    fun `carries template literal and documentation comment state across lines`() {
        val lexer = JavaScriptLexer()
        val (_, templateState) = lexer.tokenizeLine("const message = `hello", 0, 0, LexerState.Initial)
        assertTrue(templateState.inMultilineString)

        val (templateTokens, finalTemplateState) = lexer.tokenizeLine("world`;", 1, 25, templateState)
        assertEquals(TokenType.STRING_TEMPLATE, templateTokens.first().type)
        assertTrue(!finalTemplateState.inMultilineString)

        val (_, commentState) = lexer.tokenizeLine("/** docs", 0, 0, LexerState.Initial)
        assertTrue(commentState.inBlockComment)
        assertTrue(commentState.inDocComment)
        val (commentTokens, finalCommentState) = lexer.tokenizeLine(" */", 1, 8, commentState)
        assertEquals(TokenType.COMMENT_DOC, commentTokens.first().type)
        assertTrue(!finalCommentState.inBlockComment)
    }

    private fun typeOf(
        lexer: Lexer,
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
