package su.kidoz.jetaprog.editor.navigation

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Service for code navigation operations.
 *
 * Provides functionality for:
 * - Symbol search (go to class, file, symbol)
 * - Definition navigation (go to declaration, implementation)
 * - Usage search (find usages, highlight usages)
 * - Structure navigation (file structure, breadcrumbs)
 * - History navigation (recent files, back/forward)
 */
public interface NavigationService {
    // ========================================================================
    // Symbol Search
    // ========================================================================

    /**
     * Search for classes/types by name.
     *
     * @param query The search query (supports camelCase, wildcards)
     * @param scope The search scope
     * @param limit Maximum number of results
     * @return List of matching classes
     */
    public suspend fun searchClasses(
        query: String,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 50,
    ): List<NavigationSearchResult>

    /**
     * Search for files by name.
     *
     * @param query The search query (supports wildcards, folder prefix)
     * @param scope The search scope
     * @param limit Maximum number of results
     * @return List of matching files
     */
    public suspend fun searchFiles(
        query: String,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 50,
    ): List<NavigationSearchResult>

    /**
     * Search for any symbol by name.
     *
     * @param query The search query
     * @param scope The search scope
     * @param limit Maximum number of results
     * @return List of matching symbols
     */
    public suspend fun searchSymbols(
        query: String,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 50,
    ): List<NavigationSearchResult>

    /**
     * Search everywhere (classes, files, symbols, actions).
     *
     * @param query The search query
     * @param limit Maximum number of results per category
     * @return Map of category to results
     */
    public suspend fun searchEverywhere(
        query: String,
        limit: Int = 20,
    ): Map<SearchCategory, List<NavigationSearchResult>>

    // ========================================================================
    // Definition Navigation
    // ========================================================================

    /**
     * Get the declaration/definition of a symbol at the given position.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return The definition target, or null if not found
     */
    public suspend fun getDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget?

    /**
     * Get the type declaration of a symbol at the given position.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return The type definition target, or null if not found
     */
    public suspend fun getTypeDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget?

    /**
     * Get all implementations of an interface/abstract method.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return List of implementation targets
     */
    public suspend fun getImplementations(
        filePath: String,
        position: TextPosition,
    ): List<NavigationTarget>

    /**
     * Get the super class/method of the symbol at the given position.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return The super target, or null if not found
     */
    public suspend fun getSuperSymbol(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget?

    /**
     * Get quick documentation/definition preview for a symbol.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return Quick info with documentation and definition preview
     */
    public suspend fun getQuickInfo(
        filePath: String,
        position: TextPosition,
    ): QuickInfo?

    // ========================================================================
    // Usage Search
    // ========================================================================

    /**
     * Find all usages of a symbol.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @param scope The search scope
     * @return Find usages result with grouped usages
     */
    public suspend fun findUsages(
        filePath: String,
        position: TextPosition,
        scope: SearchScope = SearchScope.PROJECT,
    ): FindUsagesResult?

    /**
     * Get usage highlights in the current file.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return List of text ranges to highlight
     */
    public suspend fun getUsageHighlights(
        filePath: String,
        position: TextPosition,
    ): List<UsageHighlight>

    // ========================================================================
    // Structure Navigation
    // ========================================================================

    /**
     * Get the structure of a file (classes, methods, fields).
     *
     * @param filePath The file path
     * @return List of structure items (tree structure)
     */
    public suspend fun getFileStructure(filePath: String): List<StructureItem>

    /**
     * Get breadcrumbs for the current position.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return List of breadcrumb items from root to current
     */
    public suspend fun getBreadcrumbs(
        filePath: String,
        position: TextPosition,
    ): List<BreadcrumbItem>

    // ========================================================================
    // Hierarchy Navigation
    // ========================================================================

    /**
     * Get the call hierarchy for a method.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @param incoming True for callers, false for callees
     * @return Hierarchy tree
     */
    public suspend fun getCallHierarchy(
        filePath: String,
        position: TextPosition,
        incoming: Boolean = true,
    ): HierarchyNode?

    /**
     * Get the type hierarchy for a class.
     *
     * @param filePath The file path
     * @param position The cursor position
     * @return Hierarchy tree with supertypes and subtypes
     */
    public suspend fun getTypeHierarchy(
        filePath: String,
        position: TextPosition,
    ): HierarchyNode?

    // ========================================================================
    // History Navigation
    // ========================================================================

    /**
     * Get recently opened files.
     *
     * @param limit Maximum number of files
     * @return List of recent file entries
     */
    public suspend fun getRecentFiles(limit: Int = 30): List<NavigationHistoryEntry>

    /**
     * Get recent navigation locations.
     *
     * @param limit Maximum number of locations
     * @return List of recent location entries
     */
    public suspend fun getRecentLocations(limit: Int = 30): List<NavigationHistoryEntry>

    /**
     * Record a navigation to a location.
     *
     * @param filePath The file path
     * @param position The position
     * @param preview Optional code preview
     */
    public suspend fun recordNavigation(
        filePath: String,
        position: TextPosition,
        preview: String? = null,
    )

    /**
     * Go back in navigation history.
     *
     * @return The previous location, or null if at start
     */
    public suspend fun goBack(): NavigationHistoryEntry?

    /**
     * Go forward in navigation history.
     *
     * @return The next location, or null if at end
     */
    public suspend fun goForward(): NavigationHistoryEntry?

    /**
     * Get the last edit location.
     *
     * @return The last edit location, or null if none
     */
    public suspend fun getLastEditLocation(): NavigationHistoryEntry?
}

/**
 * Search categories for Search Everywhere.
 */
public enum class SearchCategory {
    CLASSES,
    FILES,
    SYMBOLS,
    ACTIONS,
    TEXT,
}

/**
 * Quick info for hover/peek definition.
 */
public data class QuickInfo(
    /**
     * The symbol being described.
     */
    val symbol: NavigationTarget,
    /**
     * Documentation in markdown format.
     */
    val documentation: String?,
    /**
     * Definition code preview.
     */
    val definitionPreview: String?,
    /**
     * Type signature.
     */
    val signature: String?,
)

/**
 * A highlight range for usage highlighting.
 */
public data class UsageHighlight(
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
     * Kind of usage for different highlight styles.
     */
    val kind: UsageKind,
)
