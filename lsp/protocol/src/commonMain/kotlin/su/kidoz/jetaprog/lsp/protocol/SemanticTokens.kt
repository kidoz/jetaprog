package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * Semantic token legend describing the token types and modifiers
 * supported by the server.
 */
@Serializable
public data class SemanticTokensLegend(
    val tokenTypes: List<String>,
    val tokenModifiers: List<String>,
)

/**
 * Semantic tokens provider options.
 */
@Serializable
public data class SemanticTokensOptions(
    val legend: SemanticTokensLegend,
    val full: Boolean? = null,
    val range: Boolean? = null,
)

/**
 * Parameters for semantic tokens full request.
 */
@Serializable
public data class SemanticTokensParams(
    val textDocument: TextDocumentIdentifier,
)

/**
 * Parameters for semantic tokens range request.
 */
@Serializable
public data class SemanticTokensRangeParams(
    val textDocument: TextDocumentIdentifier,
    val range: LspRange,
)

/**
 * Semantic tokens response.
 *
 * The data array is encoded as follows:
 * - Each token is represented by 5 integers: deltaLine, deltaStart, length, tokenType, tokenModifiers
 * - deltaLine: Line delta from previous token (or 0 for first token)
 * - deltaStart: Character delta from previous token on same line (or start char if new line)
 * - length: Length of the token
 * - tokenType: Index into SemanticTokensLegend.tokenTypes
 * - tokenModifiers: Bit flags into SemanticTokensLegend.tokenModifiers
 */
@Serializable
public data class SemanticTokens(
    val resultId: String? = null,
    val data: List<Int>,
)

/**
 * Standard LSP semantic token types.
 *
 * These are the predefined token types from the LSP specification.
 * Language servers may define additional types.
 */
public object SemanticTokenTypes {
    public const val NAMESPACE: String = "namespace"
    public const val TYPE: String = "type"
    public const val CLASS: String = "class"
    public const val ENUM: String = "enum"
    public const val INTERFACE: String = "interface"
    public const val STRUCT: String = "struct"
    public const val TYPE_PARAMETER: String = "typeParameter"
    public const val PARAMETER: String = "parameter"
    public const val VARIABLE: String = "variable"
    public const val PROPERTY: String = "property"
    public const val ENUM_MEMBER: String = "enumMember"
    public const val EVENT: String = "event"
    public const val FUNCTION: String = "function"
    public const val METHOD: String = "method"
    public const val MACRO: String = "macro"
    public const val KEYWORD: String = "keyword"
    public const val MODIFIER: String = "modifier"
    public const val COMMENT: String = "comment"
    public const val STRING: String = "string"
    public const val NUMBER: String = "number"
    public const val REGEXP: String = "regexp"
    public const val OPERATOR: String = "operator"
    public const val DECORATOR: String = "decorator"

    /**
     * All standard token types in order.
     * Used to build the legend for servers that follow the standard ordering.
     */
    public val ALL: List<String> =
        listOf(
            NAMESPACE,
            TYPE,
            CLASS,
            ENUM,
            INTERFACE,
            STRUCT,
            TYPE_PARAMETER,
            PARAMETER,
            VARIABLE,
            PROPERTY,
            ENUM_MEMBER,
            EVENT,
            FUNCTION,
            METHOD,
            MACRO,
            KEYWORD,
            MODIFIER,
            COMMENT,
            STRING,
            NUMBER,
            REGEXP,
            OPERATOR,
            DECORATOR,
        )
}

/**
 * Standard LSP semantic token modifiers.
 *
 * These are the predefined token modifiers from the LSP specification.
 * Modifiers are represented as bit flags.
 */
public object SemanticTokenModifiers {
    public const val DECLARATION: String = "declaration"
    public const val DEFINITION: String = "definition"
    public const val READONLY: String = "readonly"
    public const val STATIC: String = "static"
    public const val DEPRECATED: String = "deprecated"
    public const val ABSTRACT: String = "abstract"
    public const val ASYNC: String = "async"
    public const val MODIFICATION: String = "modification"
    public const val DOCUMENTATION: String = "documentation"
    public const val DEFAULT_LIBRARY: String = "defaultLibrary"

    /**
     * All standard modifiers in order.
     * The index determines the bit position in the modifier flags.
     */
    public val ALL: List<String> =
        listOf(
            DECLARATION,
            DEFINITION,
            READONLY,
            STATIC,
            DEPRECATED,
            ABSTRACT,
            ASYNC,
            MODIFICATION,
            DOCUMENTATION,
            DEFAULT_LIBRARY,
        )

    /**
     * Decode modifier flags into a set of modifier names.
     */
    public fun decode(
        flags: Int,
        legend: List<String>,
    ): Set<String> {
        val result = mutableSetOf<String>()
        for (i in legend.indices) {
            if ((flags and (1 shl i)) != 0) {
                result.add(legend[i])
            }
        }
        return result
    }
}
