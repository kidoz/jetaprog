package su.kidoz.jetaprog.editor.treesitter

import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.LexerState
import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList

/**
 * Lexer implementation that uses Tree-sitter for tokenization.
 *
 * This adapter wraps a Tree-sitter parser to provide syntax highlighting
 * tokens that are compatible with the existing lexer infrastructure.
 *
 * Tree-sitter provides several advantages over hand-written lexers:
 * - Accurate parsing based on a full grammar
 * - Incremental parsing for efficient updates
 * - Error recovery for partial/invalid code
 * - Consistent behavior across all supported languages
 */
public class TreeSitterLexer(
    override val languageId: String,
    private val parser: TreeSitterParser,
    private val mapper: NodeToTokenMapper = NodeToTokenMapper(),
) : Lexer {
    private var cachedTree: TreeSitterTree? = null
    private var cachedText: String? = null

    /**
     * Tokenize the entire text using Tree-sitter.
     */
    override fun tokenize(text: String): TokenList {
        // Reuse cached tree if text hasn't changed
        if (text == cachedText && cachedTree != null) {
            return extractTokens(cachedTree!!.rootNode)
        }

        // Parse (incrementally if possible)
        val tree =
            if (cachedTree != null && cachedText != null) {
                parser.parseIncremental(text, cachedTree)
            } else {
                parser.parse(text)
            }

        cachedTree = tree
        cachedText = text

        return extractTokens(tree.rootNode)
    }

    /**
     * Tokenize a single line.
     *
     * For Tree-sitter, this still uses the full tree but filters to the requested line.
     * The state is used to track whether we have a cached tree.
     */
    override fun tokenizeLine(
        text: String,
        lineNumber: Int,
        startOffset: Int,
        state: LexerState,
    ): Pair<List<Token>, LexerState> {
        // Tree-sitter handles state internally via the syntax tree
        // We just extract tokens for the specific line
        val tree = cachedTree ?: return emptyList<Token>() to state

        val lineTokens = extractLineTokens(tree.rootNode, lineNumber, startOffset)

        // Tree-sitter doesn't use LexerState the same way
        // Return the same state since the tree maintains all context
        return lineTokens to state
    }

    /**
     * Extract all tokens from a syntax tree by traversing nodes.
     */
    private fun extractTokens(rootNode: TreeSitterNode): TokenList {
        val tokens = mutableListOf<Token>()
        traverseNode(rootNode, tokens)
        return TokenList(tokens.sortedBy { it.start })
    }

    /**
     * Recursively traverse the syntax tree and collect tokens.
     */
    private fun traverseNode(
        node: TreeSitterNode,
        tokens: MutableList<Token>,
    ) {
        // Try to map this node to a token
        val token = mapper.mapNode(node)
        if (token != null) {
            tokens.add(token)
        }

        // Process children if appropriate
        if (mapper.shouldProcessChildren(node)) {
            for (i in 0 until node.childCount) {
                val child = node.child(i)
                if (child != null) {
                    traverseNode(child, tokens)
                }
            }
        }
    }

    /**
     * Extract tokens for a specific line.
     */
    private fun extractLineTokens(
        rootNode: TreeSitterNode,
        lineNumber: Int,
        startOffset: Int,
    ): List<Token> {
        val allTokens = mutableListOf<Token>()
        traverseNode(rootNode, allTokens)

        return allTokens
            .filter { it.line == lineNumber }
            .map { token ->
                // Adjust offset to be relative to line start
                token.copy(start = token.start - startOffset)
            }.sortedBy { it.start }
    }

    /**
     * Invalidate the cached tree, forcing a full reparse on next tokenize call.
     */
    public fun invalidateCache() {
        cachedTree?.close()
        cachedTree = null
        cachedText = null
    }

    /**
     * Apply an edit to the cached tree for incremental parsing.
     */
    public fun applyEdit(edit: TreeSitterEdit) {
        cachedTree?.edit(edit)
    }
}

/**
 * Registry for Tree-sitter lexers.
 *
 * Provides Tree-sitter lexers for languages that have grammar support,
 * falling back to hand-written lexers otherwise.
 */
public class TreeSitterLexerRegistry(
    private val parserFactory: TreeSitterParserFactory,
    private val mappers: Map<String, NodeToTokenMapper> = defaultMappers(),
) {
    private val lexers = mutableMapOf<String, TreeSitterLexer>()

    /**
     * Get a Tree-sitter lexer for the given language.
     *
     * @param languageId The language identifier
     * @return A TreeSitterLexer, or null if Tree-sitter doesn't support this language
     */
    public fun getLexer(languageId: String): TreeSitterLexer? {
        // Return cached lexer if available
        lexers[languageId]?.let { return it }

        // Try to create a new lexer
        val parser = parserFactory.createParser(languageId) ?: return null
        val mapper = mappers[languageId] ?: NodeToTokenMapper()

        val lexer = TreeSitterLexer(languageId, parser, mapper)
        lexers[languageId] = lexer

        return lexer
    }

    /**
     * Check if Tree-sitter supports the given language.
     */
    public fun isSupported(languageId: String): Boolean = parserFactory.createParser(languageId) != null

    /**
     * Get all languages supported by Tree-sitter.
     */
    public fun supportedLanguages(): Set<String> = parserFactory.supportedLanguages()

    public companion object {
        /**
         * Default language-specific mappers.
         */
        public fun defaultMappers(): Map<String, NodeToTokenMapper> =
            mapOf(
                "kotlin" to KotlinNodeToTokenMapper(),
                "python" to PythonNodeToTokenMapper(),
                "rust" to RustNodeToTokenMapper(),
            )
    }
}
