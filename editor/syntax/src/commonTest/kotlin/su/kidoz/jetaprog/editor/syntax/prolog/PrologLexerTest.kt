package su.kidoz.jetaprog.editor.syntax.prolog

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrologLexerTest {
    private val lexer = PrologLexer()

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
    fun highlightsLineCommentsToTheEndOfTheLine() {
        val source = "% greet the world\ngreet."
        assertEquals(TokenType.COMMENT_LINE, typeOf(source, "% greet the world"))
        assertEquals(TokenType.IDENTIFIER, typeOf(source, "greet"))
    }

    @Test
    fun highlightsBlockComments() {
        assertEquals(TokenType.COMMENT_BLOCK, typeOf("/* setup */ go.", "/* setup */"))
    }

    @Test
    fun blockCommentsSpanLinesThroughLexerState() {
        val (_, afterOpening) = lexer.tokenizeLine("/* first", 0, 0, LexerState.Initial)
        assertTrue(afterOpening.inBlockComment)

        val (tokens, afterClosing) = lexer.tokenizeLine("still */ go.", 1, 9, afterOpening)
        assertFalse(afterClosing.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, tokens.first().type)
    }

    @Test
    fun highlightsClauseNeckAndArrowsAsOperators() {
        val source = "head :- body, (X > 1 -> a ; b)."
        assertEquals(TokenType.OPERATOR, typeOf(source, ":-"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "->"))
        assertEquals(TokenType.OPERATOR, typeOf(source, ";"))
    }

    @Test
    fun highlightsPredicatesWithArgumentsAsFunctions() {
        val source = "discount(Price, Percent, Result) :- Result is Price * Percent / 100."
        assertEquals(TokenType.FUNCTION, typeOf(source, "discount"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "is"))
        assertEquals(TokenType.PARAMETER, typeOf(source, "Price"))
        assertEquals(TokenType.NUMBER, typeOf(source, "100"))
    }

    @Test
    fun highlightsVariablesIncludingAnonymousOnes() {
        val source = "member(_Elem, [Head | _])."
        assertEquals(TokenType.PARAMETER, typeOf(source, "_Elem"))
        assertEquals(TokenType.PARAMETER, typeOf(source, "Head"))
        assertEquals(TokenType.PARAMETER, typeOf(source, "_"))
    }

    @Test
    fun highlightsQuotedAtomsAndStrings() {
        val source = """greet :- write('hello world'), write("bye")."""
        assertEquals(TokenType.STRING, typeOf(source, "'hello world'"))
        assertEquals(TokenType.STRING, typeOf(source, "\"bye\""))
    }

    @Test
    fun honoursEscapesInsideQuotedAtoms() {
        assertEquals(TokenType.STRING, typeOf("""a('it\'s').""", """'it\'s'"""))
        assertEquals(TokenType.STRING, typeOf("a('it''s').", "'it''s'"))
    }

    @Test
    fun keepsTheClauseTerminatorOutOfNumbers() {
        val tokens = tokensOf("x(X) :- X is 5.")
        assertTrue(tokens.contains(TokenType.NUMBER to "5"))
        assertTrue(tokens.contains(TokenType.PUNCTUATION to "."))
    }

    @Test
    fun highlightsFloatsAndRadixLiterals() {
        assertEquals(TokenType.NUMBER, typeOf("pi(3.14159).", "3.14159"))
        assertEquals(TokenType.NUMBER, typeOf("e(2.0e10).", "2.0e10"))
        assertEquals(TokenType.NUMBER, typeOf("mask(0xff).", "0xff"))
    }

    @Test
    fun highlightsCharacterCodes() {
        assertEquals(TokenType.CHARACTER, typeOf("c(0'a).", "0'a"))
        assertEquals(TokenType.CHARACTER, typeOf("nl(0'\\n).", "0'\\n"))
    }

    @Test
    fun highlightsDirectivesAndDcgArrows() {
        val source = ":- module(pricing, []).\ngreeting --> [hello]."
        assertEquals(TokenType.OPERATOR, typeOf(source, ":-"))
        assertEquals(TokenType.KEYWORD, typeOf(source, "module"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "-->"))
    }

    @Test
    fun highlightsClrContractDirectivesAsFunctions() {
        val source = ":- clr_export(discount/3, det, [in(price, float), out(result, float)])."
        assertEquals(TokenType.FUNCTION, typeOf(source, "clr_export"))
        assertEquals(TokenType.FUNCTION, typeOf(source, "in"))
    }

    @Test
    fun highlightsControlConstants() {
        val source = "always :- true.\nnever :- fail."
        assertEquals(TokenType.CONSTANT, typeOf(source, "true"))
        assertEquals(TokenType.CONSTANT, typeOf(source, "fail"))
    }
}
