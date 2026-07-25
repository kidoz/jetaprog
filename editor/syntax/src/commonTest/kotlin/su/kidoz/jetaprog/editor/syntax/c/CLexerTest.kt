package su.kidoz.jetaprog.editor.syntax.c

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CLexerTest {
    private val lexer = CLexer()

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
    fun coversEveryCharacterOfTheInput() {
        val source = "int main(void) { return 0; }\n"
        var offset = 0
        for (token in lexer.tokenize(source)) {
            assertTrue(token.start >= offset, "tokens overlap at ${token.start}")
            offset = token.end
        }
        assertTrue(offset <= source.length)
    }

    @Test
    fun recognisesC23Keywords() {
        val source = "constexpr bool ok = true; static_assert(ok); typeof_unqual(x) y;"
        assertEquals(TokenType.MODIFIER, typeOf(source, "constexpr"))
        assertEquals(TokenType.TYPE, typeOf(source, "bool"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "true"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "static_assert"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "typeof_unqual"))
    }

    @Test
    fun recognisesNullptrAndBitIntTypes() {
        val source = "_BitInt(128) n = 0; char *p = nullptr;"
        assertEquals(TokenType.TYPE, typeOf(source, "_BitInt"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "nullptr"))
    }

    @Test
    fun recognisesC23PreprocessorDirectives() {
        for (directive in listOf("#embed", "#elifdef", "#elifndef", "#warning", "#include")) {
            val tokens = tokensOf("$directive <stdio.h>")
            assertEquals(
                TokenType.KEYWORD,
                tokens.first().first,
                "$directive should lex as a keyword",
            )
            assertEquals(directive, tokens.first().second)
        }
    }

    @Test
    fun treatsStringifyOperatorAsOperatorNotDirective() {
        val tokens = tokensOf("#define STR(x) #x")
        assertTrue(tokens.any { it.first == TokenType.OPERATOR && it.second == "#" })
    }

    @Test
    fun lexesAttributesAsASingleAnnotation() {
        val source = "[[nodiscard]] int f(void);"
        assertEquals(TokenType.ANNOTATION, typeOf(source, "[[nodiscard]]"))
    }

    @Test
    fun lexesBinaryLiteralsAndDigitSeparators() {
        assertEquals(TokenType.NUMBER, typeOf("int x = 0b1010'1010;", "0b1010'1010"))
        assertEquals(TokenType.NUMBER, typeOf("int x = 1'000'000;", "1'000'000"))
        assertEquals(TokenType.NUMBER, typeOf("double d = 0x1.8p3;", "0x1.8p3"))
    }

    @Test
    fun lexesBitIntAndDecimalLiteralSuffixes() {
        assertEquals(TokenType.NUMBER, typeOf("x = 42wb;", "42wb"))
        assertEquals(TokenType.NUMBER, typeOf("x = 42ull;", "42ull"))
        assertEquals(TokenType.NUMBER, typeOf("x = 1.5df;", "1.5df"))
    }

    @Test
    fun doesNotSwallowIdentifiersFollowingANumber() {
        // "0 xor" must not be read as a hex literal or a suffix.
        val tokens = tokensOf("int a = 0; int b = 1;")
        assertTrue(tokens.any { it.first == TokenType.NUMBER && it.second == "0" })
        assertTrue(tokens.any { it.first == TokenType.NUMBER && it.second == "1" })
    }

    @Test
    fun lexesPrefixedStringAndCharacterLiterals() {
        assertEquals(TokenType.STRING, typeOf("""const char *s = u8"hi";""", """u8"hi""""))
        assertEquals(TokenType.CHARACTER, typeOf("int c = L'x';", "L'x'"))
    }

    @Test
    fun classifiesUppercaseIdentifiersAsConstants() {
        val source = "int n = MAX_SIZE;"
        assertEquals(TokenType.CONSTANT, typeOf(source, "MAX_SIZE"))
    }

    @Test
    fun classifiesCallsAsFunctions() {
        assertEquals(TokenType.FUNCTION, typeOf("printf(\"x\");", "printf"))
    }

    @Test
    fun carriesBlockCommentStateAcrossLines() {
        val (firstTokens, stateAfterFirst) = lexer.tokenizeLine("/* start", 0, 0, LexerState.Initial)
        assertTrue(stateAfterFirst.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, firstTokens.single().type)

        val (secondTokens, stateAfterSecond) = lexer.tokenizeLine("still comment", 1, 9, stateAfterFirst)
        assertTrue(stateAfterSecond.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, secondTokens.single().type)

        val (thirdTokens, stateAfterThird) = lexer.tokenizeLine("end */ int x;", 2, 23, stateAfterSecond)
        assertTrue(!stateAfterThird.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, thirdTokens.first().type)
        assertTrue(thirdTokens.any { it.type == TokenType.TYPE })
    }

    @Test
    fun tokenizeLineOffsetsAreDocumentRelative() {
        val (tokens, _) = lexer.tokenizeLine("int x;", lineNumber = 3, startOffset = 100, state = LexerState.Initial)
        val first: Token = tokens.first()
        assertEquals(100, first.start)
        assertEquals(3, first.line)
    }
}
