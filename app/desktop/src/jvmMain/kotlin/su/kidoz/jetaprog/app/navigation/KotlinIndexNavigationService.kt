package su.kidoz.jetaprog.app.navigation

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.BreadcrumbItem
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.HierarchyNode
import su.kidoz.jetaprog.editor.navigation.MatchRange
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
import su.kidoz.jetaprog.editor.search.ProjectTextSearcher
import su.kidoz.jetaprog.editor.search.TextSearchQuery
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinNavigationProvider
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbol
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.SymbolKind
import su.kidoz.jetaprog.plugins.kotlin.Visibility

/**
 * Navigation service that layers the regex-based [KotlinSymbolIndex] on top of a
 * delegate [NavigationService].
 *
 * The delegate (LSP-backed) service is always consulted first so that a real
 * language server takes precedence once one is registered. When the delegate
 * has no answer, this implementation falls back to the local Kotlin index:
 *
 * - Go to Class / Go to Symbol from the indexed declarations
 * - Go to Declaration via [KotlinNavigationProvider]
 * - File structure and breadcrumbs from the file's indexed symbols
 * - Find usages via whole-word project text search
 *
 * Navigation history and file search are always served by the delegate.
 */
public class KotlinIndexNavigationService(
    private val delegate: NavigationService,
    private val symbolIndex: KotlinSymbolIndex,
    private val fileSystem: FileSystem,
    private val workspacePath: String,
) : NavigationService {
    private val navigationProvider = KotlinNavigationProvider(symbolIndex)
    private val textSearcher = ProjectTextSearcher(fileSystem)

    // ========================================================================
    // Symbol Search
    // ========================================================================

    override suspend fun searchClasses(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> =
        searchIndex(query, limit) { it.kind in CLASS_KINDS } +
            delegate.searchClasses(query, scope, limit)

    override suspend fun searchFiles(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> = delegate.searchFiles(query, scope, limit)

    override suspend fun searchSymbols(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> =
        searchIndex(query, limit) { true } +
            delegate.searchSymbols(query, scope, limit)

    override suspend fun searchEverywhere(
        query: String,
        limit: Int,
    ): Map<SearchCategory, List<NavigationSearchResult>> =
        mapOf(
            SearchCategory.CLASSES to searchClasses(query, SearchScope.PROJECT, limit),
            SearchCategory.FILES to searchFiles(query, SearchScope.PROJECT, limit),
            SearchCategory.SYMBOLS to searchSymbols(query, SearchScope.PROJECT, limit),
        )

    // ========================================================================
    // Definition Navigation
    // ========================================================================

    override suspend fun getDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? {
        delegate.getDefinition(filePath, position)?.let { return it }
        if (!isKotlinFile(filePath)) return null

        val content = fileSystem.readText(filePath).getOrNull() ?: return null
        val location = navigationProvider.goToDefinition(filePath, position, content) ?: return null
        val symbol = symbolIndex.getSymbolAt(location.filePath, location.position)
        return symbol?.toNavigationTarget()
            ?: NavigationTarget(
                name = location.filePath.substringAfterLast('/'),
                qualifiedName = location.filePath,
                kind = NavigationSymbolKind.UNKNOWN,
                filePath = location.filePath,
                position = location.position,
                languageId = KOTLIN_LANGUAGE_ID,
            )
    }

    override suspend fun getTypeDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? = delegate.getTypeDefinition(filePath, position)

    override suspend fun getImplementations(
        filePath: String,
        position: TextPosition,
    ): List<NavigationTarget> = delegate.getImplementations(filePath, position)

    override suspend fun getSuperSymbol(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? = delegate.getSuperSymbol(filePath, position)

    override suspend fun getQuickInfo(
        filePath: String,
        position: TextPosition,
    ): QuickInfo? {
        delegate.getQuickInfo(filePath, position)?.let { return it }

        val target = getDefinition(filePath, position) ?: return null
        val definitionLine =
            fileSystem
                .readText(target.filePath)
                .getOrNull()
                ?.lines()
                ?.getOrNull(target.position.line)
                ?.trim()
        return QuickInfo(
            symbol = target,
            documentation = null,
            definitionPreview = definitionLine,
            signature = target.detail ?: target.qualifiedName,
        )
    }

    // ========================================================================
    // Usage Search
    // ========================================================================

    override suspend fun findUsages(
        filePath: String,
        position: TextPosition,
        scope: SearchScope,
    ): FindUsagesResult? {
        delegate.findUsages(filePath, position, scope)?.let { return it }
        if (!isKotlinFile(filePath)) return null

        val content = fileSystem.readText(filePath).getOrNull() ?: return null
        val identifier = identifierAt(content, position) ?: return null

        val query = TextSearchQuery(query = identifier, caseSensitive = true, wholeWord = true)
        val groups =
            textSearcher
                .search(workspacePath, query, MAX_USAGE_RESULTS)
                .filter { isKotlinFile(it.filePath) }
                .map { file ->
                    UsageGroup(
                        filePath = file.filePath,
                        fileName = file.filePath.substringAfterLast('/'),
                        usages =
                            file.matches.map { match ->
                                UsageInfo(
                                    target =
                                        NavigationTarget(
                                            name = identifier,
                                            qualifiedName = identifier,
                                            kind = NavigationSymbolKind.UNKNOWN,
                                            filePath = file.filePath,
                                            position = TextPosition(match.line, match.startColumn),
                                            languageId = KOTLIN_LANGUAGE_ID,
                                        ),
                                    usageKind = UsageKind.UNKNOWN,
                                    contextLine = match.lineText,
                                    lineNumber = match.line + 1,
                                    columnRange = MatchRange(match.startColumn, match.endColumn - 1),
                                )
                            },
                    )
                }
        if (groups.isEmpty()) return null

        val symbol =
            getDefinition(filePath, position)
                ?: NavigationTarget(
                    name = identifier,
                    qualifiedName = identifier,
                    kind = NavigationSymbolKind.UNKNOWN,
                    filePath = filePath,
                    position = position,
                    languageId = KOTLIN_LANGUAGE_ID,
                )
        return FindUsagesResult(
            symbol = symbol,
            groups = groups,
            totalCount = groups.sumOf { it.usages.size },
        )
    }

    override suspend fun getUsageHighlights(
        filePath: String,
        position: TextPosition,
    ): List<UsageHighlight> = delegate.getUsageHighlights(filePath, position)

    // ========================================================================
    // Structure Navigation
    // ========================================================================

    override suspend fun getFileStructure(filePath: String): List<StructureItem> {
        delegate.getFileStructure(filePath).takeIf { it.isNotEmpty() }?.let { return it }
        if (!isKotlinFile(filePath)) return emptyList()

        val symbols = navigationProvider.getFileOutline(filePath)
        val containers = symbols.filter { it.kind in CONTAINER_KINDS }
        val members = symbols.filterNot { it.kind in CONTAINER_KINDS }
        val containerNames = containers.map { it.name }.toSet()

        val topLevel =
            containers.map { container ->
                container.toStructureItem(
                    depth = 0,
                    children =
                        members
                            .filter { it.parent == container.name }
                            .map { it.toStructureItem(depth = 1) },
                )
            } +
                members
                    .filter { it.parent == null || it.parent !in containerNames }
                    .map { it.toStructureItem(depth = 0) }

        return topLevel.sortedBy { it.target.position.line }
    }

    override suspend fun getBreadcrumbs(
        filePath: String,
        position: TextPosition,
    ): List<BreadcrumbItem> {
        val delegateCrumbs = delegate.getBreadcrumbs(filePath, position)
        // The delegate always emits at least the file crumb; only symbol crumbs
        // beyond it mean a language server answered.
        if (delegateCrumbs.size > 1 || !isKotlinFile(filePath)) return delegateCrumbs

        val symbols = navigationProvider.getFileOutline(filePath)
        val container =
            symbols
                .filter { it.kind in CONTAINER_KINDS && it.range.start.line <= position.line }
                .maxByOrNull { it.range.start.line }
        val member =
            symbols
                .filter { it.kind !in CONTAINER_KINDS && it.range.start.line <= position.line }
                .filter { container == null || it.parent == container.name }
                .maxByOrNull { it.range.start.line }

        val symbolCrumbs =
            listOfNotNull(container, member).map { symbol ->
                BreadcrumbItem(
                    name = symbol.name,
                    target = symbol.toNavigationTarget(),
                    kind = symbol.kind.toNavigationSymbolKind(),
                )
            }
        return delegateCrumbs + symbolCrumbs
    }

    // ========================================================================
    // Hierarchy & History — served by the delegate
    // ========================================================================

    override suspend fun getCallHierarchy(
        filePath: String,
        position: TextPosition,
        incoming: Boolean,
    ): HierarchyNode? = delegate.getCallHierarchy(filePath, position, incoming)

    override suspend fun getTypeHierarchy(
        filePath: String,
        position: TextPosition,
    ): HierarchyNode? = delegate.getTypeHierarchy(filePath, position)

    override suspend fun getRecentFiles(limit: Int): List<NavigationHistoryEntry> = delegate.getRecentFiles(limit)

    override suspend fun getRecentLocations(limit: Int): List<NavigationHistoryEntry> =
        delegate.getRecentLocations(limit)

    override suspend fun recordNavigation(
        filePath: String,
        position: TextPosition,
        preview: String?,
    ) {
        delegate.recordNavigation(filePath, position, preview)
    }

    override suspend fun goBack(): NavigationHistoryEntry? = delegate.goBack()

    override suspend fun goForward(): NavigationHistoryEntry? = delegate.goForward()

    override suspend fun getLastEditLocation(): NavigationHistoryEntry? = delegate.getLastEditLocation()

    // ========================================================================
    // Helpers
    // ========================================================================

    private suspend fun searchIndex(
        query: String,
        limit: Int,
        kindFilter: (KotlinSymbol) -> Boolean,
    ): List<NavigationSearchResult> {
        if (query.isBlank()) return emptyList()
        return symbolIndex
            .search(query, limit * SEARCH_OVERSCAN)
            .filter(kindFilter)
            .map { symbol ->
                NavigationSearchResult(
                    target = symbol.toNavigationTarget(),
                    matchRanges = matchRanges(symbol.name, query),
                    score = matchScore(symbol.name, query),
                )
            }.sortedByDescending { it.score }
            .take(limit)
    }

    private fun matchRanges(
        name: String,
        query: String,
    ): List<MatchRange> {
        val index = name.indexOf(query, ignoreCase = true)
        if (index < 0) return emptyList()
        return listOf(MatchRange(index, index + query.length - 1))
    }

    private fun matchScore(
        name: String,
        query: String,
    ): Int {
        var score = 0
        if (name.equals(query, ignoreCase = true)) score += SCORE_EXACT
        if (name.startsWith(query, ignoreCase = true)) score += SCORE_PREFIX
        if (name.contains(query, ignoreCase = true)) score += SCORE_CONTAINS
        return score - name.length
    }

    private fun isKotlinFile(filePath: String): Boolean = filePath.endsWith(".kt") || filePath.endsWith(".kts")

    private fun identifierAt(
        content: String,
        position: TextPosition,
    ): String? {
        val line = content.lines().getOrNull(position.line) ?: return null
        if (position.column > line.length) return null

        var start = position.column.coerceAtMost(line.length)
        var end = start
        while (start > 0 && line[start - 1].isIdentifierChar()) start--
        while (end < line.length && line[end].isIdentifierChar()) end++
        return line.substring(start, end).takeIf { it.isNotEmpty() }
    }

    private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'

    private fun KotlinSymbol.toStructureItem(
        depth: Int,
        children: List<StructureItem> = emptyList(),
    ): StructureItem =
        StructureItem(
            target = toNavigationTarget(),
            visibility = visibility.toSymbolVisibility(),
            children = children,
            depth = depth,
        )

    private fun KotlinSymbol.toNavigationTarget(): NavigationTarget =
        NavigationTarget(
            name = name,
            qualifiedName = fqName,
            kind = kind.toNavigationSymbolKind(),
            filePath = filePath,
            position = nameRange.start,
            endPosition = nameRange.end,
            containerName = parent,
            detail = signature,
            languageId = KOTLIN_LANGUAGE_ID,
        )

    private fun SymbolKind.toNavigationSymbolKind(): NavigationSymbolKind =
        when (this) {
            SymbolKind.CLASS -> NavigationSymbolKind.CLASS
            SymbolKind.INTERFACE -> NavigationSymbolKind.INTERFACE
            SymbolKind.OBJECT, SymbolKind.COMPANION_OBJECT -> NavigationSymbolKind.OBJECT
            SymbolKind.ENUM -> NavigationSymbolKind.ENUM
            SymbolKind.ENUM_ENTRY -> NavigationSymbolKind.ENUM_MEMBER
            SymbolKind.ANNOTATION -> NavigationSymbolKind.ANNOTATION
            SymbolKind.FUNCTION -> NavigationSymbolKind.FUNCTION
            SymbolKind.PROPERTY -> NavigationSymbolKind.PROPERTY
            SymbolKind.PARAMETER -> NavigationSymbolKind.PARAMETER
            SymbolKind.TYPE_PARAMETER -> NavigationSymbolKind.TYPE_PARAMETER
            SymbolKind.CONSTRUCTOR -> NavigationSymbolKind.CONSTRUCTOR
            SymbolKind.FILE -> NavigationSymbolKind.FILE
        }

    private fun Visibility.toSymbolVisibility(): SymbolVisibility =
        when (this) {
            Visibility.PUBLIC -> SymbolVisibility.PUBLIC
            Visibility.PRIVATE -> SymbolVisibility.PRIVATE
            Visibility.PROTECTED -> SymbolVisibility.PROTECTED
            Visibility.INTERNAL -> SymbolVisibility.INTERNAL
        }

    private companion object {
        const val KOTLIN_LANGUAGE_ID = "kotlin"
        const val SEARCH_OVERSCAN = 3
        const val MAX_USAGE_RESULTS = 500
        const val SCORE_EXACT = 1000
        const val SCORE_PREFIX = 500
        const val SCORE_CONTAINS = 100

        val CLASS_KINDS =
            setOf(
                SymbolKind.CLASS,
                SymbolKind.INTERFACE,
                SymbolKind.OBJECT,
                SymbolKind.ENUM,
                SymbolKind.ANNOTATION,
            )

        val CONTAINER_KINDS =
            setOf(
                SymbolKind.CLASS,
                SymbolKind.INTERFACE,
                SymbolKind.OBJECT,
                SymbolKind.ENUM,
                SymbolKind.ANNOTATION,
                SymbolKind.COMPANION_OBJECT,
            )
    }
}
