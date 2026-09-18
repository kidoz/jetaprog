package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.common.text.CamelHumpMatcher
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
import su.kidoz.jetaprog.editor.navigation.UsageGroup
import su.kidoz.jetaprog.editor.navigation.UsageHighlight
import su.kidoz.jetaprog.editor.navigation.UsageInfo
import su.kidoz.jetaprog.editor.navigation.UsageKind

/**
 * Navigation service implementation backed by a local symbol index.
 *
 * This provides fast "Go to Symbol" functionality without requiring LSP,
 * using the local symbol index for instant results.
 *
 * The optional delegate (embedded servers, language registry, LSP) is always consulted
 * first; the index answers only when the delegate has nothing, so index results never
 * shadow semantically accurate ones.
 */
public class IndexedNavigationService(
    private val symbolIndex: SymbolIndex,
    private val history: NavigationHistory = NavigationHistory(),
    private val lspDelegate: NavigationService? = null,
    private val fileContentProvider: FileContentProvider? = null,
) : NavigationService {
    // ========================================================================
    // Symbol Search - Delegate first, local index as fallback
    // ========================================================================

    override suspend fun searchClasses(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        lspDelegate?.searchClasses(query, scope, limit)?.takeIf { it.isNotEmpty() }?.let { return it }

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
        lspDelegate?.searchFiles(query, scope, limit)?.takeIf { it.isNotEmpty() }?.let { return it }

        // Fallback: match indexed file names. This used to match symbol names and hand
        // back their files, so a file name that was not also a symbol found nothing.
        val matcher = CamelHumpMatcher(query)
        return symbolIndex
            .getIndexedFiles()
            .mapNotNull { path -> matcher.matchingScore(path.substringAfterLast('/'))?.let { score -> path to score } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { (path, score) ->
                NavigationSearchResult(
                    target =
                        NavigationTarget(
                            name = path.substringAfterLast('/'),
                            qualifiedName = path,
                            kind = NavigationSymbolKind.FILE,
                            filePath = path,
                            position = TextPosition(0, 0),
                        ),
                    score = score,
                )
            }
    }

    override suspend fun searchSymbols(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        lspDelegate?.searchSymbols(query, scope, limit)?.takeIf { it.isNotEmpty() }?.let { return it }

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

        // Fallback: the caret sits on a declaration the index knows
        findSymbolAtPosition(filePath, position)?.let { return it.toNavigationTarget() }

        // Fallback: the caret sits on a use. The index records no references, so resolve
        // by name and prefer the declaration closest to the current file.
        val word = wordAt(filePath, position) ?: return null
        return closestDeclaration(symbolIndex.findByName(word).filter { it.name == word }, filePath)
            ?.toNavigationTarget()
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
    ): FindUsagesResult? {
        lspDelegate?.findUsages(filePath, position, scope)?.let { return it }

        // Fallback: whole-word occurrences across the indexed files. This used to list
        // every same-named declaration and call them usages.
        val provider = fileContentProvider ?: return null
        val word = findSymbolAtPosition(filePath, position)?.name ?: wordAt(filePath, position) ?: return null
        val declarations = symbolIndex.findByName(word).filter { it.name == word }
        val declarationOffsets = declarations.map { it.filePath to it.offset }.toSet()
        val symbol =
            closestDeclaration(declarations, filePath)?.toNavigationTarget()
                ?: NavigationTarget(
                    name = word,
                    qualifiedName = word,
                    kind = NavigationSymbolKind.UNKNOWN,
                    filePath = filePath,
                    position = position,
                )
        val pattern = Regex("(?<![A-Za-z0-9_])" + Regex.escape(word) + "(?![A-Za-z0-9_])")
        val files = listOf(filePath) + symbolIndex.getIndexedFiles().filter { it != filePath }.sorted()

        var remaining = MAX_FALLBACK_USAGES
        val groups = mutableListOf<UsageGroup>()
        for (path in files) {
            if (remaining <= 0) break
            val content = provider.getContent(path) ?: continue
            val usages = mutableListOf<UsageInfo>()
            var lineStart = 0
            for ((lineIndex, line) in content.split('\n').withIndex()) {
                for (match in pattern.findAll(line)) {
                    if (remaining <= 0) break
                    val column = match.range.first
                    usages +=
                        UsageInfo(
                            target =
                                NavigationTarget(
                                    name = word,
                                    qualifiedName = word,
                                    kind = NavigationSymbolKind.UNKNOWN,
                                    filePath = path,
                                    position = TextPosition(lineIndex, column),
                                ),
                            usageKind =
                                if ((path to lineStart + column) in declarationOffsets) {
                                    UsageKind.DEFINITION
                                } else {
                                    UsageKind.UNKNOWN
                                },
                            contextLine = line.trim(),
                            lineNumber = lineIndex + 1,
                            columnRange = MatchRange(column, match.range.last),
                        )
                    remaining--
                }
                lineStart += line.length + 1
            }
            if (usages.isNotEmpty()) {
                groups += UsageGroup(filePath = path, fileName = path.substringAfterLast('/'), usages = usages)
            }
        }
        if (groups.isEmpty()) return null
        return FindUsagesResult(
            symbol = symbol,
            groups = groups,
            totalCount = groups.sumOf { it.usages.size },
        )
    }

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

    override suspend fun seedRecentFiles(paths: List<String>) {
        history.restoreRecentFiles(paths)
    }

    override suspend fun recordNavigation(
        filePath: String,
        position: TextPosition,
        preview: String?,
    ) {
        history.record(filePath, position, preview)
    }

    override suspend fun goBack(): NavigationHistoryEntry? = history.goBack()

    override suspend fun goForward(): NavigationHistoryEntry? = history.goForward()

    override suspend fun canGoBack(): Boolean = history.canGoBack()

    override suspend fun canGoForward(): Boolean = history.canGoForward()

    override suspend fun getLastEditLocation(): NavigationHistoryEntry? = history.getLastEditLocation()

    override suspend fun recordEdit(
        filePath: String,
        position: TextPosition,
        preview: String?,
    ) {
        history.recordEdit(filePath, position, preview)
    }

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

    /** The identifier under the caret, read from the live buffer or disk. */
    private fun wordAt(
        filePath: String,
        position: TextPosition,
    ): String? {
        val content = fileContentProvider?.getContent(filePath) ?: return null
        val line = content.split('\n').getOrNull(position.line) ?: return null
        var start = position.column.coerceIn(0, line.length)
        var end = start
        while (start > 0 && line[start - 1].isWordChar()) start--
        while (end < line.length && line[end].isWordChar()) end++
        return line.substring(start, end).takeIf { it.isNotEmpty() }
    }

    private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'

    /**
     * Among same-named declarations, the one most likely meant from [filePath]:
     * same file, then same directory, then same file extension, then path order.
     */
    private fun closestDeclaration(
        candidates: List<IndexedSymbol>,
        filePath: String,
    ): IndexedSymbol? {
        val directory = filePath.substringBeforeLast('/', "")
        val extension = filePath.substringAfterLast('.', "")
        return candidates.minWithOrNull(
            compareBy<IndexedSymbol> { it.filePath != filePath }
                .thenBy { it.filePath.substringBeforeLast('/', "") != directory }
                .thenBy { it.filePath.substringAfterLast('.', "") != extension }
                .thenBy { it.filePath }
                .thenBy { it.offset },
        )
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

    private companion object {
        /** Cap on textual matches returned by the index-backed Find Usages fallback. */
        const val MAX_FALLBACK_USAGES = 500
    }
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
        position = TextPosition(line, column),
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
