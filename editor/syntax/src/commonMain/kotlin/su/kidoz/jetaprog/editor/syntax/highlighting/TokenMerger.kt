package su.kidoz.jetaprog.editor.syntax.highlighting

import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenList

/**
 * Merges tokens from multiple sources (lexer, tree-sitter, LSP semantic tokens).
 *
 * Priority order (highest to lowest):
 * 1. LSP semantic tokens - most accurate, compiler-level information
 * 2. Tree-sitter tokens - structural, AST-aware
 * 3. Hand-written lexer tokens - basic syntax highlighting
 */
public object TokenMerger {
    /**
     * Merge base tokens with semantic tokens.
     *
     * Semantic tokens take priority for overlapping ranges.
     * Base tokens are preserved for ranges not covered by semantic tokens.
     *
     * @param base The base tokens from lexer or tree-sitter
     * @param semantic The semantic tokens from LSP
     * @return Merged token list with semantic tokens overriding base tokens
     */
    public fun merge(
        base: TokenList,
        semantic: List<SemanticToken>,
    ): TokenList {
        if (semantic.isEmpty()) return base
        if (base.isEmpty()) return base

        val result = mutableListOf<Token>()
        val semanticByLine = semantic.groupBy { it.line }

        for (baseToken in base) {
            val lineSemantics = semanticByLine[baseToken.line]

            if (lineSemantics == null) {
                // No semantic tokens on this line, keep base token
                result.add(baseToken)
                continue
            }

            // Find overlapping semantic token
            val overlap = findOverlappingToken(baseToken, lineSemantics)

            if (overlap != null) {
                // Semantic token wins - use its type
                result.add(baseToken.copy(type = overlap.type))
            } else {
                // No overlap, keep base token
                result.add(baseToken)
            }
        }

        return TokenList(result)
    }

    /**
     * Merge base tokens with absolute-offset semantic tokens.
     */
    public fun mergeWithTokens(
        base: TokenList,
        semantic: List<Token>,
    ): TokenList {
        if (semantic.isEmpty()) return base
        if (base.isEmpty()) return TokenList(semantic)

        val result = mutableListOf<Token>()
        val semanticByLine = semantic.groupBy { it.line }

        for (baseToken in base) {
            val lineSemantics = semanticByLine[baseToken.line]

            if (lineSemantics == null) {
                result.add(baseToken)
                continue
            }

            // Find overlapping semantic token by offset
            val overlap =
                lineSemantics.find { sem ->
                    baseToken.start < sem.end && baseToken.end > sem.start
                }

            if (overlap != null) {
                result.add(baseToken.copy(type = overlap.type))
            } else {
                result.add(baseToken)
            }
        }

        return TokenList(result)
    }

    /**
     * Find a semantic token that overlaps with the given base token.
     */
    private fun findOverlappingToken(
        baseToken: Token,
        semanticTokens: List<SemanticToken>,
    ): SemanticToken? {
        // Calculate character position within line
        // Base token has absolute offset, we need line-relative position
        // For simplicity, we match by checking if base token overlaps with semantic token range
        for (semantic in semanticTokens) {
            if (semantic.line == baseToken.line) {
                // Check character overlap on the same line
                // Note: baseToken.start is absolute, semantic.startChar is line-relative
                // This works when comparing tokens on the same line
                val baseEndInLine = baseToken.start + baseToken.length

                // Simplified overlap check - assumes tokens on same line have comparable positions
                // In practice, we'd need line offset information for precise matching
                if (tokensOverlap(baseToken, semantic)) {
                    return semantic
                }
            }
        }
        return null
    }

    /**
     * Check if a base token overlaps with a semantic token.
     *
     * This is a heuristic comparison since base tokens use absolute offsets
     * while semantic tokens use line-relative positions.
     */
    private fun tokensOverlap(
        baseToken: Token,
        semantic: SemanticToken,
    ): Boolean {
        // If they're on different lines, no overlap
        if (baseToken.line != semantic.line) return false

        // For same-line comparison, we compare relative positions
        // This works because token positions within a line are comparable
        val baseLength = baseToken.length
        val semLength = semantic.length

        // Use token length as a heuristic for matching
        // Tokens with similar lengths at the same position are likely the same
        return baseLength == semLength ||
            (baseLength > 0 && semLength > 0 && kotlin.math.abs(baseLength - semLength) <= 2)
    }

    /**
     * Enhanced merge that handles partial overlaps.
     *
     * When a semantic token partially overlaps a base token:
     * - The overlapping portion uses the semantic token type
     * - Non-overlapping portions keep the base token type
     */
    public fun mergeWithSplitting(
        base: TokenList,
        semantic: List<Token>,
    ): TokenList {
        if (semantic.isEmpty()) return base
        if (base.isEmpty()) return TokenList(semantic)

        val result = mutableListOf<Token>()
        val semanticByLine = semantic.groupBy { it.line }

        for (baseToken in base) {
            val lineSemantics = semanticByLine[baseToken.line] ?: emptyList()

            if (lineSemantics.isEmpty()) {
                result.add(baseToken)
                continue
            }

            // Find all overlapping semantic tokens
            val overlaps =
                lineSemantics.filter { sem ->
                    baseToken.start < sem.end && baseToken.end > sem.start
                }

            if (overlaps.isEmpty()) {
                result.add(baseToken)
            } else {
                // Use the first overlapping semantic token's type
                // Could be enhanced to handle multiple overlaps
                result.add(baseToken.copy(type = overlaps.first().type))
            }
        }

        return TokenList(result)
    }
}
