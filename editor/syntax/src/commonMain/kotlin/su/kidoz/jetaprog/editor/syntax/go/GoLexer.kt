package su.kidoz.jetaprog.editor.syntax.go

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/** Lexer for Go source code. */
public class GoLexer : Lexer {
    override val languageId: String = LANGUAGE_ID

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var position = 0
        var line = 0
        var state = LexerState.Initial

        while (position < text.length) {
            val (token, nextState, consumed) = nextToken(text, position, line, state)
            if (token != null && token.type != TokenType.WHITESPACE && token.type != TokenType.NEWLINE) {
                tokens += token
            }
            repeat(consumed) { offset ->
                if (text[position + offset] == '\n') line++
            }
            position += consumed
            state = nextState
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
        var position = 0
        var currentState = state

        while (position < text.length) {
            val (token, nextState, consumed) =
                nextToken(text, position, lineNumber, currentState, startOffset)
            if (token != null && token.type != TokenType.WHITESPACE) tokens += token
            position += consumed
            currentState = nextState
        }

        return tokens to currentState
    }

    private fun nextToken(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int = 0,
    ): Triple<Token?, LexerState, Int> {
        if (state.inBlockComment) return consumeBlockComment(text, position, line, state, baseOffset, false)
        if (state.inMultilineString) return consumeRawString(text, position, line, baseOffset, continuation = true)

        val character = text[position]
        return when {
            character == '\n' -> token(TokenType.NEWLINE, position, 1, line, state, baseOffset)
            character.isWhitespace() -> consumeWhitespace(text, position, line, baseOffset)
            text.startsWith("//", position) -> consumeLineComment(text, position, line, baseOffset)
            text.startsWith("/*", position) -> consumeBlockComment(text, position, line, state, baseOffset, true)
            character == '"' -> consumeQuotedLiteral(text, position, line, baseOffset, character, TokenType.STRING)
            character == '\'' -> consumeQuotedLiteral(text, position, line, baseOffset, character, TokenType.CHARACTER)
            character == '`' -> consumeRawString(text, position, line, baseOffset, continuation = false)
            character.isDigit() -> consumeNumber(text, position, line, baseOffset)
            character.isIdentifierStart() -> consumeIdentifier(text, position, line, baseOffset)
            character in BRACKETS -> token(TokenType.BRACKET, position, 1, line, state, baseOffset)
            text.startsWith(DECLARE_OPERATOR, position) -> consumeOperator(text, position, line, state, baseOffset)
            character in PUNCTUATION -> token(TokenType.PUNCTUATION, position, 1, line, state, baseOffset)
            else -> consumeOperator(text, position, line, state, baseOffset)
        }
    }

    private fun consumeWhitespace(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (text.getOrNull(position + length)?.let { it.isWhitespace() && it != '\n' } == true) length++
        return token(TokenType.WHITESPACE, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeLineComment(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 2
        while (text.getOrNull(position + length)?.let { it != '\n' } == true) length++
        return token(TokenType.COMMENT_LINE, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeBlockComment(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
        opening: Boolean,
    ): Triple<Token, LexerState, Int> {
        var length = if (opening) 2 else 0
        while (position + length < text.length) {
            if (text.startsWith("*/", position + length)) {
                length += 2
                return token(TokenType.COMMENT_BLOCK, position, length, line, LexerState.Initial, baseOffset)
            }
            length++
        }
        return token(
            TokenType.COMMENT_BLOCK,
            position,
            length,
            line,
            state.copy(inBlockComment = true),
            baseOffset,
        )
    }

    private fun consumeQuotedLiteral(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        quote: Char,
        type: TokenType,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (position + length < text.length) {
            when (text[position + length]) {
                '\\' -> {
                    length += if (position + length + 1 < text.length) 2 else 1
                }

                quote -> {
                    length++
                    break
                }

                '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }
        return token(type, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeRawString(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
        continuation: Boolean,
    ): Triple<Token, LexerState, Int> {
        var length = if (continuation) 0 else 1
        while (position + length < text.length) {
            if (text[position + length] == '`') {
                length++
                return token(TokenType.STRING, position, length, line, LexerState.Initial, baseOffset)
            }
            length++
        }
        return token(
            TokenType.STRING,
            position,
            length,
            line,
            LexerState(inMultilineString = true, stringDelimiter = "`"),
            baseOffset,
        )
    }

    private fun consumeNumber(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        val prefix = text.drop(position).take(2).lowercase()
        val isHexadecimal = prefix == "0x"

        if (prefix in NUMBER_PREFIXES) {
            length = 2
            while (text.getOrNull(position + length)?.isBasedDigitOrSeparator(prefix) == true) length++
        } else {
            while (text.getOrNull(position + length)?.isDigitOrSeparator() == true) length++
        }

        if (text.getOrNull(position + length) == '.') {
            length++
            while (text.getOrNull(position + length)?.isFractionDigit(isHexadecimal) == true) length++
        }

        val exponentMarkers = if (isHexadecimal) HEX_EXPONENT_MARKERS else DECIMAL_EXPONENT_MARKERS
        if (text.getOrNull(position + length) in exponentMarkers) {
            length++
            if (text.getOrNull(position + length) in EXPONENT_SIGNS) length++
            while (text.getOrNull(position + length)?.isDigitOrSeparator() == true) length++
        }
        if (text.getOrNull(position + length) == 'i') length++

        return token(TokenType.NUMBER, position, length.coerceAtLeast(1), line, LexerState.Initial, baseOffset)
    }

    private fun consumeIdentifier(
        text: String,
        position: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (text.getOrNull(position + length).isIdentifierPart()) length++
        val word = text.substring(position, position + length)
        val type =
            when {
                word in CONSTANTS -> TokenType.CONSTANT
                word in TYPES -> TokenType.TYPE
                word in KEYWORDS -> TokenType.KEYWORD
                text.getOrNull(position + length) == '(' -> TokenType.FUNCTION
                word.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
                else -> TokenType.IDENTIFIER
            }
        return token(type, position, length, line, LexerState.Initial, baseOffset)
    }

    private fun consumeOperator(
        text: String,
        position: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val operator = OPERATORS.firstOrNull { text.startsWith(it, position) }
        return if (operator == null) {
            token(TokenType.UNKNOWN, position, 1, line, state, baseOffset)
        } else {
            token(TokenType.OPERATOR, position, operator.length, line, state, baseOffset)
        }
    }

    private fun token(
        type: TokenType,
        position: Int,
        length: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> = Triple(Token(type, baseOffset + position, length, line), state, length)

    private fun Char.isBasedDigit(prefix: String): Boolean =
        when (prefix) {
            "0x" -> isHexDigit()
            "0b" -> this == '0' || this == '1'
            else -> this in '0'..'7'
        }

    private fun Char.isBasedDigitOrSeparator(prefix: String): Boolean = isBasedDigit(prefix) || this == '_'

    private fun Char.isDigitOrSeparator(): Boolean = isDigit() || this == '_'

    private fun Char.isFractionDigit(isHexadecimal: Boolean): Boolean =
        isDigitOrSeparator() || (isHexadecimal && isHexDigit())

    private fun Char.isHexDigit(): Boolean = isDigit() || lowercaseChar() in 'a'..'f'

    private fun Char?.isIdentifierStart(): Boolean = this != null && (isLetter() || this == '_')

    private fun Char?.isIdentifierPart(): Boolean = this != null && (isLetterOrDigit() || this == '_')

    private companion object {
        const val LANGUAGE_ID = "go"
        const val DECLARE_OPERATOR = ":="
        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')
        val PUNCTUATION = setOf('.', ',', ';', ':')
        val CONSTANTS = setOf("true", "false", "iota", "nil")
        val TYPES =
            setOf(
                "any",
                "bool",
                "byte",
                "comparable",
                "complex64",
                "complex128",
                "error",
                "float32",
                "float64",
                "int",
                "int8",
                "int16",
                "int32",
                "int64",
                "rune",
                "string",
                "uint",
                "uint8",
                "uint16",
                "uint32",
                "uint64",
                "uintptr",
            )
        val KEYWORDS =
            setOf(
                "break",
                "case",
                "chan",
                "const",
                "continue",
                "default",
                "defer",
                "else",
                "fallthrough",
                "for",
                "func",
                "go",
                "goto",
                "if",
                "import",
                "interface",
                "map",
                "package",
                "range",
                "return",
                "select",
                "struct",
                "switch",
                "type",
                "var",
            )
        val NUMBER_PREFIXES = setOf("0x", "0b", "0o")
        val HEX_EXPONENT_MARKERS = setOf('p', 'P')
        val DECIMAL_EXPONENT_MARKERS = setOf('e', 'E')
        val EXPONENT_SIGNS = setOf('+', '-')
        val OPERATORS =
            setOf(
                "<<=",
                ">>=",
                "&^=",
                "...",
                ":=",
                "++",
                "--",
                "==",
                "!=",
                "<=",
                ">=",
                "&&",
                "||",
                "<<",
                ">>",
                "&^",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "&=",
                "|=",
                "^=",
                "<-",
                "+",
                "-",
                "*",
                "/",
                "%",
                "&",
                "|",
                "^",
                "!",
                "=",
                "<",
                ">",
                "~",
            ).sortedByDescending(String::length)
    }
}
