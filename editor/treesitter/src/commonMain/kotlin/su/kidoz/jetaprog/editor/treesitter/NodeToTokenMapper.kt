package su.kidoz.jetaprog.editor.treesitter

import su.kidoz.jetaprog.editor.syntax.Token
import su.kidoz.jetaprog.editor.syntax.TokenType

/**
 * Maps Tree-sitter syntax nodes to editor tokens.
 *
 * Each language may have its own mapper with language-specific node type mappings.
 */
public open class NodeToTokenMapper {
    /**
     * Map a Tree-sitter node to an editor Token.
     *
     * @param node The syntax tree node
     * @return A Token, or null if this node should not be highlighted
     */
    public open fun mapNode(node: TreeSitterNode): Token? {
        val tokenType = mapNodeType(node.type) ?: return null
        return Token(
            type = tokenType,
            start = node.startByte,
            length = node.endByte - node.startByte,
            line = node.startPoint.row,
        )
    }

    /**
     * Map a Tree-sitter node type string to a TokenType.
     *
     * Override this in language-specific mappers for accurate highlighting.
     *
     * @param nodeType The Tree-sitter node type (e.g., "function_declaration")
     * @return The corresponding TokenType, or null to skip this node
     */
    public open fun mapNodeType(nodeType: String): TokenType? =
        when (nodeType) {
            // Keywords (common across languages)
            "keyword",
            "if", "else", "elif", "endif",
            "for", "while", "do", "loop",
            "return", "break", "continue", "yield",
            "try", "catch", "finally", "throw", "raise",
            "import", "export", "from", "as",
            "class", "struct", "enum", "interface", "trait",
            "fun", "func", "function", "def", "fn",
            "val", "var", "let", "const", "mut",
            "if_statement", "for_statement", "while_statement",
            "return_statement", "break_statement", "continue_statement",
            -> TokenType.KEYWORD

            // Modifiers
            "visibility_modifier",
            "public", "private", "protected", "internal",
            "static", "final", "abstract", "sealed", "open",
            "async", "await", "suspend", "inline", "override",
            -> TokenType.MODIFIER

            // Types
            "type_identifier", "type",
            "class_declaration", "interface_declaration",
            "struct_declaration", "enum_declaration",
            "type_alias", "type_annotation",
            "primitive_type", "builtin_type",
            -> TokenType.TYPE

            // Functions
            "function_declaration", "function_definition",
            "method_declaration", "method_definition",
            "call_expression", "function_call",
            "lambda_expression", "arrow_function",
            -> TokenType.FUNCTION

            // Strings
            "string_literal", "string", "raw_string",
            "template_string", "string_content",
            "interpreted_string_literal",
            -> TokenType.STRING

            // String escapes
            "escape_sequence", "escape_character",
            -> TokenType.STRING_ESCAPE

            // String templates
            "string_template", "template_substitution",
            "interpolation", "string_interpolation",
            -> TokenType.STRING_TEMPLATE

            // Numbers
            "number_literal", "number",
            "integer_literal", "integer",
            "float_literal", "float", "real",
            "hex_literal", "binary_literal", "octal_literal",
            -> TokenType.NUMBER

            // Characters
            "character_literal", "char_literal", "rune_literal",
            -> TokenType.CHARACTER

            // Comments
            "line_comment", "comment",
            -> TokenType.COMMENT_LINE

            "block_comment", "multiline_comment",
            -> TokenType.COMMENT_BLOCK

            "doc_comment", "documentation_comment",
            -> TokenType.COMMENT_DOC

            // Identifiers
            "identifier", "simple_identifier", "name",
            -> TokenType.IDENTIFIER

            // Properties
            "property_identifier", "field_identifier",
            "attribute", "property", "field",
            -> TokenType.PROPERTY

            // Parameters
            "parameter", "formal_parameter",
            "parameter_declaration",
            -> TokenType.PARAMETER

            // Annotations
            "annotation", "decorator", "attribute",
            -> TokenType.ANNOTATION

            // Operators
            "operator", "binary_operator", "unary_operator",
            "comparison_operator", "arithmetic_operator",
            "assignment_operator", "compound_assignment",
            -> TokenType.OPERATOR

            // Punctuation
            "punctuation", "delimiter",
            ".", ",", ";", ":", "::", "->", "=>",
            -> TokenType.PUNCTUATION

            // Brackets
            "(", ")", "[", "]", "{", "}", "<", ">",
            "left_paren", "right_paren",
            "left_bracket", "right_bracket",
            "left_brace", "right_brace",
            -> TokenType.BRACKET

            // Constants
            "true", "false", "null", "nil", "none",
            "boolean_literal", "null_literal",
            -> TokenType.CONSTANT

            // Whitespace (usually not highlighted, but tracked)
            "whitespace", "newline",
            -> null

            // Skip whitespace tokens

            else -> null // Unknown node types are skipped
        }

    /**
     * Check if a node should have its children processed.
     *
     * Some container nodes (like function_body) should have their
     * children highlighted individually rather than as a single token.
     */
    public open fun shouldProcessChildren(node: TreeSitterNode): Boolean =
        when (node.type) {
            // Container nodes - process children
            "program", "source_file", "module",
            "class_body", "function_body", "block",
            "statement_list", "expression_list",
            "argument_list", "parameter_list",
            -> true

            // Leaf nodes - don't process children
            else -> !node.isNamed || node.childCount == 0
        }
}

/**
 * Kotlin-specific node to token mapper.
 */
public class KotlinNodeToTokenMapper : NodeToTokenMapper() {
    override fun mapNodeType(nodeType: String): TokenType? =
        when (nodeType) {
            // Kotlin-specific keywords
            "fun", "val", "var", "class", "interface", "object",
            "when", "is", "in", "!in", "as", "as?",
            "companion", "data", "sealed", "inner", "expect", "actual",
            "suspend", "inline", "crossinline", "noinline", "reified",
            "typealias", "by", "init", "constructor",
            -> TokenType.KEYWORD

            // Kotlin modifiers
            "visibility_modifier", "inheritance_modifier",
            "parameter_modifier", "platform_modifier", "function_modifier",
            "property_modifier", "class_modifier", "member_modifier",
            "annotation_modifier",
            -> TokenType.MODIFIER

            // Kotlin string templates
            "string_template", "interpolated_expression",
            -> TokenType.STRING_TEMPLATE

            // Delegate to default mapping for everything else
            else -> super.mapNodeType(nodeType)
        }
}

/**
 * Python-specific node to token mapper.
 */
public class PythonNodeToTokenMapper : NodeToTokenMapper() {
    override fun mapNodeType(nodeType: String): TokenType? =
        when (nodeType) {
            // Python-specific keywords
            "def", "class", "async", "await",
            "with", "as", "lambda", "global", "nonlocal",
            "pass", "assert", "del", "exec",
            -> TokenType.KEYWORD

            // Python decorators
            "decorator", "decorated_definition",
            -> TokenType.ANNOTATION

            // Python f-strings
            "f_string", "format_spec",
            -> TokenType.STRING_TEMPLATE

            else -> super.mapNodeType(nodeType)
        }
}

/**
 * Rust-specific node to token mapper.
 */
public class RustNodeToTokenMapper : NodeToTokenMapper() {
    override fun mapNodeType(nodeType: String): TokenType? =
        when (nodeType) {
            // Rust-specific keywords
            "fn", "let", "mut", "pub", "mod", "use",
            "impl", "trait", "where", "self", "Self",
            "move", "ref", "dyn", "unsafe", "extern",
            "crate", "super", "as", "in",
            -> TokenType.KEYWORD

            // Rust lifetime
            "lifetime", "label",
            -> TokenType.ANNOTATION

            // Rust macros
            "macro_invocation", "macro_definition",
            -> TokenType.FUNCTION

            else -> super.mapNodeType(nodeType)
        }
}
