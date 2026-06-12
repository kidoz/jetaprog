package su.kidoz.jetaprog.editor.syntax.highlighting

import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Converts LSP semantic tokens to editor Token objects.
 *
 * LSP semantic tokens are encoded as a flat array of integers:
 * [deltaLine, deltaChar, length, tokenType, modifiers] × N
 */
public object SemanticTokenConverter {
    /**
     * Decode LSP semantic tokens from delta-encoded format.
     *
     * @param data The encoded token data from LSP response
     * @param tokenTypes The token type legend from the server
     * @param lineOffsets Offset of each line in the document (for computing absolute offsets)
     * @return List of Token objects
     */
    public fun decode(
        data: List<Int>,
        tokenTypes: List<String>,
        lineOffsets: List<Int>,
    ): List<Token> {
        if (data.isEmpty()) return emptyList()

        val tokens = mutableListOf<Token>()

        var line = 0
        var char = 0

        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaChar = data[i + 1]
            val length = data[i + 2]
            val tokenTypeIndex = data[i + 3]
            // val modifierBits = data[i + 4] // Modifiers available for future use

            // Update position
            if (deltaLine > 0) {
                line += deltaLine
                char = deltaChar
            } else {
                char += deltaChar
            }

            // Map LSP token type to our TokenType
            val lspType = tokenTypes.getOrNull(tokenTypeIndex)
            val tokenType = mapSemanticType(lspType)

            if (tokenType != null && line < lineOffsets.size) {
                val absoluteOffset = lineOffsets[line] + char
                tokens.add(
                    Token(
                        type = tokenType,
                        start = absoluteOffset,
                        length = length,
                        line = line,
                    ),
                )
            }

            i += 5
        }

        return tokens
    }

    /**
     * Simplified decode without line offsets.
     * Uses character position within line instead of absolute offset.
     */
    public fun decodeSimple(
        data: List<Int>,
        tokenTypes: List<String>,
    ): List<SemanticToken> {
        if (data.isEmpty()) return emptyList()

        val tokens = mutableListOf<SemanticToken>()

        var line = 0
        var char = 0

        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaChar = data[i + 1]
            val length = data[i + 2]
            val tokenTypeIndex = data[i + 3]
            val modifierBits = data[i + 4]

            // Update position
            if (deltaLine > 0) {
                line += deltaLine
                char = deltaChar
            } else {
                char += deltaChar
            }

            val lspType = tokenTypes.getOrNull(tokenTypeIndex)
            val tokenType = mapSemanticType(lspType)

            if (tokenType != null) {
                tokens.add(
                    SemanticToken(
                        line = line,
                        startChar = char,
                        length = length,
                        type = tokenType,
                        modifiers = modifierBits,
                    ),
                )
            }

            i += 5
        }

        return tokens
    }

    /**
     * Map LSP semantic token type to our TokenType.
     */
    public fun mapSemanticType(lspType: String?): TokenType? =
        when (lspType) {
            // Keywords and modifiers
            "keyword" -> TokenType.KEYWORD

            "modifier" -> TokenType.MODIFIER

            // Types
            "type", "class", "interface", "enum", "struct", "typeParameter" -> TokenType.TYPE

            "namespace" -> TokenType.TYPE

            // Functions
            "function", "method", "macro" -> TokenType.FUNCTION

            // Variables and properties
            "variable" -> TokenType.IDENTIFIER

            "property" -> TokenType.PROPERTY

            "parameter" -> TokenType.PARAMETER

            "enumMember" -> TokenType.CONSTANT

            // Literals
            "string" -> TokenType.STRING

            "number" -> TokenType.NUMBER

            "regexp" -> TokenType.STRING

            // Comments
            "comment" -> TokenType.COMMENT_LINE

            // Operators and decorators
            "operator" -> TokenType.OPERATOR

            "decorator" -> TokenType.ANNOTATION

            // Event (treat as property)
            "event" -> TokenType.PROPERTY

            else -> null
        }
}

/**
 * Semantic token with line-relative positioning.
 */
public data class SemanticToken(
    val line: Int,
    val startChar: Int,
    val length: Int,
    val type: TokenType,
    val modifiers: Int = 0,
) {
    val endChar: Int get() = startChar + length

    /**
     * Check if this token overlaps with another on the same line.
     */
    public fun overlaps(other: SemanticToken): Boolean {
        if (line != other.line) return false
        return startChar < other.endChar && endChar > other.startChar
    }
}
