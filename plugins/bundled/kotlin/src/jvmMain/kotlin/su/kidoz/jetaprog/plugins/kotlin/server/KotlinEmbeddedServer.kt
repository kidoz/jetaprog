package su.kidoz.jetaprog.plugins.kotlin.server

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyIncomingCallsParams
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyOutgoingCallsParams
import su.kidoz.jetaprog.lsp.protocol.CallHierarchyPrepareParams
import su.kidoz.jetaprog.lsp.protocol.CodeActionParams
import su.kidoz.jetaprog.lsp.protocol.CompletionParams
import su.kidoz.jetaprog.lsp.protocol.DidChangeTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidCloseTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidSaveTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DocumentFormattingParams
import su.kidoz.jetaprog.lsp.protocol.DocumentHighlightParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.InitializeParams
import su.kidoz.jetaprog.lsp.protocol.InitializeResult
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyIncomingCall
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyItem
import su.kidoz.jetaprog.lsp.protocol.LspCallHierarchyOutgoingCall
import su.kidoz.jetaprog.lsp.protocol.LspCodeAction
import su.kidoz.jetaprog.lsp.protocol.LspCompletionList
import su.kidoz.jetaprog.lsp.protocol.LspDocumentHighlight
import su.kidoz.jetaprog.lsp.protocol.LspDocumentHighlightKind
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspHover
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspRange
import su.kidoz.jetaprog.lsp.protocol.LspSymbolKind
import su.kidoz.jetaprog.lsp.protocol.LspTextEdit
import su.kidoz.jetaprog.lsp.protocol.LspTypeHierarchyItem
import su.kidoz.jetaprog.lsp.protocol.MarkupContent
import su.kidoz.jetaprog.lsp.protocol.PublishDiagnosticsParams
import su.kidoz.jetaprog.lsp.protocol.ReferenceParams
import su.kidoz.jetaprog.lsp.protocol.SemanticTokens
import su.kidoz.jetaprog.lsp.protocol.SemanticTokensParams
import su.kidoz.jetaprog.lsp.protocol.ServerCapabilities
import su.kidoz.jetaprog.lsp.protocol.ServerInfo
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentSyncOptions
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchyPrepareParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySubtypesParams
import su.kidoz.jetaprog.lsp.protocol.TypeHierarchySupertypesParams
import su.kidoz.jetaprog.lsp.server.EmbeddedLspServer
import su.kidoz.jetaprog.plugins.kotlin.KotlinNavigationProvider
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbol
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.SymbolKind
import java.io.File

/**
 * Embedded LSP server for Kotlin backed by the local [KotlinSymbolIndex].
 *
 * First iteration of the in-process Kotlin language server: definitions,
 * document symbols, hover and document highlights are answered from the
 * regex-based symbol index. `references` intentionally returns empty so the
 * richer text-search fallback in the application's navigation service is used;
 * it (and semantic analysis for the other features) will move here as the
 * server grows.
 *
 * The index is shared with the host application, which owns workspace-wide
 * indexing; the server re-indexes individual files on open/save notifications.
 */
public class KotlinEmbeddedServer(
    private val symbolIndex: KotlinSymbolIndex,
) : EmbeddedLspServer {
    override val serverId: String = "kotlin-embedded"
    override val languageId: String = "kotlin"

    override val capabilities: ServerCapabilities =
        ServerCapabilities(
            textDocumentSync = TextDocumentSyncOptions(openClose = true, change = SYNC_FULL),
            hoverProvider = true,
            definitionProvider = true,
            documentSymbolProvider = true,
        )

    private var initializedFlag = false
    override val isInitialized: Boolean
        get() = initializedFlag

    private val navigationProvider = KotlinNavigationProvider(symbolIndex)

    /** In-memory contents of open documents, keyed by path. */
    private val openDocuments = mutableMapOf<String, String>()

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override suspend fun initialize(params: InitializeParams): InitializeResult {
        initializedFlag = true
        return InitializeResult(
            capabilities = capabilities,
            serverInfo = ServerInfo(name = "JetaProg Kotlin"),
        )
    }

    override suspend fun initialized() {
        // Nothing to do; workspace indexing is owned by the host application.
    }

    override suspend fun shutdown() {
        initializedFlag = false
        openDocuments.clear()
    }

    override fun dispose() {
        openDocuments.clear()
    }

    // ========================================================================
    // Text Document Sync
    // ========================================================================

    override suspend fun didOpen(params: DidOpenTextDocumentParams) {
        val path = uriToPath(params.textDocument.uri) ?: return
        openDocuments[path] = params.textDocument.text
        symbolIndex.indexFile(path)
    }

    override suspend fun didChange(params: DidChangeTextDocumentParams) {
        val path = uriToPath(params.textDocument.uri) ?: return
        // Full sync: the last change event carries the complete document text.
        params.contentChanges.lastOrNull()?.let { openDocuments[path] = it.text }
    }

    override suspend fun didSave(params: DidSaveTextDocumentParams) {
        val path = uriToPath(params.textDocument.uri) ?: return
        params.text?.let { openDocuments[path] = it }
        symbolIndex.indexFile(path)
    }

    override suspend fun didClose(params: DidCloseTextDocumentParams) {
        val path = uriToPath(params.textDocument.uri) ?: return
        openDocuments.remove(path)
    }

    // ========================================================================
    // Language Features
    // ========================================================================

    override suspend fun completion(params: CompletionParams): LspCompletionList? = null

    override suspend fun hover(params: TextDocumentPositionParams): LspHover? {
        val path = uriToPath(params.textDocument.uri) ?: return null
        val position = params.position.toTextPosition()
        val content = documentContent(path) ?: return null
        val location = navigationProvider.goToDefinition(path, position, content) ?: return null
        val symbol = symbolIndex.getSymbolAt(location.filePath, location.position) ?: return null

        val declarationLine =
            documentContent(symbol.filePath)
                ?.lines()
                ?.getOrNull(symbol.range.start.line)
                ?.trim()
        val markdown =
            buildString {
                if (declarationLine != null) {
                    appendLine("```kotlin")
                    appendLine(declarationLine)
                    appendLine("```")
                }
                append(symbol.fqName)
            }
        return LspHover(
            contents = MarkupContent(kind = "markdown", value = markdown),
        )
    }

    override suspend fun definition(params: TextDocumentPositionParams): List<LspLocation> {
        val path = uriToPath(params.textDocument.uri) ?: return emptyList()
        val position = params.position.toTextPosition()
        val content = documentContent(path) ?: return emptyList()
        val location = navigationProvider.goToDefinition(path, position, content) ?: return emptyList()
        return listOf(
            LspLocation(
                uri = pathToUri(location.filePath),
                range = location.range.toLspRange(),
            ),
        )
    }

    override suspend fun typeDefinition(params: TextDocumentPositionParams): List<LspLocation> = emptyList()

    override suspend fun implementation(params: TextDocumentPositionParams): List<LspLocation> = emptyList()

    override suspend fun references(params: ReferenceParams): List<LspLocation> = emptyList()

    override suspend fun documentSymbol(params: DocumentSymbolParams): List<LspDocumentSymbol> {
        val path = uriToPath(params.textDocument.uri) ?: return emptyList()
        var symbols = symbolIndex.getFileSymbols(path)
        if (symbols.isEmpty()) {
            symbolIndex.indexFile(path)
            symbols = symbolIndex.getFileSymbols(path)
        }
        if (symbols.isEmpty()) return emptyList()

        val containers = symbols.filter { it.kind in CONTAINER_KINDS }
        val containerNames = containers.map { it.name }.toSet()
        val members = symbols.filterNot { it.kind in CONTAINER_KINDS }

        val topLevel =
            containers.map { container ->
                container.toDocumentSymbol(
                    children =
                        members
                            .filter { it.parent == container.name }
                            .map { it.toDocumentSymbol() },
                )
            } +
                members
                    .filter { it.parent == null || it.parent !in containerNames }
                    .map { it.toDocumentSymbol() }

        return topLevel.sortedBy { it.range.start.line }
    }

    override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens? = null

    override suspend fun codeAction(params: CodeActionParams): List<LspCodeAction> = emptyList()

    override suspend fun formatting(params: DocumentFormattingParams): List<LspTextEdit> = emptyList()

    override suspend fun documentHighlight(params: DocumentHighlightParams): List<LspDocumentHighlight> {
        val path = uriToPath(params.textDocument.uri) ?: return emptyList()
        val content = documentContent(path) ?: return emptyList()
        val identifier = identifierAt(content, params.position.toTextPosition()) ?: return emptyList()

        val wordRegex = Regex("\\b${Regex.escape(identifier)}\\b")
        return content
            .lines()
            .flatMapIndexed { lineIndex, line ->
                wordRegex.findAll(line).map { match ->
                    LspDocumentHighlight(
                        range =
                            LspRange(
                                start = LspPosition(lineIndex, match.range.first),
                                end = LspPosition(lineIndex, match.range.last + 1),
                            ),
                        kind = LspDocumentHighlightKind.Text,
                    )
                }
            }
    }

    // ========================================================================
    // Call & Type Hierarchy — not provided by the index-backed server
    // ========================================================================

    override suspend fun prepareCallHierarchy(params: CallHierarchyPrepareParams): List<LspCallHierarchyItem> =
        emptyList()

    override suspend fun callHierarchyIncomingCalls(
        params: CallHierarchyIncomingCallsParams,
    ): List<LspCallHierarchyIncomingCall> = emptyList()

    override suspend fun callHierarchyOutgoingCalls(
        params: CallHierarchyOutgoingCallsParams,
    ): List<LspCallHierarchyOutgoingCall> = emptyList()

    override suspend fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): List<LspTypeHierarchyItem> =
        emptyList()

    override suspend fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): List<LspTypeHierarchyItem> =
        emptyList()

    override suspend fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): List<LspTypeHierarchyItem> =
        emptyList()

    // ========================================================================
    // Diagnostics
    // ========================================================================

    override fun onDiagnostics(callback: (PublishDiagnosticsParams) -> Unit) {
        // Diagnostics are not published by the index-backed server yet.
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun documentContent(path: String): String? =
        openDocuments[path]
            ?: File(path).takeIf { it.isFile }?.readText()

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

    private fun uriToPath(uri: String): String? = uri.takeIf { it.startsWith("file://") }?.removePrefix("file://")

    private fun pathToUri(path: String): String = if (path.startsWith("file://")) path else "file://$path"

    private fun TextPosition.toLspPosition(): LspPosition = LspPosition(line, column)

    private fun LspPosition.toTextPosition(): TextPosition = TextPosition(line, character)

    private fun TextRange.toLspRange(): LspRange = LspRange(start.toLspPosition(), end.toLspPosition())

    private fun KotlinSymbol.toDocumentSymbol(children: List<LspDocumentSymbol> = emptyList()): LspDocumentSymbol =
        LspDocumentSymbol(
            name = name,
            detail = signature ?: fqName,
            kind = kind.toLspSymbolKind(),
            range = range.toLspRange(),
            selectionRange = nameRange.toLspRange(),
            children = children.ifEmpty { null },
        )

    private fun SymbolKind.toLspSymbolKind(): LspSymbolKind =
        when (this) {
            SymbolKind.CLASS, SymbolKind.ANNOTATION -> LspSymbolKind.Class
            SymbolKind.INTERFACE -> LspSymbolKind.Interface
            SymbolKind.OBJECT, SymbolKind.COMPANION_OBJECT -> LspSymbolKind.Object
            SymbolKind.ENUM -> LspSymbolKind.Enum
            SymbolKind.ENUM_ENTRY -> LspSymbolKind.EnumMember
            SymbolKind.FUNCTION -> LspSymbolKind.Function
            SymbolKind.PROPERTY -> LspSymbolKind.Property
            SymbolKind.PARAMETER -> LspSymbolKind.Variable
            SymbolKind.TYPE_PARAMETER -> LspSymbolKind.TypeParameter
            SymbolKind.CONSTRUCTOR -> LspSymbolKind.Constructor
            SymbolKind.FILE -> LspSymbolKind.File
        }

    private companion object {
        /** LSP text document sync kind: full document on every change. */
        const val SYNC_FULL = 1

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
