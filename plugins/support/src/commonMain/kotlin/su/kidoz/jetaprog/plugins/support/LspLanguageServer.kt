package su.kidoz.jetaprog.plugins.support

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.completion.resolvedWith
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.lsp.client.LspClient
import su.kidoz.jetaprog.lsp.client.LspClientConfig
import su.kidoz.jetaprog.lsp.client.WorkspaceEditCallback
import su.kidoz.jetaprog.lsp.protocol.CodeActionContext
import su.kidoz.jetaprog.lsp.protocol.CodeActionParams
import su.kidoz.jetaprog.lsp.protocol.CompletionContext
import su.kidoz.jetaprog.lsp.protocol.CompletionParams
import su.kidoz.jetaprog.lsp.protocol.DidChangeTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidCloseTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidSaveTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DocumentFormattingParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspFormattingOptions
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspSymbolInformation
import su.kidoz.jetaprog.lsp.protocol.ReferenceContext
import su.kidoz.jetaprog.lsp.protocol.ReferenceParams
import su.kidoz.jetaprog.lsp.protocol.SignatureHelpParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentContentChangeEvent
import su.kidoz.jetaprog.lsp.protocol.TextDocumentIdentifier
import su.kidoz.jetaprog.lsp.protocol.TextDocumentItem
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.lsp.protocol.VersionedTextDocumentIdentifier
import su.kidoz.jetaprog.lsp.protocol.WorkspaceSymbolParams
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.services.CodeActionProvider
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.CompletionTriggerKind
import su.kidoz.jetaprog.plugins.api.services.DefinitionProvider
import su.kidoz.jetaprog.plugins.api.services.FormattingProvider
import su.kidoz.jetaprog.plugins.api.services.HoverProvider
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.api.services.ReferencesProvider
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpProvider
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpTriggerKind

/**
 * Configuration for an LSP language server.
 *
 * @property languageId The primary language the server handles.
 * @property languageIds Every language the server handles. Defaults to just [languageId];
 *   servers such as clangd that cover several languages list them all here so a single
 *   process serves all of them.
 */
public data class LspServerConfig(
    val name: String,
    val languageId: String,
    val command: List<String>,
    val workingDirectory: String? = null,
    val initializationOptions: Map<String, Any?>? = null,
    val languageIds: List<String> = listOf(languageId),
)

/**
 * Callback for diagnostics from LSP server.
 */
public typealias DiagnosticsListener = (uri: String, diagnostics: List<LanguageDiagnostic>) -> Unit

/**
 * Wrapper around an LSP client that provides JetaProg-compatible providers.
 *
 * This class manages the lifecycle of an LSP server and exposes its features
 * as JetaProg language service providers.
 */
public class LspLanguageServer(
    public val config: LspServerConfig,
    private val client: LspClient,
) : Disposable {
    private val documentVersions = mutableMapOf<String, Int>()
    private val documentDiagnostics = mutableMapOf<String, List<su.kidoz.jetaprog.lsp.protocol.LspDiagnostic>>()
    private var diagnosticsListener: DiagnosticsListener? = null

    /**
     * Whether the server is running and initialized.
     */
    public val isRunning: Boolean
        get() = client.isInitialized

    /**
     * Set callback for diagnostics notifications.
     */
    public fun onDiagnostics(listener: DiagnosticsListener) {
        diagnosticsListener = listener
        client.onDiagnostics { params ->
            // Store raw LSP diagnostics for code action context
            documentDiagnostics[params.uri] = params.diagnostics
            val diagnostics = params.diagnostics.map { it.toLanguageDiagnostic() }
            diagnosticsListener?.invoke(params.uri, diagnostics)
        }
    }

    /** Handles workspace edits requested by the language server. */
    public fun onWorkspaceEdit(callback: WorkspaceEditCallback) {
        client.onWorkspaceEdit(callback)
    }

    /**
     * Get stored diagnostics for a document.
     */
    public fun getDiagnostics(uri: String): List<LanguageDiagnostic> =
        documentDiagnostics[uri]?.map { it.toLanguageDiagnostic() } ?: emptyList()

    /**
     * Get raw LSP diagnostics for a document (for code action context).
     */
    private fun getLspDiagnostics(uri: String): List<su.kidoz.jetaprog.lsp.protocol.LspDiagnostic> =
        documentDiagnostics[uri] ?: emptyList()

    /**
     * Get diagnostics that overlap with a given range.
     */
    private fun getDiagnosticsInRange(
        uri: String,
        range: su.kidoz.jetaprog.common.text.TextRange,
    ): List<su.kidoz.jetaprog.lsp.protocol.LspDiagnostic> =
        getLspDiagnostics(uri).filter { diag ->
            val diagRange = diag.range.toTextRange()
            // Check if ranges overlap
            diagRange.start.line <= range.end.line &&
                diagRange.end.line >= range.start.line
        }

    // ========================================================================
    // Document Synchronization
    // ========================================================================

    /**
     * Notify server that a document was opened.
     */
    public suspend fun openDocument(
        uri: String,
        languageId: String,
        content: String,
    ) {
        val version = 1
        documentVersions[uri] = version

        client.didOpen(
            DidOpenTextDocumentParams(
                textDocument =
                    TextDocumentItem(
                        uri = uri,
                        languageId = languageId,
                        version = version,
                        text = content,
                    ),
            ),
        )
    }

    /**
     * Notify server that a document was changed.
     */
    public suspend fun changeDocument(
        uri: String,
        content: String,
    ) {
        val version = (documentVersions[uri] ?: 0) + 1
        documentVersions[uri] = version

        client.didChange(
            DidChangeTextDocumentParams(
                textDocument =
                    VersionedTextDocumentIdentifier(
                        uri = uri,
                        version = version,
                    ),
                contentChanges =
                    listOf(
                        TextDocumentContentChangeEvent(text = content),
                    ),
            ),
        )
    }

    /**
     * Notify server that a document was saved.
     */
    public suspend fun saveDocument(
        uri: String,
        content: String? = null,
    ) {
        client.didSave(
            DidSaveTextDocumentParams(
                textDocument = TextDocumentIdentifier(uri = uri),
                text = content,
            ),
        )
    }

    /**
     * Notify server that a document was closed.
     */
    public suspend fun closeDocument(uri: String) {
        documentVersions.remove(uri)
        client.didClose(
            DidCloseTextDocumentParams(
                textDocument = TextDocumentIdentifier(uri = uri),
            ),
        )
    }

    // ========================================================================
    // Provider Factories
    // ========================================================================

    /**
     * Create a completion provider backed by this LSP server.
     */
    public fun createCompletionProvider(): CompletionProvider =
        CompletionProvider { document, position, context ->
            val params =
                CompletionParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    position = position.toLspPosition(),
                    context =
                        CompletionContext(
                            triggerKind =
                                when (context.triggerKind) {
                                    CompletionTriggerKind.Invoked -> 1
                                    CompletionTriggerKind.TriggerCharacter -> 2
                                    CompletionTriggerKind.TriggerForIncompleteCompletions -> 3
                                },
                            triggerCharacter = context.triggerCharacter?.toString(),
                        ),
                )

            // Items carry a resolve handle only when the server offers to complete them lazily.
            val resolvable = client.serverCapabilities?.completionProvider?.resolveProvider == true
            client.completion(params)?.toCompletionList(providerId = config.name.takeIf { resolvable })
                ?: CompletionList(emptyList(), false)
        }

    /**
     * Fills in the parts of [item] the server left for `completionItem/resolve`, or
     * returns null when the item did not come from this server or it has nothing to add.
     */
    public suspend fun resolveCompletion(item: CompletionItem): CompletionItem? {
        if (!isRunning || item.providerId != config.name) return null
        val lspItem = item.toLspCompletionItem() ?: return null
        val resolved = client.resolveCompletionItem(lspItem) ?: return null
        return item.resolvedWith(resolved.toCompletionItem())
    }

    /**
     * Create a hover provider backed by this LSP server.
     */
    public fun createHoverProvider(): HoverProvider =
        HoverProvider { document, position ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    position = position.toLspPosition(),
                )

            client.hover(params)?.toHover()
        }

    /**
     * Create a signature help provider backed by this LSP server.
     */
    public fun createSignatureHelpProvider(): SignatureHelpProvider =
        SignatureHelpProvider { document, position, context ->
            val params =
                SignatureHelpParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    position = position.toLspPosition(),
                    context =
                        su.kidoz.jetaprog.lsp.protocol.SignatureHelpContext(
                            triggerKind =
                                when (context.triggerKind) {
                                    SignatureHelpTriggerKind.Invoked -> 1
                                    SignatureHelpTriggerKind.TriggerCharacter -> 2
                                    SignatureHelpTriggerKind.ContentChange -> 3
                                },
                            triggerCharacter = context.triggerCharacter?.toString(),
                            isRetrigger = context.isRetrigger,
                        ),
                )

            client.signatureHelp(params)?.toSignatureHelp()
        }

    /**
     * Create a definition provider backed by this LSP server.
     */
    public fun createDefinitionProvider(): DefinitionProvider =
        DefinitionProvider { document, position ->
            val params =
                TextDocumentPositionParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    position = position.toLspPosition(),
                )

            client.definition(params)?.map { it.toLocation() } ?: emptyList()
        }

    /** Characters the server asked to have completion triggered on, from its capabilities. */
    public val completionTriggerCharacters: Set<Char>
        get() =
            client.serverCapabilities
                ?.completionProvider
                ?.triggerCharacters
                .toCharacterSet()

    /** Characters that should request or refresh signature help, from the server's capabilities. */
    public val signatureHelpTriggerCharacters: Set<Char>
        get() {
            val options = client.serverCapabilities?.signatureHelpProvider ?: return emptySet()
            return options.triggerCharacters.toCharacterSet() + options.retriggerCharacters.toCharacterSet()
        }

    // Trigger characters are specified as strings; only single characters can be typed.
    private fun List<String>?.toCharacterSet(): Set<Char> = orEmpty().mapNotNullTo(mutableSetOf()) { it.singleOrNull() }

    /** Where the type of the symbol at [position] is declared, per the server; empty when unknown. */
    public suspend fun typeDefinitionLocations(
        uri: String,
        position: TextPosition,
    ): List<LspLocation> {
        if (!isRunning) return emptyList()
        return client.typeDefinition(positionParams(uri, position)).orEmpty()
    }

    /** Implementations of the symbol at [position], per the server; empty when unknown. */
    public suspend fun implementationLocations(
        uri: String,
        position: TextPosition,
    ): List<LspLocation> {
        if (!isRunning) return emptyList()
        return client.implementation(positionParams(uri, position)).orEmpty()
    }

    private fun positionParams(
        uri: String,
        position: TextPosition,
    ) = TextDocumentPositionParams(textDocument = TextDocumentIdentifier(uri), position = position.toLspPosition())

    /** Structure of the open document at [uri] as the server sees it; empty when it has none. */
    public suspend fun documentSymbols(uri: String): List<LspDocumentSymbol> {
        if (!isRunning) return emptyList()
        return client.documentSymbols(DocumentSymbolParams(TextDocumentIdentifier(uri))) ?: emptyList()
    }

    /** Workspace-wide symbols matching [query]; empty until the server is initialized. */
    public suspend fun workspaceSymbols(query: String): List<LspSymbolInformation> {
        if (!isRunning) return emptyList()
        return client.workspaceSymbols(WorkspaceSymbolParams(query)) ?: emptyList()
    }

    /**
     * Create a references provider backed by this LSP server.
     */
    public fun createReferencesProvider(): ReferencesProvider =
        ReferencesProvider { document, position, includeDeclaration ->
            val params =
                ReferenceParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    position = position.toLspPosition(),
                    context = ReferenceContext(includeDeclaration = includeDeclaration),
                )

            client.references(params)?.map { it.toLocation() } ?: emptyList()
        }

    /**
     * Create a formatting provider backed by this LSP server.
     */
    public fun createFormattingProvider(): FormattingProvider =
        FormattingProvider { document, options ->
            val params =
                DocumentFormattingParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    options =
                        LspFormattingOptions(
                            tabSize = options.tabSize,
                            insertSpaces = options.insertSpaces,
                            trimTrailingWhitespace = options.trimTrailingWhitespace,
                            insertFinalNewline = options.insertFinalNewline,
                        ),
                )

            client.formatting(params)?.map { it.toTextEdit() } ?: emptyList()
        }

    /**
     * Create a code action provider backed by this LSP server.
     */
    public fun createCodeActionProvider(): CodeActionProvider =
        CodeActionProvider { document, range, _ ->
            // Get diagnostics that overlap with the requested range
            val diagnosticsInRange = getDiagnosticsInRange(document.uri.value, range)

            val params =
                CodeActionParams(
                    textDocument = TextDocumentIdentifier(document.uri.value),
                    range = range.toLspRange(),
                    context =
                        CodeActionContext(
                            diagnostics = diagnosticsInRange,
                            only = null,
                        ),
                )

            client.codeActions(params)?.map { it.toCodeAction() } ?: emptyList()
        }

    override fun dispose() {
        // Client lifecycle is managed by LanguageServerManager
    }
}

/**
 * Manager for LSP language servers.
 *
 * This class handles the lifecycle of multiple LSP servers and provides
 * a centralized interface for starting, stopping, and querying servers.
 */
public expect class LanguageServerManager() {
    /**
     * Start an LSP server with the given configuration.
     */
    public suspend fun startServer(
        config: LspServerConfig,
        clientConfig: LspClientConfig,
    ): LspLanguageServer

    /**
     * Stop and remove a server.
     */
    public suspend fun stopServer(name: String)

    /**
     * Get a running server by name.
     */
    public fun getServer(name: String): LspLanguageServer?

    /**
     * Get all running servers.
     */
    public fun getAllServers(): List<LspLanguageServer>

    /**
     * Stop all servers.
     */
    public suspend fun stopAll()
}
