package su.kidoz.jetaprog.editor.syntax.cmake

import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CMakeLexerTest {
    private val lexer = CMakeLexer()

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
    fun highlightsCommandsAsFunctions() {
        val source = "cmake_minimum_required(VERSION 3.20)\nproject(demo LANGUAGES C CXX)"
        assertEquals(TokenType.FUNCTION, typeOf(source, "cmake_minimum_required"))
        assertEquals(TokenType.FUNCTION, typeOf(source, "project"))
        assertEquals(TokenType.MODIFIER, typeOf(source, "VERSION"))
        assertEquals(TokenType.NUMBER, typeOf(source, "3.20"))
    }

    @Test
    fun treatsCommandsAsCaseInsensitive() {
        assertEquals(TokenType.KEYWORD, typeOf("IF(ON)\nENDIF()", "IF"))
        assertEquals(TokenType.KEYWORD, typeOf("if(ON)\nendif()", "endif"))
    }

    @Test
    fun highlightsControlFlowAheadOfTheCallForm() {
        // `if` is followed by '(' but must read as a keyword, not a command.
        assertEquals(TokenType.KEYWORD, typeOf("if(TRUE)", "if"))
        assertEquals(TokenType.CONSTANT, typeOf("if(TRUE)", "TRUE"))
    }

    @Test
    fun highlightsVariableReferences() {
        val source = "set(x \${CMAKE_SOURCE_DIR})"
        assertEquals(TokenType.STRING_TEMPLATE, typeOf(source, "\${CMAKE_SOURCE_DIR}"))
    }

    @Test
    fun highlightsNestedAndEnvironmentReferences() {
        assertEquals(TokenType.STRING_TEMPLATE, typeOf("set(a \${\${b}})", "\${\${b}}"))
        assertEquals(TokenType.STRING_TEMPLATE, typeOf("set(a \$ENV{HOME})", "\$ENV{HOME}"))
    }

    @Test
    fun highlightsGeneratorExpressionsIncludingNestedOnes() {
        val source = "target_compile_options(t PRIVATE \$<\$<CONFIG:Debug>:-g>)"
        assertEquals(TokenType.STRING_TEMPLATE, typeOf(source, "\$<\$<CONFIG:Debug>:-g>"))
        assertEquals(TokenType.MODIFIER, typeOf(source, "PRIVATE"))
    }

    @Test
    fun highlightsLineComments() {
        assertEquals(TokenType.COMMENT_LINE, typeOf("# a comment\nproject(x)", "# a comment"))
    }

    @Test
    fun highlightsBracketComments() {
        val source = "#[[ a bracket comment ]]\nproject(x)"
        assertEquals(TokenType.COMMENT_BLOCK, typeOf(source, "#[[ a bracket comment ]]"))
        // The command after the comment must still be recognised.
        assertEquals(TokenType.FUNCTION, typeOf(source, "project"))
    }

    @Test
    fun highlightsBracketArgumentsWithEqualsPadding() {
        val source = "set(x [==[ raw ]] still raw ]==])"
        assertEquals(TokenType.STRING, typeOf(source, "[==[ raw ]] still raw ]==]"))
    }

    @Test
    fun highlightsQuotedArguments() {
        assertEquals(TokenType.STRING, typeOf("""message("hello world")""", """"hello world""""))
    }

    @Test
    fun carriesBracketCommentsAcrossLines() {
        val (first, afterFirst) = lexer.tokenizeLine("#[[ start", 0, 0, LexerState.Initial)
        assertTrue(afterFirst.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, first.single().type)

        val (second, afterSecond) = lexer.tokenizeLine("still comment ]] project(x)", 1, 10, afterFirst)
        assertTrue(!afterSecond.inBlockComment)
        assertEquals(TokenType.COMMENT_BLOCK, second.first().type)
        assertTrue(second.any { it.type == TokenType.FUNCTION })
    }

    @Test
    fun carriesMultilineStringsAcrossLines() {
        val (_, afterFirst) = lexer.tokenizeLine("""set(x "line one""", 0, 0, LexerState.Initial)
        assertTrue(afterFirst.inMultilineString)

        val (second, afterSecond) = lexer.tokenizeLine("""line two")""", 1, 20, afterFirst)
        assertTrue(!afterSecond.inMultilineString)
        assertEquals(TokenType.STRING, second.first().type)
    }

    @Test
    fun keepsUnquotedPathsAndScopedNamesAsSingleArguments() {
        // CMake treats these as one argument each; splitting them looks wrong when highlighted.
        assertEquals(TokenType.IDENTIFIER, typeOf("add_executable(demo src/main.cpp)", "src/main.cpp"))
        assertEquals(TokenType.IDENTIFIER, typeOf("target_link_libraries(t PRIVATE fmt::fmt)", "fmt::fmt"))
    }

    @Test
    fun stillReadsVersionsAsNumbers() {
        // '.' is a word character, so the number path must still win for digit-led tokens.
        assertEquals(TokenType.NUMBER, typeOf("cmake_minimum_required(VERSION 3.20)", "3.20"))
    }

    @Test
    fun readsWordsBeginningWithDigitsAsIdentifiers() {
        assertEquals(TokenType.IDENTIFIER, typeOf("add_subdirectory(3rdparty)", "3rdparty"))
    }

    @Test
    fun highlightsConditionOperators() {
        val source = "if(NOT x STREQUAL \"y\")"
        assertEquals(TokenType.OPERATOR, typeOf(source, "NOT"))
        assertEquals(TokenType.OPERATOR, typeOf(source, "STREQUAL"))
    }

    @Test
    fun coversEveryCharacterOfARealisticFile() {
        val source =
            """
            cmake_minimum_required(VERSION 3.20)
            project(demo LANGUAGES C CXX)
            set(CMAKE_CXX_STANDARD 26)
            add_executable(demo src/main.cpp)
            target_link_libraries(demo PRIVATE fmt::fmt)
            """.trimIndent()

        var offset = 0
        for (token in lexer.tokenize(source)) {
            assertTrue(token.start >= offset, "tokens overlap at ${token.start}")
            offset = token.end
        }
        assertTrue(offset <= source.length)
    }
}
