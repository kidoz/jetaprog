package su.kidoz.jetaprog.editor.syntax

/**
 * Types of tokens that can be identified by a lexer.
 */
public enum class TokenType {
    /** Language keywords (fun, val, var, class, if, when, etc.) */
    KEYWORD,

    /** Modifiers (public, private, suspend, inline, etc.) */
    MODIFIER,

    /** String literals ("...", """...""") */
    STRING,

    /** Escape sequences in strings (\n, \t, etc.) */
    STRING_ESCAPE,

    /** String template expressions (${...}, $variable) */
    STRING_TEMPLATE,

    /** Numeric literals (123, 0xFF, 3.14, 1e10) */
    NUMBER,

    /** Character literals ('a', '\n') */
    CHARACTER,

    /** Single-line comments (//) */
    COMMENT_LINE,

    /** Block comments. */
    COMMENT_BLOCK,

    /** Documentation comments. */
    COMMENT_DOC,

    /** Generic identifiers */
    IDENTIFIER,

    /** Function calls and declarations */
    FUNCTION,

    /** Type names (class names, interface names) */
    TYPE,

    /** Property access */
    PROPERTY,

    /** Parameter names */
    PARAMETER,

    /** Annotations (@Something) */
    ANNOTATION,

    /** Operators (+, -, *, /, =, ==, etc.) */
    OPERATOR,

    /** Punctuation (., ,, ;, :) */
    PUNCTUATION,

    /** Brackets and braces. */
    BRACKET,

    /** Constant values (true, false, null, etc.) */
    CONSTANT,

    /** XML/HTML tags */
    TAG,

    /** Plain text content (e.g., between XML tags) */
    TEXT,

    /** Whitespace (spaces, tabs, newlines) */
    WHITESPACE,

    /** Newline characters */
    NEWLINE,

    /** Unknown or unrecognized tokens */
    UNKNOWN,
}
