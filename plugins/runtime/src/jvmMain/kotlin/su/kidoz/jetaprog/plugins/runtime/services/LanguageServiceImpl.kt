package su.kidoz.jetaprog.plugins.runtime.services

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.DisposableCollection
import su.kidoz.jetaprog.editor.language.LanguageDefinition
import su.kidoz.jetaprog.editor.language.LanguageDefinitionRegistry
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.CodeActionProvider
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.DefinitionProvider
import su.kidoz.jetaprog.plugins.api.services.DiagnosticCollection
import su.kidoz.jetaprog.plugins.api.services.FormattingProvider
import su.kidoz.jetaprog.plugins.api.services.HoverProvider
import su.kidoz.jetaprog.plugins.api.services.LanguageClient
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig
import su.kidoz.jetaprog.plugins.api.services.LanguageService
import su.kidoz.jetaprog.plugins.api.services.ReferencesProvider
import su.kidoz.jetaprog.plugins.api.services.SignatureHelpProvider
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.plugins.support.LanguageServerManager
import su.kidoz.jetaprog.plugins.support.LspServerConfig

/**
 * Implementation of LanguageService that delegates to LanguageRegistry.
 *
 * This bridges the plugin API with the underlying language support infrastructure.
 */
public class LanguageServiceImpl(
    private val languageRegistry: LanguageRegistry,
    private val languageServerManager: LanguageServerManager,
    private val workspacePath: String,
) : LanguageService {
    private val diagnosticCollections = mutableListOf<DiagnosticCollectionImpl>()

    override fun registerLanguage(config: LanguageConfiguration): Disposable =
        LanguageDefinitionRegistry.register(
            LanguageDefinition(
                id = config.id,
                extensions = config.extensions,
                filenames = config.filenames,
                aliases = config.aliases,
                firstLine = config.firstLine,
            ),
        )

    override fun registerCompletionProvider(
        selector: DocumentSelector,
        provider: CompletionProvider,
        triggerCharacters: List<Char>,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerCompletionProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerHoverProvider(
        selector: DocumentSelector,
        provider: HoverProvider,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerHoverProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerSignatureHelpProvider(
        selector: DocumentSelector,
        provider: SignatureHelpProvider,
        triggerCharacters: List<Char>,
        retriggerCharacters: List<Char>,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerSignatureHelpProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerDefinitionProvider(
        selector: DocumentSelector,
        provider: DefinitionProvider,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerDefinitionProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerReferencesProvider(
        selector: DocumentSelector,
        provider: ReferencesProvider,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerReferencesProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerDocumentFormattingProvider(
        selector: DocumentSelector,
        provider: FormattingProvider,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerFormattingProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun registerCodeActionProvider(
        selector: DocumentSelector,
        provider: CodeActionProvider,
    ): Disposable {
        val disposables = DisposableCollection()

        for (languageId in selector.getLanguageIds()) {
            disposables.add(
                languageRegistry.registerCodeActionProvider(
                    languageId = languageId,
                    provider = provider,
                ),
            )
        }

        return disposables
    }

    override fun createDiagnosticCollection(name: String): DiagnosticCollection {
        val collection = DiagnosticCollectionImpl(name)
        diagnosticCollections.add(collection)
        return collection
    }

    override suspend fun startLanguageServer(config: LanguageServerConfig): LanguageClient {
        val languageIds = config.documentSelector.getLanguageIds().ifEmpty { listOf("unknown") }
        val lspConfig =
            LspServerConfig(
                name = config.name,
                languageId = languageIds.first(),
                command = config.command + config.args,
                workingDirectory = config.workingDirectory,
                initializationOptions = config.initializationOptions,
                languageIds = languageIds,
            )

        val rootUri = "file://$workspacePath"
        val workspaceFolders = listOf(workspacePath)

        languageRegistry.registerLspServer(lspConfig, rootUri, workspaceFolders)

        return LanguageClientImpl(config.name, languageRegistry)
    }

    /**
     * Gets the language IDs from a DocumentSelector.
     */
    private fun DocumentSelector.getLanguageIds(): List<String> =
        languages.map { it.value }.ifEmpty {
            // If no specific languages, try to infer from the glob pattern's file part
            listOfNotNull(
                pattern?.let { LanguageDefinitionRegistry.detect(it)?.value },
            )
        }
}

/**
 * Wrapper for LanguageClient that delegates to the registry.
 */
private class LanguageClientImpl(
    private val serverName: String,
    private val registry: LanguageRegistry,
) : LanguageClient {
    private var running = true

    override val isRunning: Boolean get() = running

    override suspend fun <T> sendRequest(
        method: String,
        params: Any?,
    ): T {
        // Direct LSP requests are not fully supported in this implementation.
        // Language features should be accessed via providers registered with LanguageService.
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    override suspend fun sendNotification(
        method: String,
        params: Any?,
    ) {
        // Direct LSP notifications are handled internally by the registry.
    }

    override suspend fun stop() {
        running = false
        registry.stopLspServer(serverName)
    }
}
