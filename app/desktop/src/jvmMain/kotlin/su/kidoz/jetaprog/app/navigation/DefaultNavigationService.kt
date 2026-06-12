package su.kidoz.jetaprog.app.navigation

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.BreadcrumbItem
import su.kidoz.jetaprog.editor.navigation.FindUsagesResult
import su.kidoz.jetaprog.editor.navigation.HierarchyNode
import su.kidoz.jetaprog.editor.navigation.LspNavigationAdapter
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
import su.kidoz.jetaprog.editor.navigation.UsageHighlight
import su.kidoz.jetaprog.editor.navigation.UsageKind
import su.kidoz.jetaprog.lsp.client.LspClient
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyIncomingCallsParams
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyOutgoingCallsParams
import su.kidoz.jetaprog.lsp.protocol.DocumentHighlightParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.LspDocumentHighlightKind
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.ReferenceContext
import su.kidoz.jetaprog.lsp.protocol.ReferenceParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentIdentifier
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySubtypesParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySupertypesParams
import su.kidoz.jetaprog.lsp.server.EmbeddedServerRegistry
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Default implementation of NavigationService.
 *
 * Provides navigation features by delegating to:
 * 1. Embedded LSP servers (in-process, zero-latency)
 * 2. External LSP client (fallback)
 *
 * Features:
 * - Definition navigation via textDocument/definition
 * - References search via textDocument/references
 * - File structure via textDocument/documentSymbol
 * - Hover info via textDocument/hover
 * - Workspace search (classes, files, symbols)
 *
 * Also maintains navigation history for back/forward navigation.
 */
public class DefaultNavigationService(
    private val lspClient: LspClient?,
    private val fileSystem: FileSystem,
    private val embeddedServerRegistry: EmbeddedServerRegistry? = null,
    private val workspacePath: String = System.getProperty("user.dir"),
) : NavigationService {
    private val history = NavigationHistory()
    private val adapter = LspNavigationAdapter()

    // File extensions to include in file search
    private val sourceExtensions =
        setOf(
            "kt",
            "kts",
            "java",
            "py",
            "js",
            "ts",
            "tsx",
            "jsx",
            "rs",
            "go",
            "c",
            "cpp",
            "h",
            "hpp",
            "vala",
            "vapi",
            "xml",
            "json",
            "yaml",
            "yml",
            "toml",
            "md",
            "txt",
            "gradle",
            "properties",
            "sh",
            "bat",
            "meson",
        )

    // ========================================================================
    // Symbol Search
    // ========================================================================

    override suspend fun searchClasses(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<NavigationSearchResult>()

        // Search for symbols matching class-like kinds from embedded servers
        embeddedServerRegistry?.getRegisteredLanguages()?.forEach { languageId ->
            val server = embeddedServerRegistry.getServer(languageId) ?: return@forEach

            // Get all indexed files and search their symbols
            val symbolResults =
                searchServerSymbols(server, query, limit) { kind ->
                    kind in
                        listOf(
                            NavigationSymbolKind.CLASS,
                            NavigationSymbolKind.INTERFACE,
                            NavigationSymbolKind.ENUM,
                            NavigationSymbolKind.STRUCT,
                            NavigationSymbolKind.OBJECT,
                            NavigationSymbolKind.TRAIT,
                        )
                }
            results.addAll(symbolResults)
        }

        return results
            .sortedByDescending { it.score }
            .take(limit)
    }

    override suspend fun searchFiles(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<NavigationSearchResult>()
        val lowerQuery = query.lowercase()

        // Recursively search for files matching the query
        searchFilesRecursively(workspacePath, lowerQuery, results, limit)

        return results
            .sortedByDescending { it.score }
            .take(limit)
    }

    override suspend fun searchSymbols(
        query: String,
        scope: SearchScope,
        limit: Int,
    ): List<NavigationSearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<NavigationSearchResult>()

        // Search all symbols from embedded servers (no kind filter)
        embeddedServerRegistry?.getRegisteredLanguages()?.forEach { languageId ->
            val server = embeddedServerRegistry.getServer(languageId) ?: return@forEach
            val symbolResults = searchServerSymbols(server, query, limit) { true }
            results.addAll(symbolResults)
        }

        return results
            .sortedByDescending { it.score }
            .take(limit)
    }

    override suspend fun searchEverywhere(
        query: String,
        limit: Int,
    ): Map<SearchCategory, List<NavigationSearchResult>> {
        val results = mutableMapOf<SearchCategory, List<NavigationSearchResult>>()

        results[SearchCategory.CLASSES] = searchClasses(query, SearchScope.PROJECT, limit)
        results[SearchCategory.FILES] = searchFiles(query, SearchScope.PROJECT, limit)
        results[SearchCategory.SYMBOLS] = searchSymbols(query, SearchScope.PROJECT, limit)

        return results
    }

    // ========================================================================
    // Definition Navigation
    // ========================================================================

    override suspend fun getDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? {
        // Try embedded server first (faster, zero-latency)
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )
            val locations = server.definition(params)
            if (locations.isNotEmpty()) {
                return adapter.toNavigationTarget(locations.first())
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return null

        val params =
            TextDocumentPositionParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
            )

        val locations = client.definition(params) ?: return null
        return locations.firstOrNull()?.let { adapter.toNavigationTarget(it) }
    }

    override suspend fun getTypeDefinition(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )
            val locations = server.typeDefinition(params)
            if (locations.isNotEmpty()) {
                return adapter.toNavigationTarget(locations.first())
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return null

        val params =
            TextDocumentPositionParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
            )

        val locations = client.typeDefinition(params) ?: return null
        return locations.firstOrNull()?.let { adapter.toNavigationTarget(it) }
    }

    override suspend fun getImplementations(
        filePath: String,
        position: TextPosition,
    ): List<NavigationTarget> {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )
            val locations = server.implementation(params)
            if (locations.isNotEmpty()) {
                return locations.map { adapter.toNavigationTarget(it) }
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return emptyList()

        val params =
            TextDocumentPositionParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
            )

        val locations = client.implementation(params) ?: return emptyList()
        return locations.map { adapter.toNavigationTarget(it) }
    }

    override suspend fun getSuperSymbol(
        filePath: String,
        position: TextPosition,
    ): NavigationTarget? {
        // TODO: Requires language-specific analysis
        return null
    }

    override suspend fun getQuickInfo(
        filePath: String,
        position: TextPosition,
    ): QuickInfo? {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )
            val hover = server.hover(params)
            if (hover != null) {
                val symbol =
                    NavigationTarget(
                        name = "Symbol",
                        qualifiedName = "",
                        kind = NavigationSymbolKind.UNKNOWN,
                        filePath = filePath,
                        position = position,
                    )
                return adapter.toQuickInfo(symbol, hover)
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return null

        val params =
            TextDocumentPositionParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
            )

        val hover = client.hover(params) ?: return null

        // Create a basic navigation target for the symbol
        val symbol =
            NavigationTarget(
                name = "Symbol",
                qualifiedName = "",
                kind = NavigationSymbolKind.UNKNOWN,
                filePath = filePath,
                position = position,
            )

        return adapter.toQuickInfo(symbol, hover)
    }

    // ========================================================================
    // Usage Search
    // ========================================================================

    override suspend fun findUsages(
        filePath: String,
        position: TextPosition,
        scope: SearchScope,
    ): FindUsagesResult? {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                ReferenceParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                    context = ReferenceContext(includeDeclaration = true),
                )
            val locations = server.references(params)
            if (locations.isNotEmpty()) {
                val symbol =
                    NavigationTarget(
                        name = "Symbol",
                        qualifiedName = "",
                        kind = NavigationSymbolKind.UNKNOWN,
                        filePath = filePath,
                        position = position,
                    )
                return adapter.toFindUsagesResult(symbol, locations)
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return null

        val params =
            ReferenceParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
                context = ReferenceContext(includeDeclaration = true),
            )

        val locations = client.references(params) ?: return null
        if (locations.isEmpty()) return null

        // Create a symbol representing the search target
        val symbol =
            NavigationTarget(
                name = "Symbol",
                qualifiedName = "",
                kind = NavigationSymbolKind.UNKNOWN,
                filePath = filePath,
                position = position,
            )

        return adapter.toFindUsagesResult(symbol, locations)
    }

    override suspend fun getUsageHighlights(
        filePath: String,
        position: TextPosition,
    ): List<UsageHighlight> {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                DocumentHighlightParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )
            val highlights = server.documentHighlight(params)
            if (highlights.isNotEmpty()) {
                return highlights.map { highlight ->
                    val range = highlight.range
                    UsageHighlight(
                        startLine = range.start.line,
                        startColumn = range.start.character,
                        endLine = range.end.line,
                        endColumn = range.end.character,
                        kind =
                            when (highlight.kind) {
                                LspDocumentHighlightKind.Write -> UsageKind.WRITE
                                LspDocumentHighlightKind.Read -> UsageKind.READ
                                else -> UsageKind.UNKNOWN
                            },
                    )
                }
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return emptyList()

        val params =
            TextDocumentPositionParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                position = LspPosition(position.line, position.column),
            )

        val highlights = client.documentHighlight(params) ?: return emptyList()
        return highlights.map { highlight ->
            val range = highlight.range
            UsageHighlight(
                startLine = range.start.line,
                startColumn = range.start.character,
                endLine = range.end.line,
                endColumn = range.end.character,
                kind =
                    when (highlight.kind) {
                        LspDocumentHighlightKind.Write -> UsageKind.WRITE
                        LspDocumentHighlightKind.Read -> UsageKind.READ
                        else -> UsageKind.UNKNOWN
                    },
            )
        }
    }

    // ========================================================================
    // Structure Navigation
    // ========================================================================

    override suspend fun getFileStructure(filePath: String): List<StructureItem> {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val params =
                DocumentSymbolParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                )
            val symbols = server.documentSymbol(params)
            if (symbols.isNotEmpty()) {
                return adapter.toStructureItems(symbols, filePath)
            }
        }

        // Fall back to external LSP client
        val client = lspClient ?: return emptyList()

        val params =
            DocumentSymbolParams(
                textDocument = TextDocumentIdentifier(pathToUri(filePath)),
            )

        val symbols = client.documentSymbols(params) ?: return emptyList()
        return adapter.toStructureItems(symbols, filePath)
    }

    override suspend fun getBreadcrumbs(
        filePath: String,
        position: TextPosition,
    ): List<BreadcrumbItem> {
        // Get file structure and find the path to current position
        val structure = getFileStructure(filePath)
        val breadcrumbs = mutableListOf<BreadcrumbItem>()

        // Add file as first breadcrumb
        val fileName = filePath.substringAfterLast('/')
        breadcrumbs.add(
            BreadcrumbItem(
                name = fileName,
                target =
                    NavigationTarget(
                        name = fileName,
                        qualifiedName = filePath,
                        kind = NavigationSymbolKind.FILE,
                        filePath = filePath,
                        position = TextPosition(0, 0),
                    ),
                kind = NavigationSymbolKind.FILE,
            ),
        )

        // Find containing symbols at the position
        fun findContainingSymbols(
            items: List<StructureItem>,
            path: MutableList<BreadcrumbItem>,
        ) {
            for (item in items) {
                val target = item.target
                val startLine = target.position.line
                val endLine = target.endPosition?.line ?: startLine

                if (position.line in startLine..endLine) {
                    path.add(
                        BreadcrumbItem(
                            name = target.name,
                            target = target,
                            kind = target.kind,
                        ),
                    )
                    findContainingSymbols(item.children, path)
                    return
                }
            }
        }

        findContainingSymbols(structure, breadcrumbs)
        return breadcrumbs
    }

    // ========================================================================
    // Hierarchy Navigation
    // ========================================================================

    override suspend fun getCallHierarchy(
        filePath: String,
        position: TextPosition,
        incoming: Boolean,
    ): HierarchyNode? {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val prepareParams =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )

            val items = server.prepareCallHierarchy(prepareParams)
            if (items.isEmpty()) return null

            val rootItem = items.first()
            val rootTarget =
                NavigationTarget(
                    name = rootItem.name,
                    qualifiedName = rootItem.detail ?: rootItem.name,
                    kind = adapter.toLspSymbolKind(rootItem.kind),
                    filePath = uriToPath(rootItem.uri) ?: filePath,
                    position =
                        TextPosition(
                            rootItem.selectionRange.start.line,
                            rootItem.selectionRange.start.character,
                        ),
                )

            val children =
                if (incoming) {
                    val incomingCalls =
                        server.callHierarchyIncomingCalls(
                            CallHierarchyIncomingCallsParams(item = rootItem),
                        )
                    incomingCalls.map { call ->
                        HierarchyNode(
                            target =
                                NavigationTarget(
                                    name = call.from.name,
                                    qualifiedName = call.from.detail ?: call.from.name,
                                    kind = adapter.toLspSymbolKind(call.from.kind),
                                    filePath = uriToPath(call.from.uri) ?: "",
                                    position =
                                        TextPosition(
                                            call.from.selectionRange.start.line,
                                            call.from.selectionRange.start.character,
                                        ),
                                ),
                            canExpand = true,
                        )
                    }
                } else {
                    val outgoingCalls =
                        server.callHierarchyOutgoingCalls(
                            CallHierarchyOutgoingCallsParams(item = rootItem),
                        )
                    outgoingCalls.map { call ->
                        HierarchyNode(
                            target =
                                NavigationTarget(
                                    name = call.to.name,
                                    qualifiedName = call.to.detail ?: call.to.name,
                                    kind = adapter.toLspSymbolKind(call.to.kind),
                                    filePath = uriToPath(call.to.uri) ?: "",
                                    position =
                                        TextPosition(
                                            call.to.selectionRange.start.line,
                                            call.to.selectionRange.start.character,
                                        ),
                                ),
                            canExpand = true,
                        )
                    }
                }

            return HierarchyNode(
                target = rootTarget,
                children = children,
                canExpand = children.isNotEmpty(),
            )
        }

        // TODO: Fall back to external LSP client when callHierarchy is added
        return null
    }

    override suspend fun getTypeHierarchy(
        filePath: String,
        position: TextPosition,
    ): HierarchyNode? {
        // Try embedded server first
        val languageId = detectLanguageId(filePath)
        embeddedServerRegistry?.getServer(languageId)?.let { server ->
            val prepareParams =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(pathToUri(filePath)),
                    position = LspPosition(position.line, position.column),
                )

            val items = server.prepareTypeHierarchy(prepareParams)
            if (items.isEmpty()) return null

            val rootItem = items.first()
            val rootTarget =
                NavigationTarget(
                    name = rootItem.name,
                    qualifiedName = rootItem.detail ?: rootItem.name,
                    kind = adapter.toLspSymbolKind(rootItem.kind),
                    filePath = uriToPath(rootItem.uri) ?: filePath,
                    position =
                        TextPosition(
                            rootItem.selectionRange.start.line,
                            rootItem.selectionRange.start.character,
                        ),
                )

            // Get supertypes (parents)
            val supertypes =
                server.typeHierarchySupertypes(TypeHierarchySupertypesParams(item = rootItem))
            val supertypeNodes =
                supertypes.map { item ->
                    HierarchyNode(
                        target =
                            NavigationTarget(
                                name = item.name,
                                qualifiedName = item.detail ?: item.name,
                                kind = adapter.toLspSymbolKind(item.kind),
                                filePath = uriToPath(item.uri) ?: "",
                                position =
                                    TextPosition(
                                        item.selectionRange.start.line,
                                        item.selectionRange.start.character,
                                    ),
                            ),
                        canExpand = true,
                    )
                }

            // Get subtypes (children)
            val subtypes =
                server.typeHierarchySubtypes(TypeHierarchySubtypesParams(item = rootItem))
            val subtypeNodes =
                subtypes.map { item ->
                    HierarchyNode(
                        target =
                            NavigationTarget(
                                name = item.name,
                                qualifiedName = item.detail ?: item.name,
                                kind = adapter.toLspSymbolKind(item.kind),
                                filePath = uriToPath(item.uri) ?: "",
                                position =
                                    TextPosition(
                                        item.selectionRange.start.line,
                                        item.selectionRange.start.character,
                                    ),
                            ),
                        canExpand = true,
                    )
                }

            // Combine supertypes and subtypes as children
            val allChildren = supertypeNodes + subtypeNodes

            return HierarchyNode(
                target = rootTarget,
                children = allChildren,
                canExpand = allChildren.isNotEmpty(),
            )
        }

        // TODO: Fall back to external LSP client when typeHierarchy is added
        return null
    }

    // ========================================================================
    // History Navigation
    // ========================================================================

    override suspend fun getRecentFiles(limit: Int): List<NavigationHistoryEntry> =
        history.getRecentFiles(limit).map { filePath ->
            NavigationHistoryEntry(
                filePath = filePath,
                position = TextPosition(0, 0),
                timestamp = System.currentTimeMillis(),
                preview = null,
            )
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
    // Utility Methods
    // ========================================================================

    /**
     * Record an edit location for "last edit location" navigation.
     */
    public fun recordEdit(
        filePath: String,
        position: TextPosition,
        preview: String? = null,
    ) {
        history.recordEdit(filePath, position, preview)
    }

    /**
     * Convert file path to file:// URI.
     */
    private fun pathToUri(filePath: String): String =
        if (filePath.startsWith("file://")) filePath else "file://$filePath"

    /**
     * Convert file:// URI to path.
     */
    private fun uriToPath(uri: String): String? = uri.takeIf { it.startsWith("file://") }?.removePrefix("file://")

    /**
     * Detect language ID from file extension.
     */
    private fun detectLanguageId(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "mjs", "cjs" -> "javascript"
            "ts", "mts", "cts" -> "typescript"
            "rs" -> "rust"
            "go" -> "go"
            "c", "h" -> "c"
            "cpp", "hpp", "cc", "cxx" -> "cpp"
            "vala", "vapi" -> "vala"
            else -> extension
        }
    }

    // ========================================================================
    // Search Helpers
    // ========================================================================

    /**
     * Recursively search for files matching a query.
     */
    private suspend fun searchFilesRecursively(
        path: String,
        query: String,
        results: MutableList<NavigationSearchResult>,
        limit: Int,
    ) {
        if (results.size >= limit) return

        val entries = fileSystem.listDirectory(path).getOrNull() ?: return

        for (entry in entries) {
            if (results.size >= limit) break

            // Skip hidden files and common non-source directories
            if (entry.isHidden) continue
            if (entry.isDirectory &&
                entry.name in
                listOf(
                    "node_modules",
                    ".git",
                    ".gradle",
                    "build",
                    "out",
                    ".idea",
                    "target",
                    "__pycache__",
                    ".venv",
                    "venv",
                )
            ) {
                continue
            }

            if (entry.isDirectory) {
                searchFilesRecursively(entry.path, query, results, limit)
            } else if (entry.isFile) {
                val ext = fileSystem.extension(entry.path).lowercase()
                if (ext !in sourceExtensions) continue

                val fileName = entry.name.lowercase()
                if (fileName.contains(query)) {
                    val matchRanges = findMatchRanges(entry.name, query)
                    val score = calculateMatchScore(entry.name, query)

                    results.add(
                        NavigationSearchResult(
                            target =
                                NavigationTarget(
                                    name = entry.name,
                                    qualifiedName = entry.path,
                                    kind = NavigationSymbolKind.FILE,
                                    filePath = entry.path,
                                    position = TextPosition(0, 0),
                                    languageId = detectLanguageId(entry.path),
                                ),
                            matchRanges = matchRanges,
                            score = score,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Search symbols from an embedded server.
     *
     * Currently returns empty as workspace/symbol LSP method is not yet implemented.
     * For Kotlin, symbols are searchable via the KotlinSymbolIndex directly.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun searchServerSymbols(
        server: su.kidoz.jetaprog.lsp.server.EmbeddedLspServer,
        query: String,
        limit: Int,
        kindFilter: (NavigationSymbolKind) -> Boolean,
    ): List<NavigationSearchResult> {
        // TODO: Implement workspace/symbol LSP method in EmbeddedLspServer
        // For now, symbol search relies on file-based search
        // A complete implementation would:
        // 1. Add workspaceSymbol() method to EmbeddedLspServer interface
        // 2. Implement it in KotlinEmbeddedServer using KotlinSymbolIndex.search()
        // 3. Convert KotlinSymbol to NavigationSearchResult
        return emptyList()
    }

    /**
     * Find match ranges in a string for highlighting.
     */
    private fun findMatchRanges(
        text: String,
        query: String,
    ): List<MatchRange> {
        val ranges = mutableListOf<MatchRange>()
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        var startIndex = 0
        while (true) {
            val index = lowerText.indexOf(lowerQuery, startIndex)
            if (index < 0) break
            ranges.add(MatchRange(index, index + query.length - 1))
            startIndex = index + 1
        }

        return ranges
    }

    /**
     * Calculate a match score for sorting search results.
     * Higher score = better match.
     */
    private fun calculateMatchScore(
        text: String,
        query: String,
    ): Int {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        var score = 0

        // Exact match gets highest score
        if (lowerText == lowerQuery) {
            score += 1000
        }

        // Starts with query gets high score
        if (lowerText.startsWith(lowerQuery)) {
            score += 500
        }

        // Contains query
        if (lowerText.contains(lowerQuery)) {
            score += 100
        }

        // CamelCase matching
        val camelMatches = matchCamelCase(text, query)
        score += camelMatches * 50

        // Shorter names are preferred for equal matches
        score -= text.length

        return score
    }

    /**
     * Count how many CamelCase parts match the query.
     * For example, "FileSystem" matches "FS" with 2 matches.
     */
    private fun matchCamelCase(
        text: String,
        query: String,
    ): Int {
        val upperChars = text.filter { it.isUpperCase() }
        val lowerQuery = query.lowercase()
        var matches = 0
        var queryIndex = 0

        for (char in upperChars) {
            if (queryIndex < lowerQuery.length &&
                char.lowercaseChar() == lowerQuery[queryIndex]
            ) {
                matches++
                queryIndex++
            }
        }

        return if (queryIndex == lowerQuery.length) matches else 0
    }
}
