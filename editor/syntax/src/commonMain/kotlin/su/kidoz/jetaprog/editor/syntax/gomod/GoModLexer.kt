package su.kidoz.jetaprog.editor.syntax.gomod

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for Go module, workspace, and checksum files.
 *
 * `go.mod` and `go.work` share the same directives, comments, and version tokens.
 * `go.sum` has no directives; paths and checksums still highlight as identifiers
 * and versions.
 *
 * @property languageId Lexer id registered for this instance. One class serves
 * `go.mod`, `go.work`, and `go.sum`.
 */
public class GoModLexer(
    override val languageId: String = LANGUAGE_ID,
) : Lexer {
    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var offset = 0
        var state = LexerState.Initial
        text.split('\n').forEachIndexed { lineNumber, line ->
            val (lineTokens, nextState) = tokenizeLine(line, lineNumber, offset, state)
            tokens += lineTokens
            offset += line.length + 1
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
        var current = state
        if (current.inBlockComment) {
            val (consumed, closed) = consumeBlockComment(text, 0, lineNumber, startOffset, tokens, opening = false)
            position = consumed
            current = if (closed) LexerState.Initial else current
        }
        while (position < text.length) {
            position =
                when {
                    text[position].isWhitespace() -> {
                        skipWhitespace(text, position)
                    }

                    text.startsWith("//", position) -> {
                        tokens +=
                            Token(TokenType.COMMENT_LINE, startOffset + position, text.length - position, lineNumber)
                        text.length
                    }

                    text.startsWith("/*", position) -> {
                        val (consumed, closed) =
                            consumeBlockComment(text, position, lineNumber, startOffset, tokens, opening = true)
                        current = if (closed) LexerState.Initial else LexerState(inBlockComment = true)
                        if (!closed) text.length else position + consumed
                    }

                    text.startsWith("=>", position) -> {
                        tokens += Token(TokenType.OPERATOR, startOffset + position, 2, lineNumber)
                        position + 2
                    }

                    text[position] == '"' -> {
                        consumeString(text, position, lineNumber, startOffset, tokens)
                    }

                    text[position].isDigit() ||
                        (text[position] == 'v' && text.getOrNull(position + 1)?.isDigit() == true) -> {
                        consumeNumber(text, position, lineNumber, startOffset, tokens)
                    }

                    text[position] in BRACKETS -> {
                        tokens += Token(TokenType.BRACKET, startOffset + position, 1, lineNumber)
                        position + 1
                    }

                    text[position] in PUNCTUATION -> {
                        tokens += Token(TokenType.PUNCTUATION, startOffset + position, 1, lineNumber)
                        position + 1
                    }

                    isPathChar(text[position]) -> {
                        consumePath(text, position, lineNumber, startOffset, tokens)
                    }

                    else -> {
                        tokens += Token(TokenType.OPERATOR, startOffset + position, 1, lineNumber)
                        position + 1
                    }
                }
        }
        return tokens to current
    }

    public companion object {
        /** Lexer id for `go.mod`. */
        public const val LANGUAGE_ID: String = "go.mod"

        /** Lexer id for `go.work`. */
        public const val WORK_LANGUAGE_ID: String = "go.work"

        /** Lexer id for `go.sum`. */
        public const val SUM_LANGUAGE_ID: String = "go.sum"

        private const val BRACKETS = "()[]"
        private const val PUNCTUATION = ",:"
    }
}

private fun skipWhitespace(
    text: String,
    position: Int,
): Int {
    var index = position
    while (index < text.length && text[index].isWhitespace()) index++
    return index
}

private fun consumeString(
    text: String,
    position: Int,
    lineNumber: Int,
    startOffset: Int,
    tokens: MutableList<Token>,
): Int {
    var index = position + 1
    while (index < text.length && text[index] != '"') index++
    if (index < text.length) index++
    tokens += Token(TokenType.STRING, startOffset + position, index - position, lineNumber)
    return index
}

private fun consumeNumber(
    text: String,
    position: Int,
    lineNumber: Int,
    startOffset: Int,
    tokens: MutableList<Token>,
): Int {
    var index = position + 1
    while (index < text.length && (text[index].isLetterOrDigit() || text[index] in ".+-")) index++
    tokens += Token(TokenType.NUMBER, startOffset + position, index - position, lineNumber)
    return index
}

private fun consumePath(
    text: String,
    position: Int,
    lineNumber: Int,
    startOffset: Int,
    tokens: MutableList<Token>,
): Int {
    var index = position + 1
    while (index < text.length && isPathChar(text[index])) index++
    val value = text.substring(position, index)
    val type = if (value in GoModKeywords) TokenType.KEYWORD else TokenType.IDENTIFIER
    tokens += Token(type, startOffset + position, index - position, lineNumber)
    return index
}

private fun consumeBlockComment(
    text: String,
    position: Int,
    lineNumber: Int,
    startOffset: Int,
    tokens: MutableList<Token>,
    opening: Boolean,
): Pair<Int, Boolean> {
    val start = if (opening) position + 2 else position
    val close = text.indexOf("*/", start)
    val end = if (close < 0) text.length else close + 2
    if (end > position) {
        tokens += Token(TokenType.COMMENT_BLOCK, startOffset + position, end - position, lineNumber)
    }
    return (end - position) to (close >= 0)
}

private fun isPathChar(character: Char): Boolean = character.isLetterOrDigit() || character in "_./+-"

private val GoModKeywords = setOf("module", "go", "toolchain", "require", "exclude", "replace", "retract", "use")
