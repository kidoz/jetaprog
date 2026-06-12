package su.kidoz.jetaprog.editor.syntax.highlighting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.editor.syntax.Lexer
import su.kidoz.jetaprog.editor.syntax.TokenList

/**
 * Layered syntax highlighter that combines multiple token sources.
 *
 * Implements a 3-layer highlighting architecture:
 * 1. **Layer 1 (Fallback)**: Hand-written lexers - instant, always works
 * 2. **Layer 2 (Structural)**: Tree-sitter parsers - fast, AST-aware (optional)
 * 3. **Layer 3 (Semantic)**: LSP semantic tokens - most accurate, async
 *
 * The layers are combined with priority: LSP > Tree-sitter > Lexer
 */
public class LayeredHighlighter(
    private val scope: CoroutineScope,
) {
    private val _tokens = MutableStateFlow(TokenList(emptyList()))

    /**
     * The current merged tokens from all active layers.
     */
    public val tokens: StateFlow<TokenList> = _tokens.asStateFlow()

    private val _semanticTokens = MutableStateFlow<List<SemanticToken>>(emptyList())

    /**
     * Semantic tokens from LSP (for debugging/inspection).
     */
    public val semanticTokens: StateFlow<List<SemanticToken>> = _semanticTokens.asStateFlow()

    private var semanticJob: Job? = null
    private var currentLexer: Lexer? = null

    /**
     * Highlight content using the provided lexer.
     *
     * This performs synchronous lexer-based highlighting immediately,
     * then optionally fetches semantic tokens asynchronously.
     *
     * @param content The source code content
     * @param lexer The lexer to use for base highlighting
     * @param semanticTokenProvider Optional provider for LSP semantic tokens
     */
    public fun highlight(
        content: String,
        lexer: Lexer?,
        semanticTokenProvider: SemanticTokenProvider? = null,
    ) {
        currentLexer = lexer

        // Layer 1: Immediate lexer-based highlighting
        val baseTokens = lexer?.tokenize(content) ?: TokenList(emptyList())
        _tokens.value = baseTokens

        // Layer 3: Async semantic tokens
        if (semanticTokenProvider != null) {
            semanticJob?.cancel()
            semanticJob =
                scope.launch {
                    requestSemanticTokens(semanticTokenProvider)
                }
        }
    }

    /**
     * Update highlighting with new content.
     * Uses the previously set lexer.
     */
    public fun update(content: String) {
        val lexer = currentLexer ?: return
        val baseTokens = lexer.tokenize(content)
        _tokens.value = mergeWithSemanticTokens(baseTokens)
    }

    /**
     * Apply semantic tokens received from LSP.
     *
     * @param data The encoded semantic token data
     * @param tokenTypes The token type legend from the server
     */
    public fun applySemanticTokens(
        data: List<Int>,
        tokenTypes: List<String>,
    ) {
        val decoded = SemanticTokenConverter.decodeSimple(data, tokenTypes)
        _semanticTokens.value = decoded

        // Merge with current base tokens
        _tokens.value = mergeWithSemanticTokens(_tokens.value)
    }

    /**
     * Clear semantic tokens (e.g., when LSP server disconnects).
     */
    public fun clearSemanticTokens() {
        semanticJob?.cancel()
        _semanticTokens.value = emptyList()
    }

    private suspend fun requestSemanticTokens(provider: SemanticTokenProvider) {
        val result = provider.getSemanticTokens()
        if (result != null) {
            val decoded = SemanticTokenConverter.decodeSimple(result.data, result.tokenTypes)
            _semanticTokens.value = decoded
            _tokens.value = mergeWithSemanticTokens(_tokens.value)
        }
    }

    private fun mergeWithSemanticTokens(base: TokenList): TokenList {
        val semantic = _semanticTokens.value
        return if (semantic.isEmpty()) {
            base
        } else {
            TokenMerger.merge(base, semantic)
        }
    }

    /**
     * Cancel any pending async operations.
     */
    public fun cancel() {
        semanticJob?.cancel()
    }
}

/**
 * Provider for LSP semantic tokens.
 */
public interface SemanticTokenProvider {
    /**
     * Get semantic tokens for the current document.
     * Returns null if semantic tokens are not available.
     */
    public suspend fun getSemanticTokens(): SemanticTokenResult?
}

/**
 * Result of a semantic tokens request.
 */
public data class SemanticTokenResult(
    val data: List<Int>,
    val tokenTypes: List<String>,
    val tokenModifiers: List<String> = emptyList(),
)
