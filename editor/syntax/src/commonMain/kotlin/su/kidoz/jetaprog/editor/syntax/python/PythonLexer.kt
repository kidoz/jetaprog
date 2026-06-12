package su.kidoz.jetaprog.editor.syntax.python

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for the Python programming language.
 */
public class PythonLexer : Lexer {
    override val languageId: String = "python"

    private data class StringStart(
        val prefix: String,
        val quoteChar: Char,
        val isTriple: Boolean,
        val isFString: Boolean,
    )

    private companion object {
        val KEYWORDS =
            setOf(
                "and",
                "as",
                "assert",
                "async",
                "await",
                "break",
                "case",
                "class",
                "continue",
                "def",
                "del",
                "elif",
                "else",
                "except",
                "finally",
                "for",
                "from",
                "global",
                "if",
                "import",
                "in",
                "is",
                "lambda",
                "match",
                "nonlocal",
                "not",
                "or",
                "pass",
                "raise",
                "return",
                "try",
                "while",
                "with",
                "yield",
            )

        val CONSTANTS = setOf("True", "False", "None")

        val OPERATORS =
            setOf(
                "**=",
                "//=",
                "<<=",
                ">>=",
                "==",
                "!=",
                "<=",
                ">=",
                ":=",
                "**",
                "//",
                "<<",
                ">>",
                "+=",
                "-=",
                "*=",
                "/=",
                "%=",
                "&=",
                "|=",
                "^=",
                "->",
                "+",
                "-",
                "*",
                "/",
                "%",
                "=",
                "<",
                ">",
                "&",
                "|",
                "^",
                "~",
            )

        val BRACKETS = setOf('(', ')', '[', ']', '{', '}')

        val PUNCTUATION = setOf('.', ',', ';', ':')

        val STRING_PREFIX_CHARS = setOf('r', 'R', 'u', 'U', 'b', 'B', 'f', 'F')
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

        if (state.inMultilineString) {
            return consumeMultilineStringContinuation(text, pos, line, state, baseOffset)
        }

        val char = text[pos]

        return when {
            char == '\n' -> {
                Triple(
                    Token(TokenType.NEWLINE, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            char.isWhitespace() -> {
                consumeWhitespace(text, pos, line, baseOffset)
            }

            char == '#' -> {
                consumeLineComment(text, pos, line, baseOffset)
            }

            detectStringStart(text, pos) != null -> {
                consumeString(text, pos, line, baseOffset)
            }

            char == '@' -> {
                consumeDecorator(text, pos, line, baseOffset)
            }

            char.isDigit() || (char == '.' && pos + 1 < text.length && text[pos + 1].isDigit()) -> {
                consumeNumber(text, pos, line, baseOffset)
            }

            char.isLetter() || char == '_' -> {
                consumeIdentifier(text, pos, line, baseOffset)
            }

            char in BRACKETS -> {
                Triple(
                    Token(TokenType.BRACKET, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            char in PUNCTUATION -> {
                Triple(
                    Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line),
                    state,
                    1,
                )
            }

            else -> {
                consumeOperator(text, pos, line, state, baseOffset)
            }
        }
    }

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
        return Triple(
            Token(TokenType.WHITESPACE, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeLineComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        return Triple(
            Token(TokenType.COMMENT_LINE, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun detectStringStart(
        text: String,
        pos: Int,
    ): StringStart? {
        if (pos >= text.length) return null

        val possibleLengths = listOf(2, 1, 0)
        for (prefixLength in possibleLengths) {
            if (pos + prefixLength >= text.length) continue
            val prefix = text.substring(pos, pos + prefixLength)
            if (prefixLength > 0 && !prefix.all { it in STRING_PREFIX_CHARS }) continue

            val quotePos = pos + prefixLength
            val quoteChar = text[quotePos]
            if (quoteChar != '\'' && quoteChar != '"') continue

            val isTriple =
                quotePos + 2 < text.length &&
                    text[quotePos + 1] == quoteChar &&
                    text[quotePos + 2] == quoteChar
            val isFString = prefix.any { it == 'f' || it == 'F' }
            return StringStart(prefix, quoteChar, isTriple, isFString)
        }

        return null
    }

    private fun consumeString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val start =
            detectStringStart(text, pos)
                ?: return Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), LexerState.Initial, 1)
        val tokenType = if (start.isFString) TokenType.STRING_TEMPLATE else TokenType.STRING

        if (start.isTriple) {
            val delimiter = "${start.quoteChar}${start.quoteChar}${start.quoteChar}"
            var length = start.prefix.length + delimiter.length
            while (pos + length < text.length) {
                if (text.startsWith(delimiter, pos + length)) {
                    length += delimiter.length
                    return Triple(
                        Token(tokenType, baseOffset + pos, length, line),
                        LexerState.Initial,
                        length,
                    )
                }
                length++
            }
            return Triple(
                Token(tokenType, baseOffset + pos, length, line),
                LexerState(inMultilineString = true, stringDelimiter = start.prefix + delimiter),
                length,
            )
        }

        var length = start.prefix.length + 1
        while (pos + length < text.length) {
            val ch = text[pos + length]
            when {
                ch == '\\' && pos + length + 1 < text.length -> {
                    length += 2
                }

                ch == start.quoteChar -> {
                    length++
                    break
                }

                ch == '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }

        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeMultilineStringContinuation(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        val delimiter = state.stringDelimiter.takeLast(3)
        val isFString = state.stringDelimiter.any { it == 'f' || it == 'F' }
        val tokenType = if (isFString) TokenType.STRING_TEMPLATE else TokenType.STRING
        var length = 0

        while (pos + length < text.length) {
            if (delimiter.isNotEmpty() && text.startsWith(delimiter, pos + length)) {
                length += delimiter.length
                return Triple(
                    Token(tokenType, baseOffset + pos, length, line),
                    LexerState.Initial,
                    length,
                )
            }
            length++
        }

        return Triple(
            Token(tokenType, baseOffset + pos, length, line),
            state.copy(inMultilineString = true),
            length,
        )
    }

    private fun consumeDecorator(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length) {
            val ch = text[pos + length]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '.') {
                length++
            } else {
                break
            }
        }
        return Triple(
            Token(TokenType.ANNOTATION, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        if (text[pos] == '.') {
            length = 1
            while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                length++
            }
        } else if (pos + 1 < text.length && text[pos] == '0') {
            when (text[pos + 1].lowercaseChar()) {
                'x' -> {
                    length = 2
                    while (pos + length < text.length &&
                        (text[pos + length].isHexDigit() || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                'b' -> {
                    length = 2
                    while (pos + length < text.length &&
                        (text[pos + length] == '0' || text[pos + length] == '1' || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                'o' -> {
                    length = 2
                    while (pos + length < text.length &&
                        (text[pos + length] in '0'..'7' || text[pos + length] == '_')
                    ) {
                        length++
                    }
                }

                else -> {
                    length = consumeDecimalNumber(text, pos)
                }
            }
        } else {
            length = consumeDecimalNumber(text, pos)
        }

        if (pos + length < text.length && text[pos + length].lowercaseChar() == 'e') {
            length++
            if (pos + length < text.length && (text[pos + length] == '+' || text[pos + length] == '-')) {
                length++
            }
            while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                length++
            }
        }

        if (pos + length < text.length && (text[pos + length] == 'j' || text[pos + length] == 'J')) {
            length++
        }

        return Triple(
            Token(TokenType.NUMBER, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeDecimalNumber(
        text: String,
        pos: Int,
    ): Int {
        var length = 0
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
            length++
        }
        if (pos + length < text.length && text[pos + length] == '.') {
            if (pos + length + 1 < text.length && text[pos + length + 1].isDigit()) {
                length++
                while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                    length++
                }
            }
        }
        return length
    }

    private fun consumeIdentifier(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length &&
            (text[pos + length].isLetterOrDigit() || text[pos + length] == '_')
        ) {
            length++
        }

        val word = text.substring(pos, pos + length)
        val type =
            when {
                word in CONSTANTS -> TokenType.CONSTANT
                word in KEYWORDS -> TokenType.KEYWORD
                word.first().isUpperCase() -> TokenType.TYPE
                pos + length < text.length && text[pos + length] == '(' -> TokenType.FUNCTION
                else -> TokenType.IDENTIFIER
            }

        return Triple(
            Token(type, baseOffset + pos, length, line),
            LexerState.Initial,
            length,
        )
    }

    private fun consumeOperator(
        text: String,
        pos: Int,
        line: Int,
        state: LexerState,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        for (op in OPERATORS.sortedByDescending { it.length }) {
            if (text.startsWith(op, pos)) {
                return Triple(
                    Token(TokenType.OPERATOR, baseOffset + pos, op.length, line),
                    state,
                    op.length,
                )
            }
        }

        return Triple(
            Token(TokenType.UNKNOWN, baseOffset + pos, 1, line),
            state,
            1,
        )
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
