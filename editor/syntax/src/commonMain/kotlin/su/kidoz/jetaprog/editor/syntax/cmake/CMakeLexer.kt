package su.kidoz.jetaprog.editor.syntax.cmake

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the CMake language, covering `CMakeLists.txt` and `*.cmake` files.
 *
 * Handles the constructs that make CMake awkward to highlight: bracket comments
 * (`#[[ ... ]]`) and bracket arguments (`[==[ ... ]==]`) with matched `=` padding,
 * quoted arguments that may span lines, `${variable}` and `$ENV{...}` references, and
 * `$<...>` generator expressions. Command and control-flow names are matched
 * case-insensitively, as CMake itself treats them.
 */
public class CMakeLexer : Lexer {
    override val languageId: String = "cmake"

    private companion object {
        /** Control-flow and definition keywords, matched case-insensitively. */
        val KEYWORDS =
            setOf(
                "if",
                "elseif",
                "else",
                "endif",
                "foreach",
                "endforeach",
                "while",
                "endwhile",
                "break",
                "continue",
                "return",
                "function",
                "endfunction",
                "macro",
                "endmacro",
                "block",
                "endblock",
            )

        /** Condition operators, which CMake spells as bare words. */
        val OPERATOR_WORDS =
            setOf(
                "AND",
                "OR",
                "NOT",
                "STREQUAL",
                "STRLESS",
                "STRGREATER",
                "STRLESS_EQUAL",
                "STRGREATER_EQUAL",
                "EQUAL",
                "LESS",
                "GREATER",
                "LESS_EQUAL",
                "GREATER_EQUAL",
                "MATCHES",
                "VERSION_EQUAL",
                "VERSION_LESS",
                "VERSION_GREATER",
                "VERSION_LESS_EQUAL",
                "VERSION_GREATER_EQUAL",
                "IN_LIST",
                "DEFINED",
                "EXISTS",
                "COMMAND",
                "TARGET",
                "TEST",
                "POLICY",
                "IS_NEWER_THAN",
                "IS_DIRECTORY",
                "IS_SYMLINK",
                "IS_ABSOLUTE",
            )

        /** Frequently used named arguments, highlighted as modifiers. */
        val NAMED_ARGUMENTS =
            setOf(
                "PUBLIC",
                "PRIVATE",
                "INTERFACE",
                "STATIC",
                "SHARED",
                "MODULE",
                "OBJECT",
                "IMPORTED",
                "ALIAS",
                "GLOBAL",
                "REQUIRED",
                "QUIET",
                "EXACT",
                "COMPONENTS",
                "OPTIONAL_COMPONENTS",
                "NAMES",
                "PATHS",
                "HINTS",
                "DESTINATION",
                "FILES",
                "DIRECTORY",
                "TARGETS",
                "EXPORT",
                "NAME",
                "COMMAND",
                "WORKING_DIRECTORY",
                "PROPERTIES",
                "CACHE",
                "FORCE",
                "PARENT_SCOPE",
                "LANGUAGES",
                "VERSION",
                "DESCRIPTION",
                "HOMEPAGE_URL",
                "CONFIGURE_DEPENDS",
                "GENERATE",
                "APPEND",
                "PRIVATE_HEADER",
                "PUBLIC_HEADER",
            )

        /** Punctuation that is part of an unquoted argument rather than a separator. */
        val WORD_PUNCTUATION = setOf('_', '-', '.', '/', '+', ':')

        /** Values CMake treats as booleans. */
        val CONSTANTS =
            setOf(
                "TRUE",
                "FALSE",
                "ON",
                "OFF",
                "YES",
                "NO",
                "IGNORE",
                "NOTFOUND",
            )
    }

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var line = 0
        var state = LexerState.Initial

        while (pos < text.length) {
            val (token, newState, consumed) = nextToken(text, pos, line, state)
            if (token != null && token.type != TokenType.WHITESPACE && token.type != TokenType.NEWLINE) {
                tokens.add(token)
            }
            for (i in pos until pos + consumed) {
                if (text[i] == '\n') line++
            }
            pos += consumed
            state = newState
        }

        return TokenList(tokens)
    }

    override fun tokenizeLine(
        text: String,
        lineNumber: Int,
        startOffset: Int,
        state: LexerState,
    ): Pair<List<Token>, LexerState> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var currentState = state

        while (pos < text.length) {
            val (token, newState, consumed) = nextToken(text, pos, lineNumber, currentState, startOffset)
            if (token != null && token.type != TokenType.WHITESPACE) {
                tokens.add(token)
            }
            pos += consumed
            currentState = newState
        }

        return tokens to currentState
    }

    private fun nextToken(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int = 0,
    ): Triple<Token?, LexerState, Int> {
        if (pos >= text.length) return Triple(null, state, 0)

        if (state.inBlockComment) {
            return consumeUntilTerminator(text, pos, line, state, baseOffset, TokenType.COMMENT_BLOCK)
        }
        if (state.inMultilineString) {
            return consumeUntilTerminator(text, pos, line, state, baseOffset, TokenType.STRING)
        }

        val char = text[pos]

        return when {
            char == '\n' -> {
                Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            }

            char.isWhitespace() -> {
                consumeWhitespace(text, pos, line, baseOffset)
            }

            char == '#' -> {
                consumeComment(text, pos, line, baseOffset)
            }

            char == '[' && bracketOpenLength(text, pos) != null -> {
                consumeBracketArgument(text, pos, line, baseOffset)
            }

            char == '"' -> {
                consumeQuotedArgument(text, pos, line, baseOffset)
            }

            char == '$' -> {
                consumeReference(text, pos, line, state, baseOffset)
            }

            char == '(' || char == ')' -> {
                Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), state, 1)
            }

            char.isDigit() -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            isWordChar(char) -> {
                consumeWord(text, pos, line, baseOffset)
            }

            else -> {
                Triple(Token(TokenType.OPERATOR, baseOffset + pos, 1, line), state, 1)
            }
        }
    }

    /**
     * Characters that may appear in an unquoted argument.
     *
     * CMake treats `src/main.cpp` and `fmt::fmt` as single arguments, so path and scope
     * punctuation is part of the word rather than a run of operators.
     */
    private fun isWordChar(char: Char): Boolean = char.isLetterOrDigit() || char in WORD_PUNCTUATION

    /**
     * Returns the length of a bracket opener (`[`, `[=[`, `[==[` ...) starting at [pos],
     * or null when [pos] does not begin one.
     */
    private fun bracketOpenLength(
        text: String,
        pos: Int,
    ): Int? {
        if (text.getOrNull(pos) != '[') return null
        var equals = 0
        while (text.getOrNull(pos + 1 + equals) == '=') equals++
        return if (text.getOrNull(pos + 1 + equals) == '[') equals + 2 else null
    }

    /** The closing bracket matching an opener of [openLength] characters. */
    private fun bracketCloser(openLength: Int): String = "]" + "=".repeat(openLength - 2) + "]"

    private fun consumeWhitespace(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length && text[pos + length].isWhitespace() && text[pos + length] != '\n') {
            length++
        }
        return Triple(Token(TokenType.WHITESPACE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    /**
     * Consumes a `#` line comment, or a `#[[ ... ]]` bracket comment which may span lines.
     */
    private fun consumeComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val openLength = bracketOpenLength(text, pos + 1)
        if (openLength == null) {
            var length = 1
            while (pos + length < text.length && text[pos + length] != '\n') length++
            return Triple(Token(TokenType.COMMENT_LINE, baseOffset + pos, length, line), LexerState.Initial, length)
        }

        val closer = bracketCloser(openLength)
        val contentStart = pos + 1 + openLength
        val end = text.indexOf(closer, startIndex = contentStart)
        if (end < 0) {
            val length = text.length - pos
            return Triple(
                Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line),
                LexerState(inBlockComment = true, stringDelimiter = closer),
                length,
            )
        }
        val length = end + closer.length - pos
        return Triple(Token(TokenType.COMMENT_BLOCK, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    /**
     * Consumes a `[[ ... ]]` bracket argument, which may span lines.
     */
    private fun consumeBracketArgument(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val openLength = bracketOpenLength(text, pos) ?: return consumeWord(text, pos, line, baseOffset)
        val closer = bracketCloser(openLength)
        val end = text.indexOf(closer, startIndex = pos + openLength)
        if (end < 0) {
            val length = text.length - pos
            return Triple(
                Token(TokenType.STRING, baseOffset + pos, length, line),
                LexerState(inMultilineString = true, stringDelimiter = closer),
                length,
            )
        }
        val length = end + closer.length - pos
        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    /**
     * Consumes the continuation of a bracket comment or bracket argument that began on an
     * earlier line, ending at the terminator recorded in [state].
     */
    private fun consumeUntilTerminator(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
        tokenType: TokenType,
    ): Triple<Token, LexerState, Int> {
        val closer = state.stringDelimiter
        val end = if (closer.isEmpty()) -1 else text.indexOf(closer, startIndex = pos)
        if (end < 0) {
            val length = text.length - pos
            return Triple(Token(tokenType, baseOffset + pos, length, line), state, length)
        }
        val length = end + closer.length - pos
        return Triple(Token(tokenType, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    /**
     * Consumes a quoted argument. CMake allows these to span lines, so an unterminated
     * quote carries over to the next line rather than ending at the newline.
     */
    private fun consumeQuotedArgument(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                char == '"' -> {
                    length++
                    return Triple(
                        Token(TokenType.STRING, baseOffset + pos, length, line),
                        LexerState.Initial,
                        length,
                    )
                }

                else -> {
                    length++
                }
            }
        }
        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true, stringDelimiter = "\""),
            length,
        )
    }

    /**
     * Consumes `${var}`, `$ENV{var}`, `$CACHE{var}` and `$<generator expression>`.
     */
    private fun consumeReference(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var cursor = pos + 1
        while (cursor < text.length && text[cursor].isLetter()) cursor++

        val opener = text.getOrNull(cursor)
        val closer =
            when (opener) {
                '{' -> '}'
                '<' -> '>'
                else -> null
            }
                ?: return Triple(Token(TokenType.OPERATOR, baseOffset + pos, 1, line), state, 1)

        // Nested references are common: ${${outer}} and $<$<CONFIG:Debug>:flag>.
        var depth = 0
        while (cursor < text.length) {
            val char = text[cursor]
            if (char == opener) depth++
            if (char == closer) {
                depth--
                if (depth == 0) {
                    val length = cursor + 1 - pos
                    return Triple(
                        Token(TokenType.STRING_TEMPLATE, baseOffset + pos, length, line),
                        LexerState.Initial,
                        length,
                    )
                }
            }
            if (char == '\n') break
            cursor++
        }

        val length = cursor - pos
        return Triple(Token(TokenType.STRING_TEMPLATE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '.')) {
            length++
        }
        // A bare word may start with digits, e.g. 3rdparty; only treat it as a number
        // when nothing word-like follows.
        if (pos + length < text.length && isWordChar(text[pos + length])) {
            return consumeWord(text, pos, line, baseOffset)
        }
        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeWord(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length && isWordChar(text[pos + length])) length++
        if (length == 0) {
            return Triple(Token(TokenType.OPERATOR, baseOffset + pos, 1, line), LexerState.Initial, 1)
        }

        val word = text.substring(pos, pos + length)
        val type = classify(word, text, pos + length)
        return Triple(Token(type, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun classify(
        word: String,
        text: String,
        end: Int,
    ): TokenType {
        // A command is a word directly followed by '(', ignoring spaces.
        var cursor = end
        while (cursor < text.length && (text[cursor] == ' ' || text[cursor] == '\t')) cursor++
        val isCall = text.getOrNull(cursor) == '('

        return when {
            word.lowercase() in KEYWORDS -> TokenType.KEYWORD
            isCall -> TokenType.FUNCTION
            word in CONSTANTS -> TokenType.CONSTANT
            word in OPERATOR_WORDS -> TokenType.OPERATOR
            word in NAMED_ARGUMENTS -> TokenType.MODIFIER
            word.endsWith("-NOTFOUND") -> TokenType.CONSTANT
            else -> TokenType.IDENTIFIER
        }
    }
}
