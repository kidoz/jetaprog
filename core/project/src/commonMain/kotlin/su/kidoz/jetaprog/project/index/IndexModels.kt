package su.kidoz.jetaprog.project.index

import kotlinx.serialization.Serializable

/**
 * A symbol in the code index.
 * Represents classes, functions, variables, etc.
 */
@Serializable
public data class IndexedSymbol(
    /**
     * Unique symbol ID.
     */
    val id: Long = 0,
    /**
     * Symbol name.
     */
    val name: String,
    /**
     * Fully qualified name.
     */
    val qualifiedName: String,
    /**
     * Symbol kind.
     */
    val kind: SymbolKind,
    /**
     * File path relative to project root.
     */
    val filePath: String,
    /**
     * Start line (0-based).
     */
    val startLine: Int,
    /**
     * Start column (0-based).
     */
    val startColumn: Int,
    /**
     * End line (0-based).
     */
    val endLine: Int,
    /**
     * End column (0-based).
     */
    val endColumn: Int,
    /**
     * Parent symbol ID (for nested symbols).
     */
    val parentId: Long? = null,
    /**
     * Container name (class/namespace containing this symbol).
     */
    val containerName: String? = null,
    /**
     * Symbol signature (for functions).
     */
    val signature: String? = null,
    /**
     * Return type (for functions/properties).
     */
    val returnType: String? = null,
    /**
     * Documentation comment.
     */
    val documentation: String? = null,
    /**
     * Visibility modifier.
     */
    val visibility: Visibility = Visibility.PUBLIC,
    /**
     * Additional modifiers.
     */
    val modifiers: Set<Modifier> = emptySet(),
    /**
     * Language ID.
     */
    val languageId: String = "unknown",
)

/**
 * Symbol kinds.
 */
@Serializable
public enum class SymbolKind {
    FILE,
    MODULE,
    NAMESPACE,
    PACKAGE,
    CLASS,
    INTERFACE,
    TRAIT,
    ENUM,
    ENUM_MEMBER,
    STRUCT,
    OBJECT,
    FUNCTION,
    METHOD,
    CONSTRUCTOR,
    PROPERTY,
    FIELD,
    VARIABLE,
    CONSTANT,
    PARAMETER,
    TYPE_PARAMETER,
    TYPE_ALIAS,
    ANNOTATION,
    MACRO,
    UNKNOWN,
}

/**
 * Visibility modifiers.
 */
@Serializable
public enum class Visibility {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PRIVATE,
}

/**
 * Additional modifiers.
 */
@Serializable
public enum class Modifier {
    STATIC,
    FINAL,
    ABSTRACT,
    SEALED,
    OPEN,
    OVERRIDE,
    SUSPEND,
    INLINE,
    EXTERNAL,
    ASYNC,
    CONST,
    LATEINIT,
    LAZY,
    VIRTUAL,
    DATA,
    COMPANION,
}

/**
 * A reference to a symbol.
 */
@Serializable
public data class SymbolReference(
    /**
     * Reference ID.
     */
    val id: Long = 0,
    /**
     * Referenced symbol ID.
     */
    val symbolId: Long,
    /**
     * File containing the reference.
     */
    val filePath: String,
    /**
     * Line of the reference.
     */
    val line: Int,
    /**
     * Column of the reference.
     */
    val column: Int,
    /**
     * Reference kind.
     */
    val kind: ReferenceKind,
)

/**
 * Reference kinds.
 */
@Serializable
public enum class ReferenceKind {
    /**
     * Symbol definition.
     */
    DEFINITION,

    /**
     * Declaration (forward declaration).
     */
    DECLARATION,

    /**
     * Usage/reference to the symbol.
     */
    USAGE,

    /**
     * Import statement.
     */
    IMPORT,

    /**
     * Type reference.
     */
    TYPE_REFERENCE,

    /**
     * Call site.
     */
    CALL,

    /**
     * Inheritance (extends/implements).
     */
    INHERITANCE,

    /**
     * Override.
     */
    OVERRIDE,
}

/**
 * File index entry for tracking indexed files.
 */
@Serializable
public data class IndexedFile(
    /**
     * File path relative to project root.
     */
    val filePath: String,
    /**
     * Content hash for change detection.
     */
    val contentHash: String,
    /**
     * Last modification time (millis since epoch).
     */
    val lastModified: Long,
    /**
     * File size in bytes.
     */
    val fileSize: Long,
    /**
     * Language ID detected for this file.
     */
    val languageId: String,
    /**
     * Index timestamp (when this file was indexed).
     */
    val indexedAt: Long,
    /**
     * Number of symbols in this file.
     */
    val symbolCount: Int = 0,
)
