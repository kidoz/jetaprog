package su.kidoz.jetaprog.plugins.support

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lsp.client.LspClientConfig
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspSymbolInformation
import su.kidoz.jetaprog.lsp.protocol.LspWorkspaceEdit
import su.kidoz.jetaprog.lsp.protocol.WorkspaceFolder
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.language.Hover
import su.kidoz.jetaprog.plugins.api.language.Location
import su.kidoz.jetaprog.plugins.api.language.SignatureHelp
import su.kidoz.jetaprog.plugins.api.services.CodeAction
import su.kidoz.jetaprog.plugins.api.services.CodeActionContext
import su.kidoz.jetaprog.plugins.api.services.CodeActionProvider
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.DefinitionProvider
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.FormattingProvider
import su.kidoz.jetaprog.plugins.api.services.HoverProvider
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.api.services.ReferencesProvider
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpContext
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.settings.SettingsService

/**
 * Central registry for language features.
 */

private val logger = KotlinLogging.logger {}

public class LanguageRegistry(
    private val serverManager: LanguageServerManager,
    private val settingsService: SettingsService,
) {
    private val providers = mutableMapOf<String, HybridLanguageProvider>()
    private val lspServers = mutableMapOf<String, LspLanguageServer>()
    private val diagnosticsListeners = mutableListOf<SourcedDiagnosticsListener>()
    private val workspaceEditListeners = mutableListOf<WorkspaceEditListener>()
    private val documentSyncListeners = mutableListOf<DocumentSyncListener>()

    /**
     * Subscribes [listener] to the document lifecycle the editor reports. Embedded
     * servers use this to answer from the live buffer instead of the file on disk.
     */
    public fun addDocumentSyncListener(listener: DocumentSyncListener): Disposable {
        documentSyncListeners.add(listener)
        return Disposable { documentSyncListeners.remove(listener) }
    }

    private suspend fun forEachSyncListener(action: suspend (DocumentSyncListener) -> Unit) {
        documentSyncListeners.toList().forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.warn(e) { "Document sync listener failed: ${e.message}" }
            }
        }
    }

    /**
     * Get or create a hybrid provider for a language.
     */
    private fun getOrCreateProvider(languageId: String): HybridLanguageProvider =
        providers.getOrPut(languageId) {
            HybridLanguageProvider(
                HybridProviderConfig(
                    languageId = languageId,
                    priority = ProviderPriority.InProcessFirst,
                ),
                settingsService,
            )
        }

    // ========================================================================
    // In-Process Provider Registration
    // ========================================================================

    /**
     * Register an in-process completion provider.
     */
    public fun registerCompletionProvider(
        languageId: String,
        provider: CompletionProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerCompletionProvider(provider, priority, selector)

    /**
     * Register an in-process hover provider.
     */
    public fun registerHoverProvider(
        languageId: String,
        provider: HoverProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerHoverProvider(provider, priority, selector)

    /**
     * Register an in-process signature help provider.
     */
    public fun registerSignatureHelpProvider(
        languageId: String,
        provider: SignatureHelpProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerSignatureHelpProvider(provider, priority, selector)

    /**
     * Register an in-process definition provider.
     */
    public fun registerDefinitionProvider(
        languageId: String,
        provider: DefinitionProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerDefinitionProvider(provider, priority, selector)

    /**
     * Register an in-process references provider.
     */
    public fun registerReferencesProvider(
        languageId: String,
        provider: ReferencesProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerReferencesProvider(provider, priority, selector)

    /**
     * Register an in-process formatting provider.
     */
    public fun registerFormattingProvider(
        languageId: String,
        provider: FormattingProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerFormattingProvider(provider, priority, selector)

    /**
     * Register an in-process code action provider.
     */
    public fun registerCodeActionProvider(
        languageId: String,
        provider: CodeActionProvider,
        priority: Int = 0,
        selector: DocumentSelector? = null,
    ): Disposable = getOrCreateProvider(languageId).registerCodeActionProvider(provider, priority, selector)

    // ========================================================================
    // LSP Server Registration
    // ========================================================================

    /**
     * Register and start an LSP language server.
     *
     * The server's features are automatically registered as LSP-backed providers.
     */
    public suspend fun registerLspServer(
        config: LspServerConfig,
        rootUri: String,
        workspaceFolders: List<String> = emptyList(),
    ): Disposable {
        val clientConfig =
            LspClientConfig(
                serverName = config.name,
                rootUri = rootUri,
                workspaceFolders =
                    workspaceFolders.map { path ->
                        WorkspaceFolder(
                            uri = "file://$path",
                            name = path.substringAfterLast('/'),
                        )
                    },
                initializationOptions = config.initializationOptions?.takeIf { it.isNotEmpty() }?.toJsonObject(),
            )

        val server = serverManager.startServer(config, clientConfig)
        lspServers[config.name] = server

        val disposables = mutableListOf<Disposable>()

        // Register LSP-backed providers for every language the server covers.
        for (languageId in config.languageIds) {
            val hybridProvider = getOrCreateProvider(languageId)

            // Register providers with negative priority (fallback to in-process)
            disposables.add(
                hybridProvider.registerLspCompletionProvider(
                    server.createCompletionProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspHoverProvider(
                    server.createHoverProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspSignatureHelpProvider(
                    server.createSignatureHelpProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspDefinitionProvider(
                    server.createDefinitionProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspReferencesProvider(
                    server.createReferencesProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspFormattingProvider(
                    server.createFormattingProvider(),
                    priority = -10,
                ),
            )
            disposables.add(
                hybridProvider.registerLspCodeActionProvider(
                    server.createCodeActionProvider(),
                    priority = -10,
                ),
            )
        }

        // Forward diagnostics, tagged with the server name so diagnostics from multiple
        // servers covering the same language never overwrite each other.
        server.onDiagnostics { uri, diagnostics ->
            diagnosticsListeners.forEach { it.invoke(config.name, uri, diagnostics) }
        }
        server.onWorkspaceEdit { label, edit ->
            workspaceEditListeners.anyApplied { listener -> listener(label, edit) }
        }

        return Disposable {
            disposables.forEach { it.dispose() }
            lspServers.remove(config.name)
            // Note: Server lifecycle is managed separately
        }
    }

    /**
     * Stop an LSP server.
     */
    public suspend fun stopLspServer(name: String) {
        serverManager.stopServer(name)
        lspServers.remove(name)
    }

    /**
     * Whether a running LSP server is registered for the given language.
     */
    public fun hasLspServer(languageId: String): Boolean = lspServers.values.any { languageId in it.config.languageIds }

    /**
     * Structure of the document at [uri] from the external server covering [languageId].
     * Empty when no server covers the language or it has nothing to report.
     */
    public suspend fun provideDocumentSymbols(
        languageId: String,
        uri: String,
    ): List<LspDocumentSymbol> {
        for (server in lspServers.values.filter { languageId in it.config.languageIds }) {
            val symbols = server.querySafely("documentSymbol") { documentSymbols(uri) }
            if (symbols.isNotEmpty()) return symbols
        }
        return emptyList()
    }

    /** Symbols matching [query] from every running external server. */
    public suspend fun searchWorkspaceSymbols(query: String): List<LspSymbolInformation> =
        lspServers.values.flatMap { server -> server.querySafely("workspaceSymbol") { workspaceSymbols(query) } }

    private suspend fun <T> LspLanguageServer.querySafely(
        feature: String,
        query: suspend LspLanguageServer.() -> List<T>,
    ): List<T> =
        try {
            query()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.warn(e) { "${config.name} $feature failed: ${e.message}" }
            emptyList()
        }

    /**
     * Add a diagnostics listener. The listener receives the name of the language server
     * that produced the diagnostics, so consumers can keep per-source collections.
     */
    public fun onDiagnostics(listener: SourcedDiagnosticsListener): Disposable {
        diagnosticsListeners.add(listener)
        return Disposable { diagnosticsListeners.remove(listener) }
    }

    /** Adds a listener that can apply multi-document edits requested by LSP servers. */
    public fun onWorkspaceEdit(listener: WorkspaceEditListener): Disposable {
        workspaceEditListeners.add(listener)
        return Disposable { workspaceEditListeners.remove(listener) }
    }

    // ========================================================================
    // Document Synchronization
    // ========================================================================

    /**
     * Notify all relevant LSP servers that a document was opened.
     */
    public suspend fun notifyDocumentOpened(
        uri: String,
        languageId: String,
        content: String,
    ) {
        lspServers.values
            .filter { languageId in it.config.languageIds }
            .forEach { it.openDocument(uri, languageId, content) }
        forEachSyncListener { it.documentOpened(uri, languageId, content) }
    }

    /**
     * Notify all relevant LSP servers that a document was changed.
     */
    public suspend fun notifyDocumentChanged(
        uri: String,
        languageId: String,
        content: String,
    ) {
        lspServers.values
            .filter { languageId in it.config.languageIds }
            .forEach { it.changeDocument(uri, content) }
        forEachSyncListener { it.documentChanged(uri, languageId, content) }
    }

    /**
     * Notify all relevant LSP servers that a document was saved.
     */
    public suspend fun notifyDocumentSaved(
        uri: String,
        languageId: String,
        content: String? = null,
    ) {
        lspServers.values
            .filter { languageId in it.config.languageIds }
            .forEach { it.saveDocument(uri, content) }
        forEachSyncListener { it.documentSaved(uri, languageId, content) }
    }

    /**
     * Notify all relevant LSP servers that a document was closed.
     */
    public suspend fun notifyDocumentClosed(
        uri: String,
        languageId: String,
    ) {
        lspServers.values
            .filter { languageId in it.config.languageIds }
            .forEach { it.closeDocument(uri) }
        forEachSyncListener { it.documentClosed(uri, languageId) }
    }

    // ========================================================================
    // Feature Queries
    // ========================================================================

    /**
     * Get completions for a document position.
     */
    public suspend fun provideCompletions(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList {
        val provider =
            providers[document.languageId.value]
                ?: return CompletionList(emptyList(), false)

        return provider.provideCompletions(document, position, context)
    }

    /**
     * Get hover information for a document position.
     */
    public suspend fun provideHover(
        document: TextDocument,
        position: TextPosition,
    ): Hover? {
        val provider = providers[document.languageId.value] ?: return null
        return provider.provideHover(document, position)
    }

    /**
     * Get signature help for a document position.
     */
    public suspend fun provideSignatureHelp(
        document: TextDocument,
        position: TextPosition,
        context: SignatureHelpContext,
    ): SignatureHelp? {
        val provider = providers[document.languageId.value] ?: return null
        return provider.provideSignatureHelp(document, position, context)
    }

    /**
     * Get definition locations for a symbol.
     */
    public suspend fun provideDefinition(
        document: TextDocument,
        position: TextPosition,
    ): List<Location> {
        val provider = providers[document.languageId.value] ?: return emptyList()
        return provider.provideDefinition(document, position)
    }

    /**
     * Get reference locations for a symbol.
     */
    public suspend fun provideReferences(
        document: TextDocument,
        position: TextPosition,
        includeDeclaration: Boolean = true,
    ): List<Location> {
        val provider = providers[document.languageId.value] ?: return emptyList()
        return provider.provideReferences(document, position, includeDeclaration)
    }

    /**
     * Get formatting edits for a document.
     */
    public suspend fun provideFormatting(
        document: TextDocument,
        options: FormattingOptions,
    ): List<TextEdit> {
        val provider = providers[document.languageId.value] ?: return emptyList()
        return provider.provideFormatting(document, options)
    }

    /**
     * Get code actions for a range.
     */
    public suspend fun provideCodeActions(
        document: TextDocument,
        range: TextRange,
        context: CodeActionContext,
    ): List<CodeAction> {
        val provider = providers[document.languageId.value] ?: return emptyList()
        return provider.provideCodeActions(document, range, context)
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Stop all LSP servers and clean up.
     */
    public suspend fun shutdown() {
        serverManager.stopAll()
        lspServers.clear()
        providers.clear()
        diagnosticsListeners.clear()
        workspaceEditListeners.clear()
    }
}

/** Receives diagnostics tagged with the language-server name that produced them. */
public typealias SourcedDiagnosticsListener = (
    source: String,
    uri: String,
    diagnostics: List<LanguageDiagnostic>,
) -> Unit

/** Applies an LSP workspace edit and reports whether it succeeded. */
public typealias WorkspaceEditListener = suspend (label: String?, edit: LspWorkspaceEdit) -> Boolean

private suspend fun List<WorkspaceEditListener>.anyApplied(
    apply: suspend (WorkspaceEditListener) -> Boolean,
): Boolean {
    for (listener in this) {
        if (apply(listener)) return true
    }
    return false
}

/**
 * Converts plugin-supplied initialization options into the JSON payload sent with
 * the LSP `initialize` request.
 */
private fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, value) -> value.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
