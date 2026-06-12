package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.BreadcrumbItem
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.HierarchyNode
import su.kidoz.jetaprog.editor.navigation.MatchRange
import su.kidoz.jetaprog.editor.navigation.NavigationHistory
import su.kidoz.jetaprog.editor.navigation.NavigationHistoryEntry
import su.kidoz.jetaprog.editor.navigation.NavigationSearchResult
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.NavigationTarget
import su.kidoz.jetaprog.editor.navigation.QuickInfo
import su.kidoz.jetaprog.editor.navigation.SearchCategory
import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.editor.navigation.StructureItem
import su.kidoz.jetaprog.editor.navigation.SymbolVisibility
import su.kidoz.jetaprog.editor.navigation.UsageHighlight

/**
 * Navigation service implementation backed by a local symbol index.
 *
 * This provides fast "Go to Symbol" functionality without requiring LSP,
 * using the local symbol index for instant results.
 *
 * For operations that require semantic analysis (definition, usages, etc.),
 * this implementation delegates to an optional LSP-backed service.
 */
public class IndexedNavigationService(
    private val symbolIndex: SymbolIndex,
    private val history: NavigationHistory = NavigationHistory(),
    private val lspDelegate: NavigationService? = null,
    private val fileContentProvider: FileContentProvider? = null,
) : NavigationService {
    // ========================================================================
    // Symbol Search - Uses local index for fast results
    // ========================================================================

    override suspend fun searchClasses(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        val classKinds =
            setOf(
                NavigationSymbolKind.CLASS,
                NavigationSymbolKind.INTERFACE,
                NavigationSymbolKind.ENUM,
                NavigationSymbolKind.STRUCT,
                NavigationSymbolKind.TRAIT,
            )

        return searchSymbolsOfKinds(query, scope, limit, classKinds)
    }

    override suspend fun searchFiles(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        // For file search, we search by file path rather than symbol name
        val matches = symbolIndex.findByPattern(query, scope, limit * 3)

        // Deduplicate by file path and return file targets
        return matches
            .map { it.symbol.filePath }
            .distinct()
            .take(limit)
            .mapIndexed { index, filePath ->
                NavigationSearchResult(
                    target =
                        NavigationTarget(
                            name = filePath.substringAfterLast('/'),
                            qualifiedName = filePath,
                            kind = NavigationSymbolKind.FILE,
                            filePath = filePath,
                            position = TextPosition(0, 0),
                        ),
                    score = limit - index,
                )
            }
    }

    override suspend fun searchSymbols(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        val matches = symbolIndex.findByPattern(query, scope, limit)
        return matches.map { match ->
            NavigationSearchResult(
                target = match.symbol.toNavigationTarget(),
                matchRanges = match.matchRanges.map { it.toMatchRange() },
                score = match.score,
            )
        }
    }

    override suspend fun searchEverywhere(
        query: String,
        limit: Int,
    ): Map<SearchCategory, List<NavigationSearchResult>> {
        val results = mutableMapOf<SearchCategory, List<NavigationSearchResult>>()

        // Classes
        results[SearchCategory.CLASSES] = searchClasses(query, SearchScope.PROJECT, limit)

        // Files
        results[SearchCategory.FILES] = searchFiles(query, SearchScope.PROJECT, limit)

        // All symbols
        results[SearchCategory.SYMBOLS] = searchSymbols(query, SearchScope.PROJECT, limit)

        // Actions and Text would need different sources
        results[SearchCategory.ACTIONS] = emptyList()
        results[SearchCategory.TEXT] = emptyList()

        return results
    }

    // ========================================================================
    // Definition Navigation - Delegates to LSP when available
    // ========================================================================

    override suspend fun getDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? {
        // Try LSP first for accurate semantic navigation
        lspDelegate?.getDefinition(filePath, position)?.let { return it }

        // Fallback: Try to find symbol at position from index
        return findSymbolAtPosition(filePath, position)?.toNavigationTarget()
    }

    override suspend fun getTypeDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? = lspDelegate?.getTypeDefinition(filePath, position)

    override suspend fun getImplementations(
        filePath: String,
        position: TextPosition,
    ): List<NavigationTarget> = lspDelegate?.getImplementations(filePath, position) ?: emptyList()

    override suspend fun getSuperSymbol(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? = lspDelegate?.getSuperSymbol(filePath, position)

    override suspend fun getQuickInfo(
        filePath: String,
        position: TextPosition,
    ): QuickInfo? {
        // Try LSP first
        lspDelegate?.getQuickInfo(filePath, position)?.let { return it }

        // Fallback: Build basic info from index
        val symbol = findSymbolAtPosition(filePath, position) ?: return null
        return QuickInfo(
            symbol = symbol.toNavigationTarget(),
            documentation = null,
            definitionPreview = null,
            signature = symbol.signature,
        )
    }

    // ========================================================================
    // Usage Search - Delegates to LSP
    // ========================================================================

    override suspend fun findUsages(
        filePath: String,
        position: TextPosition,
        scope: SearchScope,
    ): FindUsagesResult? = lspDelegate?.findUsages(filePath, position, scope)

    override suspend fun getUsageHighlights(
        filePath: String,
        position: TextPosition,
    ): List<UsageHighlight> = lspDelegate?.getUsageHighlights(filePath, position) ?: emptyList()

    // ========================================================================
    // Structure Navigation - Uses local index with LSP fallback
    // ========================================================================

    override suspend fun getFileStructure(filePath: String): List<StructureItem> {
        // Try LSP for accurate structure
        lspDelegate?.getFileStructure(filePath)?.takeIf { it.isNotEmpty() }?.let { return it }

        // Fallback: Build structure from index
        val symbols = symbolIndex.getFileSymbols(filePath)
        return buildStructureTree(symbols)
    }

    override suspend fun getBreadcrumbs(
        filePath: String,
        position: TextPosition,
    ): List<BreadcrumbItem> {
        // Try LSP first
        lspDelegate?.getBreadcrumbs(filePath, position)?.takeIf { it.isNotEmpty() }?.let { return it }

        // Fallback: Build breadcrumbs from index
        val symbols = symbolIndex.getFileSymbols(filePath)
        val offset = fileContentProvider?.getOffset(filePath, position) ?: return emptyList()

        return buildBreadcrumbs(symbols, offset)
    }

    // ========================================================================
    // Hierarchy Navigation - Delegates to LSP
    // ========================================================================

    override suspend fun getCallHierarchy(
        filePath: String,
        position: TextPosition,
        incoming: Boolean,
    ): HierarchyNode? = lspDelegate?.getCallHierarchy(filePath, position, incoming)

    override suspend fun getTypeHierarchy(
        filePath: String,
        position: TextPosition,
    ): HierarchyNode? = lspDelegate?.getTypeHierarchy(filePath, position)

    // ========================================================================
    // History Navigation - Uses local history
    // ========================================================================

    override suspend fun getRecentFiles(limit: Int): List<NavigationHistoryEntry> {
        val filePaths = history.getRecentFiles(limit)
        return filePaths.map { path ->
            NavigationHistoryEntry(
                filePath = path,
                position = TextPosition(0, 0),
                timestamp = 0L,
            )
        }
    }

    override suspend fun getRecentLocations(limit: Int): List<NavigationHistoryEntry> =
        history.getRecentLocations(limit)

    override suspend fun recordNavigation(
        filePath: String,
        position: TextPosition,
        preview: String?,
    ) {
        history.record(filePath, position, preview)
    }

    override suspend fun goBack(): NavigationHistoryEntry? = history.goBack()

    override suspend fun goForward(): NavigationHistoryEntry? = history.goForward()

    override suspend fun getLastEditLocation(): NavigationHistoryEntry? = history.getLastEditLocation()

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private suspend fun searchSymbolsOfKinds(
        query: String,
        scope: SearchScope,
        limit: Int,
        kinds: Set<NavigationSymbolKind>,
    ): List<NavigationSearchResult> {
        val matches = symbolIndex.findByPattern(query, scope, limit * 2)
        return matches
            .filter { it.symbol.kind in kinds }
            .take(limit)
            .map { match ->
                NavigationSearchResult(
                    target = match.symbol.toNavigationTarget(),
                    matchRanges = match.matchRanges.map { it.toMatchRange() },
                    score = match.score,
                )
            }
    }

    private suspend fun findSymbolAtPosition(
        filePath: String,
        position: TextPosition,
    ): IndexedSymbol? {
        val symbols = symbolIndex.getFileSymbols(filePath)
        val offset = fileContentProvider?.getOffset(filePath, position) ?: return null

        // Find symbol containing this offset
        return symbols.findLast { symbol ->
            offset >= symbol.offset && offset < symbol.offset + symbol.nameLength
        }
    }

    private fun buildStructureTree(symbols: List<IndexedSymbol>): List<StructureItem> {
        // Group symbols by container
        val topLevel = mutableListOf<StructureItem>()
        val containerSymbols = symbols.filter { it.kind.isContainer() }
        val memberSymbols = symbols.filter { !it.kind.isContainer() }

        for (container in containerSymbols) {
            val members =
                memberSymbols
                    .filter { it.containerName == container.name }
                    .map { it.toStructureItem(depth = 1) }

            topLevel.add(container.toStructureItem(depth = 0, children = members))
        }

        // Add top-level members without containers
        val usedMembers = topLevel.flatMap { it.children }.map { it.target.name }.toSet()
        for (member in memberSymbols) {
            if (member.containerName == null && member.name !in usedMembers) {
                topLevel.add(member.toStructureItem(depth = 0))
            }
        }

        return topLevel.sortedBy { it.target.position.line }
    }

    private fun buildBreadcrumbs(
        symbols: List<IndexedSymbol>,
        offset: Int,
    ): List<BreadcrumbItem> {
        val breadcrumbs = mutableListOf<BreadcrumbItem>()

        // Find containing symbols from outermost to innermost
        val containingSymbols =
            symbols
                .filter { symbol ->
                    offset >= symbol.offset
                }.sortedBy { it.offset }

        // Build path
        var currentContainer: String? = null
        for (symbol in containingSymbols) {
            if (symbol.kind.isContainer()) {
                breadcrumbs.add(
                    BreadcrumbItem(
                        name = symbol.name,
                        target = symbol.toNavigationTarget(),
                        kind = symbol.kind,
                    ),
                )
                currentContainer = symbol.name
            } else if (symbol.containerName == currentContainer) {
                breadcrumbs.add(
                    BreadcrumbItem(
                        name = symbol.name,
                        target = symbol.toNavigationTarget(),
                        kind = symbol.kind,
                    ),
                )
            }
        }

        return breadcrumbs
    }

    private fun NavigationSymbolKind.isContainer(): Boolean =
        this in
            setOf(
                NavigationSymbolKind.CLASS,
                NavigationSymbolKind.INTERFACE,
                NavigationSymbolKind.ENUM,
                NavigationSymbolKind.STRUCT,
                NavigationSymbolKind.OBJECT,
                NavigationSymbolKind.NAMESPACE,
                NavigationSymbolKind.MODULE,
                NavigationSymbolKind.TRAIT,
            )

    private fun IndexedSymbol.toStructureItem(
        depth: Int,
        children: List<StructureItem> = emptyList(),
    ): StructureItem =
        StructureItem(
            target = toNavigationTarget(),
            visibility = SymbolVisibility.PUBLIC,
            children = children,
            depth = depth,
        )
}

/**
 * Convert IndexedSymbol to NavigationTarget.
 */
public fun IndexedSymbol.toNavigationTarget(): NavigationTarget =
    NavigationTarget(
        name = name,
        qualifiedName = qualifiedName,
        kind = kind,
        filePath = filePath,
        position = TextPosition(0, offset), // Line computed from offset
        containerName = containerName,
        detail = signature,
        languageId = languageId,
    )

/**
 * Convert IndexRange to MatchRange.
 */
private fun IndexRange.toMatchRange(): MatchRange = MatchRange(start, end - 1)

/**
 * Interface for getting file content and offset information.
 */
public interface FileContentProvider {
    /**
     * Get the byte offset for a position in a file.
     */
    public fun getOffset(
        filePath: String,
        position: TextPosition,
    ): Int?

    /**
     * Get the position for an offset in a file.
     */
    public fun getPosition(
        filePath: String,
        offset: Int,
    ): TextPosition?

    /**
     * Get file content.
     */
    public fun getContent(filePath: String): String?
}
