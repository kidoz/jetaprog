package su.kidoz.jetaprog.plugins.kotlin

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange

/**
 * A symbol in Kotlin source code.
 */
public data class KotlinSymbol(
    /** The symbol name. */
    val name: String,
    /** The fully qualified name. */
    val fqName: String,
    /** The kind of symbol. */
    val kind: SymbolKind,
    /** The file containing this symbol. */
    val filePath: String,
    /** The text range of the symbol declaration. */
    val range: TextRange,
    /** The name range (just the identifier). */
    val nameRange: TextRange,
    /** Parent symbol name (e.g., class name for a method). */
    val parent: String? = null,
    /** Signature or type information. */
    val signature: String? = null,
    /** Documentation comment. */
    val documentation: String? = null,
    /** Visibility modifier. */
    val visibility: Visibility = Visibility.PUBLIC,
    /** Whether this is an extension. */
    val isExtension: Boolean = false,
)

/**
 * Kind of symbol.
 */
public enum class SymbolKind {
    CLASS,
    INTERFACE,
    OBJECT,
    ENUM,
    ENUM_ENTRY,
    ANNOTATION,
    FUNCTION,
    PROPERTY,
    PARAMETER,
    TYPE_PARAMETER,
    CONSTRUCTOR,
    COMPANION_OBJECT,
    FILE,
}

/**
 * Visibility modifier.
 */
public enum class Visibility {
    PUBLIC,
    PRIVATE,
    PROTECTED,
    INTERNAL,
}

/**
 * Location of a symbol.
 */
public data class SymbolLocation(
    val filePath: String,
    val position: TextPosition,
    val range: TextRange = TextRange(position, position),
)
