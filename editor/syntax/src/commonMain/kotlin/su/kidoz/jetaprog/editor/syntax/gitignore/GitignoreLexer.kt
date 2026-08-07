package su.kidoz.jetaprog.editor.syntax.gitignore

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Lexer for `.gitignore` files and the other exclude files sharing its syntax.
 *
 * Every construct is line-scoped — a comment runs to the end of its line and a
 * pattern never continues onto the next one — so no state crosses lines.
 */
public class GitignoreLexer : Lexer {
    override val languageId: String = "gitignore"

    override fun tokenize(text: String): TokenList {
        val tokens = mutableListOf<Token>()
        var offset = 0
        text.split('\n').forEachIndexed { lineNumber, line ->
            tokens += tokenizeLine(line, lineNumber, offset, LexerState.Initial).first
            offset += line.length + 1
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
        var pos = skipLeadingWhitespace(text)

        // A '#' is only a comment marker at the start of a line.
        if (pos < text.length && text[pos] == '#') {
            tokens += Token(TokenType.COMMENT_LINE, startOffset + pos, text.length - pos, lineNumber)
            return tokens to LexerState.Initial
        }

        // A leading '!' negates the pattern, re-including what an earlier line excluded.
        if (pos < text.length && text[pos] == '!') {
            tokens += Token(TokenType.KEYWORD, startOffset + pos, 1, lineNumber)
            pos++
        }

        while (pos < text.length) {
            val consumed = appendPatternToken(text, pos, lineNumber, startOffset, tokens)
            pos += consumed
        }

        return tokens to LexerState.Initial
    }

    /**
     * Appends the token starting at [pos] to [tokens].
     *
     * @return the number of characters consumed, always at least one.
     */
    private fun appendPatternToken(
        text: String,
        pos: Int,
        line: Int,
        startOffset: Int,
        tokens: MutableList<Token>,
    ): Int {
        val length =
            when {
                text.startsWith("**", pos) -> 2
                text[pos] == '*' || text[pos] == '?' -> 1
                text[pos] == '/' -> 1
                text[pos] == '[' -> characterClassLength(text, pos)
                text[pos] == '\\' && pos + 1 < text.length -> 2
                else -> literalLength(text, pos)
            }
        val type =
            when {
                text[pos] == '*' || text[pos] == '?' -> TokenType.OPERATOR
                text[pos] == '/' -> TokenType.PUNCTUATION
                text[pos] == '[' && length > 1 -> TokenType.CHARACTER
                text[pos] == '\\' && length == 2 -> TokenType.STRING_ESCAPE
                else -> TokenType.IDENTIFIER
            }
        tokens += Token(type, startOffset + pos, length, line)
        return length
    }

    /** Length of the bracket expression at [pos], or 1 when it is unterminated. */
    private fun characterClassLength(
        text: String,
        pos: Int,
    ): Int {
        var index = pos + 1
        if (index < text.length && (text[index] == '!' || text[index] == '^')) index++
        // A ']' in the first position is a literal member of the class.
        if (index < text.length && text[index] == ']') index++
        while (index < text.length && text[index] != ']') {
            if (text[index] == '\\' && index + 1 < text.length) index++
            index++
        }
        return if (index < text.length) index + 1 - pos else 1
    }

    /** Length of the run of plain pattern characters starting at [pos]. */
    private fun literalLength(
        text: String,
        pos: Int,
    ): Int {
        var index = pos
        while (index < text.length && text[index] !in PATTERN_SYNTAX_CHARS) index++
        return (index - pos).coerceAtLeast(1)
    }

    private fun skipLeadingWhitespace(text: String): Int {
        var index = 0
        while (index < text.length && (text[index] == ' ' || text[index] == '\t' || text[index] == '\r')) index++
        return index
    }

    private companion object {
        /** Characters that start a token of their own inside a pattern. */
        const val PATTERN_SYNTAX_CHARS = "*?/[\\"
    }
}
