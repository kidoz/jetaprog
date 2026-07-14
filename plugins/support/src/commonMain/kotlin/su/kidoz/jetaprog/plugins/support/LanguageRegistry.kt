package su.kidoz.jetaprog.plugins.support

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lsp.client.LspClientConfig
import su.kidoz.jetaprog.lsp.protocol.WorkspaceFolder
import su.kidoz.jetaprog.plugins.api.language.CompletionList
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
import su.kidoz.jetaprog.plugins.api.services.ReferencesProvider
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpContext
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.settings.SettingsService

/**
 * Central registry for language features.
 */

public class LanguageRegistry(
    private val serverManager: LanguageServerManager,
    private val settingsService: SettingsService,
) {
    private val providers = mutableMapOf<String, HybridLanguageProvider>()
    private val lspServers = mutableMapOf<String, LspLanguageServer>()
    private val diagnosticsListeners = mutableListOf<DiagnosticsListener>()

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
    ): Disposable = getOrCreateProvider(languageId).registerCompletionProvider(provider, priority)

    /**
     * Register an in-process hover provider.
     */
    public fun registerHoverProvider(
        languageId: String,
        provider: HoverProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerHoverProvider(provider, priority)

    /**
     * Register an in-process signature help provider.
     */
    public fun registerSignatureHelpProvider(
        languageId: String,
        provider: SignatureHelpProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerSignatureHelpProvider(provider, priority)

    /**
     * Register an in-process definition provider.
     */
    public fun registerDefinitionProvider(
        languageId: String,
        provider: DefinitionProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerDefinitionProvider(provider, priority)

    /**
     * Register an in-process references provider.
     */
    public fun registerReferencesProvider(
        languageId: String,
        provider: ReferencesProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerReferencesProvider(provider, priority)

    /**
     * Register an in-process formatting provider.
     */
    public fun registerFormattingProvider(
        languageId: String,
        provider: FormattingProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerFormattingProvider(provider, priority)

    /**
     * Register an in-process code action provider.
     */
    public fun registerCodeActionProvider(
        languageId: String,
        provider: CodeActionProvider,
        priority: Int = 0,
    ): Disposable = getOrCreateProvider(languageId).registerCodeActionProvider(provider, priority)

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
            )

        val server = serverManager.startServer(config, clientConfig)
        lspServers[config.name] = server

        // Register LSP-backed providers
        val hybridProvider = getOrCreateProvider(config.languageId)

        val disposables = mutableListOf<Disposable>()

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

        // Forward diagnostics
        server.onDiagnostics { uri, diagnostics ->
            diagnosticsListeners.forEach { it.invoke(uri, diagnostics) }
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
    public fun hasLspServer(languageId: String): Boolean = lspServers.values.any { it.config.languageId == languageId }

    /**
     * Add a diagnostics listener.
     */
    public fun onDiagnostics(listener: DiagnosticsListener): Disposable {
        diagnosticsListeners.add(listener)
        return Disposable { diagnosticsListeners.remove(listener) }
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
            .filter { it.config.languageId == languageId }
            .forEach { it.openDocument(uri, languageId, content) }
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
            .filter { it.config.languageId == languageId }
            .forEach { it.changeDocument(uri, content) }
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
            .filter { it.config.languageId == languageId }
            .forEach { it.saveDocument(uri, content) }
    }

    /**
     * Notify all relevant LSP servers that a document was closed.
     */
    public suspend fun notifyDocumentClosed(
        uri: String,
        languageId: String,
    ) {
        lspServers.values
            .filter { it.config.languageId == languageId }
            .forEach { it.closeDocument(uri) }
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
    }
}
