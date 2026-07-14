package su.kidoz.jetaprog.editor.syntax

/**
 * State for incremental lexing across lines.
 * Used to handle multi-line constructs like block comments and multiline strings.
 */
public data class LexerState(
    /** Whether we're inside a multiline string (""" ... """). */
    val inMultilineString: Boolean = false,
    /** Whether we're inside a block comment. */
    val inBlockComment: Boolean = false,
    /** Nesting depth of block comments (for nested /* */ support). */
    val blockCommentDepth: Int = 0,
    /** String delimiter for raw strings (C++ R"delimiter(...)delimiter", Rust r#"..."#). */
    val stringDelimiter: String = "",
    /** Whether the current block comment is a documentation comment. */
    val inDocComment: Boolean = false,
) {
    public companion object {
        /** Initial lexer state. */
        public val Initial: LexerState = LexerState()
    }
}

/**
 * Interface for tokenizing source code.
 */
public interface Lexer {
    /**
     * The language ID this lexer handles.
     */
    public val languageId: String

    /**
     * Tokenizes the entire text.
     *
     * @param text The source code to tokenize
     * @return List of tokens in order of appearance
     */
    public fun tokenize(text: String): TokenList

    /**
     * Tokenizes a single line of text.
     * Used for incremental tokenization when editing.
     *
     * @param text The line text to tokenize
     * @param lineNumber The 0-based line number
     * @param startOffset The offset in the document where this line starts
     * @param state The lexer state from the previous line
     * @return Pair of tokens on this line and the state for the next line
     */
    public fun tokenizeLine(
        text: String,
        lineNumber: Int,
        startOffset: Int,
        state: LexerState,
    ): Pair<List<Token>, LexerState>
}

/**
 * Registry for language lexers.
 */
public object LexerRegistry {
    private val lexers = mutableMapOf<String, Lexer>()

    /**
     * Registers a lexer for a language.
     */
    public fun register(lexer: Lexer) {
        lexers[lexer.languageId] = lexer
    }

    /**
     * Gets the lexer for a language.
     */
    public fun get(languageId: String): Lexer? = lexers[languageId]

    /**
     * Returns all registered language IDs.
     */
    public fun languages(): Set<String> = lexers.keys
}
