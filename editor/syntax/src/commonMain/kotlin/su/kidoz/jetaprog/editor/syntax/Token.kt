package su.kidoz.jetaprog.editor.syntax

/**
 * Represents a token identified by a lexer.
 *
 * @property type The type of the token
 * @property start The starting offset in the text (0-based)
 * @property length The length of the token in characters
 * @property line The line number where the token starts (0-based)
 */
public data class Token(
    val type: TokenType,
    val start: Int,
    val length: Int,
    val line: Int = 0,
) {
    /**
     * The ending offset of the token (exclusive).
     */
    val end: Int get() = start + length

    /**
     * Returns true if this token contains the given offset.
     */
    public fun contains(offset: Int): Boolean = offset in start until end
}

/**
 * A list of tokens for efficient lookup and manipulation.
 */
public class TokenList(
    private val tokens: List<Token>,
) : List<Token> by tokens {
    /**
     * Finds the token at the given offset.
     * Returns null if no token contains the offset.
     */
    public fun tokenAt(offset: Int): Token? = tokens.find { it.contains(offset) }

    /**
     * Returns all tokens on the given line.
     */
    public fun tokensOnLine(line: Int): List<Token> = tokens.filter { it.line == line }

    /**
     * Returns tokens in the given range.
     */
    public fun tokensInRange(
        startOffset: Int,
        endOffset: Int,
    ): List<Token> = tokens.filter { it.start < endOffset && it.end > startOffset }
}
