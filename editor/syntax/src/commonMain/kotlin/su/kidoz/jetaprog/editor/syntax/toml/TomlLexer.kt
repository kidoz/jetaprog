package su.kidoz.jetaprog.editor.syntax.toml

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for TOML files (including Cargo.toml).
 */
public class TomlLexer : Lexer {
    override val languageId: String = "toml"

    private companion object {
        val CONSTANTS = setOf("true", "false")
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

        // Handle multiline string continuation
        if (state.inMultilineString) {
            return consumeMultilineStringContinuation(text, pos, line, state, baseOffset)
        }

        val char = text[pos]

        return when {
            char == '\n' -> Triple(Token(TokenType.NEWLINE, baseOffset + pos, 1, line), state, 1)
            char.isWhitespace() -> consumeWhitespace(text, pos, line, baseOffset)
            char == '#' -> consumeComment(text, pos, line, baseOffset)
            char == '[' -> consumeTableHeader(text, pos, line, baseOffset)
            text.startsWith("\"\"\"", pos) -> consumeMultilineString(text, pos, line, baseOffset, '"')
            text.startsWith("'''", pos) -> consumeMultilineLiteralString(text, pos, line, baseOffset)
            char == '"' -> consumeString(text, pos, line, baseOffset)
            char == '\'' -> consumeLiteralString(text, pos, line, baseOffset)
            char == '=' -> Triple(Token(TokenType.OPERATOR, baseOffset + pos, 1, line), state, 1)
            char == ',' -> Triple(Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line), state, 1)
            char == '.' -> Triple(Token(TokenType.PUNCTUATION, baseOffset + pos, 1, line), state, 1)
            char == '{' || char == '}' -> Triple(Token(TokenType.BRACKET, baseOffset + pos, 1, line), state, 1)
            char == '-' || char == '+' || char.isDigit() -> consumeNumber(text, pos, line, baseOffset)
            char.isLetter() || char == '_' -> consumeIdentifier(text, pos, line, baseOffset)
            else -> Triple(Token(TokenType.UNKNOWN, baseOffset + pos, 1, line), state, 1)
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
        return Triple(Token(TokenType.WHITESPACE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeComment(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length && text[pos + length] != '\n') {
            length++
        }
        return Triple(Token(TokenType.COMMENT_LINE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeTableHeader(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1

        // Check for array of tables [[...]]
        if (pos + 1 < text.length && text[pos + 1] == '[') {
            length = 2
        }

        // Find the closing brackets
        val closingBrackets = if (length == 2) "]]" else "]"

        while (pos + length < text.length) {
            if (text.startsWith(closingBrackets, pos + length)) {
                length += closingBrackets.length
                break
            }
            if (text[pos + length] == '\n') break
            length++
        }

        return Triple(Token(TokenType.TYPE, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeString(
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
                    break
                }

                char == '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }
        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeLiteralString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 1
        while (pos + length < text.length) {
            val char = text[pos + length]
            when {
                char == '\'' -> {
                    length++
                    break
                }

                char == '\n' -> {
                    break
                }

                else -> {
                    length++
                }
            }
        }
        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeMultilineString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
        quote: Char,
    ): Triple<Token, LexerState, Int> {
        var length = 3 // """
        val endPattern = "\"\"\""

        while (pos + length < text.length) {
            if (text.startsWith(endPattern, pos + length)) {
                length += 3
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true, stringDelimiter = endPattern),
            length,
        )
    }

    private fun consumeMultilineLiteralString(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 3 // '''
        val endPattern = "'''"

        while (pos + length < text.length) {
            if (text.startsWith(endPattern, pos + length)) {
                length += 3
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        return Triple(
            Token(TokenType.STRING, baseOffset + pos, length, line),
            LexerState(inMultilineString = true, stringDelimiter = endPattern),
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
        var length = 0
        val endPattern = state.stringDelimiter

        while (pos + length < text.length) {
            if (text.startsWith(endPattern, pos + length)) {
                length += endPattern.length
                return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), LexerState.Initial, length)
            }
            length++
        }

        return Triple(Token(TokenType.STRING, baseOffset + pos, length, line), state, length)
    }

    private fun consumeNumber(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0

        // Handle sign
        if (text[pos] == '+' || text[pos] == '-') {
            length++
        }

        // Check for special values (inf, nan)
        if (pos + length < text.length) {
            if (text.startsWith("inf", pos + length) || text.startsWith("nan", pos + length)) {
                length += 3
                return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
            }
        }

        // Check for hex, octal, binary
        if (pos + length + 1 < text.length && text[pos + length] == '0') {
            when (text[pos + length + 1].lowercaseChar()) {
                'x' -> {
                    length += 2
                    while (pos + length < text.length &&
                        (text[pos + length].isHexDigit() || text[pos + length] == '_')
                    ) {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }

                'o' -> {
                    length += 2
                    while (pos + length < text.length &&
                        (text[pos + length] in '0'..'7' || text[pos + length] == '_')
                    ) {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }

                'b' -> {
                    length += 2
                    while (pos + length < text.length &&
                        (text[pos + length] in '0'..'1' || text[pos + length] == '_')
                    ) {
                        length++
                    }
                    return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
                }
            }
        }

        // Decimal number with optional underscores
        while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
            length++
        }

        // Decimal part
        if (pos + length < text.length && text[pos + length] == '.') {
            if (pos + length + 1 < text.length && text[pos + length + 1].isDigit()) {
                length++
                while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                    length++
                }
            }
        }

        // Exponent part
        if (pos + length < text.length && text[pos + length].lowercaseChar() == 'e') {
            length++
            if (pos + length < text.length && (text[pos + length] == '+' || text[pos + length] == '-')) {
                length++
            }
            while (pos + length < text.length && (text[pos + length].isDigit() || text[pos + length] == '_')) {
                length++
            }
        }

        // Check for date-time (simplified)
        if (pos + length < text.length && (text[pos + length] == '-' || text[pos + length] == ':')) {
            while (pos + length < text.length &&
                (
                    text[pos + length].isDigit() ||
                        text[pos + length] in listOf('-', ':', 'T', 'Z', '.', '+')
                )
            ) {
                length++
            }
        }

        if (length == 0 || (length == 1 && (text[pos] == '+' || text[pos] == '-'))) {
            return Triple(Token(TokenType.OPERATOR, baseOffset + pos, 1, line), LexerState.Initial, 1)
        }

        return Triple(Token(TokenType.NUMBER, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun consumeIdentifier(
        text: String,
        pos: Int,
        line: Int,
        baseOffset: Int,
    ): Triple<Token, LexerState, Int> {
        var length = 0
        while (pos + length < text.length &&
            (text[pos + length].isLetterOrDigit() || text[pos + length] == '_' || text[pos + length] == '-')
        ) {
            length++
        }

        val word = text.substring(pos, pos + length)
        val type =
            when {
                word in CONSTANTS -> TokenType.CONSTANT
                else -> TokenType.IDENTIFIER
            }

        return Triple(Token(type, baseOffset + pos, length, line), LexerState.Initial, length)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
}
