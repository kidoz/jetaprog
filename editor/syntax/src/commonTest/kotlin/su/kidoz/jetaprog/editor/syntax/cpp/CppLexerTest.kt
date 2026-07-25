package su.kidoz.jetaprog.editor.syntax.cpp

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CppLexerTest {
    private val lexer = CppLexer()

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
    fun recognisesCpp20ModuleAndCoroutineKeywords() {
        val source = "export module app; import std; co_await task(); co_return 0;"
        assertEquals(TokenType.KEYWORD, typeOf(source, "module"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "import"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "co_await"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "co_return"))
    }

    @Test
    fun recognisesCpp23AndCpp26Keywords() {
        val source = "if consteval { contract_assert(x > 0); }"
        // consteval joins constexpr and constinit in the modifier group.
        assertEquals(TokenType.MODIFIER, typeOf(source, "consteval"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "contract_assert"))
    }

    @Test
    fun recognisesReflectionOperatorAndSpliceTokens() {
        val source = "constexpr auto r = ^^int; using T = [: r :];"
        assertEquals(TokenType.OPERATOR, typeOf(source, "^^"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "[:"))
        assertEquals(TokenType.OPERATOR, typeOf(source, ":]"))
    }

    @Test
    fun stillLexesOrdinarySubscriptsAsBrackets() {
        val tokens = tokensOf("v[0] = 1;")
        assertTrue(tokens.any { it.first == TokenType.BRACKET && it.second == "[" })
        assertTrue(tokens.any { it.first == TokenType.BRACKET && it.second == "]" })
    }

    @Test
    fun lexesAttributesAsASingleAnnotation() {
        assertEquals(TokenType.ANNOTATION, typeOf("[[nodiscard]] int f();", "[[nodiscard]]"))
        assertEquals(TokenType.ANNOTATION, typeOf("[[deprecated(\"x\")]] void g();", "[[deprecated(\"x\")]]"))
    }

    @Test
    fun treatsLiteralConstantsAsConstants() {
        val source = "bool b = true; auto p = nullptr; bool c = false;"
        assertEquals(TokenType.CONSTANT, typeOf(source, "true"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "false"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "nullptr"))
    }

    @Test
    fun treatsFinalAndOverrideAsModifiers() {
        val source = "struct D final : B { void f() override; };"
        assertEquals(TokenType.MODIFIER, typeOf(source, "final"))
        assertEquals(TokenType.MODIFIER, typeOf(source, "override"))
    }

    @Test
    fun lexesPrefixedAndRawStringLiterals() {
        assertEquals(TokenType.STRING, typeOf("""auto s = u8"hi";""", """u8"hi""""))
        assertEquals(TokenType.CHARACTER, typeOf("auto c = U'x';", "U'x'"))
        assertEquals(TokenType.STRING, typeOf("""auto r = R"(a"b)";""", """R"(a"b)""""))
        assertEquals(TokenType.STRING, typeOf("""auto r = LR"(x)";""", """LR"(x)""""))
    }

    @Test
    fun lexesDigitSeparatorsAndBinaryLiterals() {
        assertEquals(TokenType.NUMBER, typeOf("auto n = 1'000'000;", "1'000'000"))
        assertEquals(TokenType.NUMBER, typeOf("auto n = 0b1010'1010;", "0b1010'1010"))
    }

    @Test
    fun recognisesTheSpaceshipOperator() {
        assertEquals(TokenType.OPERATOR, typeOf("auto c = a <=> b;", "<=>"))
    }

    @Test
    fun carriesRawStringStateAcrossLines() {
        val (_, afterFirst) = lexer.tokenizeLine("""auto s = R"delim(line one""", 0, 0, LexerState.Initial)
        assertTrue(afterFirst.inMultilineString)

        val (tokens, afterSecond) = lexer.tokenizeLine("""line two)delim";""", 1, 30, afterFirst)
        assertTrue(!afterSecond.inMultilineString)
        assertEquals(TokenType.STRING, tokens.first().type)
    }
}
