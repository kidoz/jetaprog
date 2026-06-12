package su.kidoz.jetaprog.editor.treesitter

/**
 * Tree-sitter parser interface.
 *
 * Provides parsing capabilities for a specific language grammar.
 * Implementations wrap the native Tree-sitter parser.
 */
public interface TreeSitterParser {
    /**
     * The language this parser handles.
     */
    public val languageId: String

    /**
     * Parse source text into a syntax tree.
     *
     * @param text The source code to parse
     * @return The parsed syntax tree
     */
    public fun parse(text: String): TreeSitterTree

    /**
     * Parse source text incrementally, reusing parts of the old tree.
     *
     * @param text The new source code
     * @param oldTree The previous tree (will be edited in place)
     * @return The new parsed syntax tree
     */
    public fun parseIncremental(
        text: String,
        oldTree: TreeSitterTree?,
    ): TreeSitterTree

    /**
     * Release resources associated with this parser.
     */
    public fun close()
}

/**
 * Factory for creating Tree-sitter parsers.
 */
public interface TreeSitterParserFactory {
    /**
     * Create a parser for the given language.
     *
     * @param languageId The language identifier (e.g., "kotlin", "python")
     * @return A parser for the language, or null if not available
     */
    public fun createParser(languageId: String): TreeSitterParser?

    /**
     * Get the list of supported languages.
     */
    public fun supportedLanguages(): Set<String>
}
