package su.kidoz.jetaprog.editor.navigation

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextPosition

/**
 * A range of indices for match highlighting.
 */
@Serializable
public data class MatchRange(
    /**
     * Start index (inclusive).
     */
    val start: Int,
    /**
     * End index (inclusive).
     */
    val endInclusive: Int,
) {
    /**
     * Convert to IntRange.
     */
    public fun toIntRange(): IntRange = start..endInclusive

    public companion object {
        /**
         * Create from IntRange.
         */
        public fun fromIntRange(range: IntRange): MatchRange = MatchRange(range.first, range.last)
    }
}

/**
 * A target location for navigation.
 */
@Serializable
public data class NavigationTarget(
    /**
     * Display name of the symbol.
     */
    val name: String,
    /**
     * Fully qualified name.
     */
    val qualifiedName: String,
    /**
     * Kind of symbol.
     */
    val kind: NavigationSymbolKind,
    /**
     * File path (relative to project root or absolute).
     */
    val filePath: String,
    /**
     * Start position in the file.
     */
    val position: TextPosition,
    /**
     * End position (for highlighting).
     */
    val endPosition: TextPosition? = null,
    /**
     * Container name (e.g., class containing a method).
     */
    val containerName: String? = null,
    /**
     * Additional details (e.g., function signature, type).
     */
    val detail: String? = null,
    /**
     * Documentation/description.
     */
    val documentation: String? = null,
    /**
     * Language ID.
     */
    val languageId: String = "unknown",
)

/**
 * Symbol kinds for navigation.
 */
@Serializable
public enum class NavigationSymbolKind {
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
    KEYWORD,
    UNKNOWN,
}

/**
 * A search result with match information.
 */
@Serializable
public data class NavigationSearchResult(
    /**
     * The navigation target.
     */
    val target: NavigationTarget,
    /**
     * Ranges in the name that matched the query (for highlighting).
     */
    val matchRanges: List<MatchRange> = emptyList(),
    /**
     * Match score for sorting (higher is better).
     */
    val score: Int = 0,
)

/**
 * Information about a symbol usage.
 */
@Serializable
public data class UsageInfo(
    /**
     * Location of the usage.
     */
    val target: NavigationTarget,
    /**
     * Kind of usage.
     */
    val usageKind: UsageKind,
    /**
     * The line of code containing the usage (for preview).
     */
    val contextLine: String,
    /**
     * Line number (1-based for display).
     */
    val lineNumber: Int,
    /**
     * Column range of the usage in the line.
     */
    val columnRange: MatchRange,
)

/**
 * Kinds of symbol usage.
 */
@Serializable
public enum class UsageKind {
    /**
     * Symbol definition/declaration.
     */
    DEFINITION,

    /**
     * Symbol is read.
     */
    READ,

    /**
     * Symbol is written/assigned.
     */
    WRITE,

    /**
     * Method/function call.
     */
    CALL,

    /**
     * Import statement.
     */
    IMPORT,

    /**
     * Type reference.
     */
    TYPE_REFERENCE,

    /**
     * Method override.
     */
    OVERRIDE,

    /**
     * Interface implementation.
     */
    IMPLEMENTATION,

    /**
     * Inheritance (extends).
     */
    INHERITANCE,

    /**
     * Unknown usage.
     */
    UNKNOWN,
}

/**
 * Grouped usages by file.
 */
@Serializable
public data class UsageGroup(
    /**
     * File path.
     */
    val filePath: String,
    /**
     * File name for display.
     */
    val fileName: String,
    /**
     * Usages in this file.
     */
    val usages: List<UsageInfo>,
)

/**
 * Result of find usages operation.
 */
@Serializable
public data class FindUsagesResult(
    /**
     * The symbol being searched.
     */
    val symbol: NavigationTarget,
    /**
     * Usages grouped by file.
     */
    val groups: List<UsageGroup>,
    /**
     * Total number of usages.
     */
    val totalCount: Int,
)

/**
 * A node in a hierarchy tree (call hierarchy, type hierarchy).
 */
@Serializable
public data class HierarchyNode(
    /**
     * The symbol at this node.
     */
    val target: NavigationTarget,
    /**
     * Child nodes.
     */
    val children: List<HierarchyNode> = emptyList(),
    /**
     * Whether this node can be expanded (has potential children).
     */
    val canExpand: Boolean = false,
)

/**
 * File structure item for the Structure popup.
 */
@Serializable
public data class StructureItem(
    /**
     * The navigation target.
     */
    val target: NavigationTarget,
    /**
     * Visibility (public, private, etc.).
     */
    val visibility: SymbolVisibility = SymbolVisibility.PUBLIC,
    /**
     * Whether the symbol is static.
     */
    val isStatic: Boolean = false,
    /**
     * Whether the symbol is abstract.
     */
    val isAbstract: Boolean = false,
    /**
     * Whether the symbol is final.
     */
    val isFinal: Boolean = false,
    /**
     * Child items (nested classes, inner methods, etc.).
     */
    val children: List<StructureItem> = emptyList(),
    /**
     * Depth in the tree (for indentation).
     */
    val depth: Int = 0,
)

/**
 * Symbol visibility levels.
 */
@Serializable
public enum class SymbolVisibility {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PRIVATE,
}

/**
 * Breadcrumb item for navigation path.
 */
@Serializable
public data class BreadcrumbItem(
    /**
     * Display name.
     */
    val name: String,
    /**
     * Navigation target (for clicking).
     */
    val target: NavigationTarget,
    /**
     * Icon kind.
     */
    val kind: NavigationSymbolKind,
)

/**
 * Search scope for navigation queries.
 */
@Serializable
public enum class SearchScope {
    /**
     * Search in the entire project.
     */
    PROJECT,

    /**
     * Search in the current module.
     */
    MODULE,

    /**
     * Search in the current file.
     */
    FILE,

    /**
     * Search in open files.
     */
    OPEN_FILES,

    /**
     * Search including libraries/dependencies.
     */
    ALL_WITH_LIBRARIES,
}

/**
 * Navigation history entry.
 */
@Serializable
public data class NavigationHistoryEntry(
    /**
     * File path.
     */
    val filePath: String,
    /**
     * Position in the file.
     */
    val position: TextPosition,
    /**
     * Timestamp when visited.
     */
    val timestamp: Long,
    /**
     * Preview of the code at this location.
     */
    val preview: String? = null,
)
