package su.kidoz.jetaprog.plugins.support

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.plugins.api.language.CompletionItem
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
import su.kidoz.jetaprog.settings.model.CompletionProviderPreference
import su.kidoz.jetaprog.settings.model.LanguageConfig

/**
 * Provider source indicating where a feature is provided from.
 */
public enum class ProviderSource {
    /** In-process provider (fast path). */
    InProcess,

    /** External LSP server. */
    Lsp,
}

/**
 * Provider priority for routing decisions.
 */
public enum class ProviderPriority {
    /** Use in-process first, fallback to LSP. */
    InProcessFirst,

    /** Use LSP first, fallback to in-process. */
    LspFirst,

    /** Only use in-process. */
    InProcessOnly,

    /** Only use LSP. */
    LspOnly,
}

/**
 * Configuration for a hybrid language provider.
 */
public data class HybridProviderConfig(
    val languageId: String,
    val priority: ProviderPriority = ProviderPriority.InProcessFirst,
    val features: Set<LanguageFeature> = LanguageFeature.entries.toSet(),
)

/**
 * Supported language features.
 */
public enum class LanguageFeature {
    Completion,
    Hover,
    SignatureHelp,
    Definition,
    References,
    DocumentSymbol,
    CodeAction,
    Formatting,
    Diagnostics,
}

/**
 * Registered provider with metadata.
 */
public data class RegisteredProvider<T>(
    val provider: T,
    val source: ProviderSource,
    val priority: Int = 0,
)

/**
 * Hybrid language provider that routes requests to in-process or LSP providers.
 *
 * This class manages multiple providers for a single language and routes
 * requests based on priority, availability, and configuration.
 */
public class HybridLanguageProvider(
    private val config: HybridProviderConfig,
    private val settingsService: SettingsService,
) {
    private val completionProviders = mutableListOf<RegisteredProvider<CompletionProvider>>()
    private val hoverProviders = mutableListOf<RegisteredProvider<HoverProvider>>()
    private val signatureHelpProviders = mutableListOf<RegisteredProvider<SignatureHelpProvider>>()
    private val definitionProviders = mutableListOf<RegisteredProvider<DefinitionProvider>>()
    private val referencesProviders = mutableListOf<RegisteredProvider<ReferencesProvider>>()
    private val formattingProviders = mutableListOf<RegisteredProvider<FormattingProvider>>()
    private val codeActionProviders = mutableListOf<RegisteredProvider<CodeActionProvider>>()

    /**
     * Register an in-process completion provider.
     */
    public fun registerCompletionProvider(
        provider: CompletionProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        completionProviders.add(registered)
        completionProviders.sortByDescending { it.priority }
        return Disposable { completionProviders.remove(registered) }
    }

    /**
     * Register an in-process hover provider.
     */
    public fun registerHoverProvider(
        provider: HoverProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        hoverProviders.add(registered)
        hoverProviders.sortByDescending { it.priority }
        return Disposable { hoverProviders.remove(registered) }
    }

    /**
     * Register an in-process signature help provider.
     */
    public fun registerSignatureHelpProvider(
        provider: SignatureHelpProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        signatureHelpProviders.add(registered)
        signatureHelpProviders.sortByDescending { it.priority }
        return Disposable { signatureHelpProviders.remove(registered) }
    }

    /**
     * Register an in-process definition provider.
     */
    public fun registerDefinitionProvider(
        provider: DefinitionProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        definitionProviders.add(registered)
        definitionProviders.sortByDescending { it.priority }
        return Disposable { definitionProviders.remove(registered) }
    }

    /**
     * Register an in-process references provider.
     */
    public fun registerReferencesProvider(
        provider: ReferencesProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        referencesProviders.add(registered)
        referencesProviders.sortByDescending { it.priority }
        return Disposable { referencesProviders.remove(registered) }
    }

    /**
     * Register an in-process formatting provider.
     */
    public fun registerFormattingProvider(
        provider: FormattingProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        formattingProviders.add(registered)
        formattingProviders.sortByDescending { it.priority }
        return Disposable { formattingProviders.remove(registered) }
    }

    /**
     * Register an in-process code action provider.
     */
    public fun registerCodeActionProvider(
        provider: CodeActionProvider,
        priority: Int = 0,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.InProcess, priority)
        codeActionProviders.add(registered)
        codeActionProviders.sortByDescending { it.priority }
        return Disposable { codeActionProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed completion provider.
     */
    public fun registerLspCompletionProvider(
        provider: CompletionProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        completionProviders.add(registered)
        completionProviders.sortByDescending { it.priority }
        return Disposable { completionProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed hover provider.
     */
    public fun registerLspHoverProvider(
        provider: HoverProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        hoverProviders.add(registered)
        hoverProviders.sortByDescending { it.priority }
        return Disposable { hoverProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed signature help provider.
     */
    public fun registerLspSignatureHelpProvider(
        provider: SignatureHelpProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        signatureHelpProviders.add(registered)
        signatureHelpProviders.sortByDescending { it.priority }
        return Disposable { signatureHelpProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed definition provider.
     */
    public fun registerLspDefinitionProvider(
        provider: DefinitionProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        definitionProviders.add(registered)
        definitionProviders.sortByDescending { it.priority }
        return Disposable { definitionProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed references provider.
     */
    public fun registerLspReferencesProvider(
        provider: ReferencesProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        referencesProviders.add(registered)
        referencesProviders.sortByDescending { it.priority }
        return Disposable { referencesProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed formatting provider.
     */
    public fun registerLspFormattingProvider(
        provider: FormattingProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        formattingProviders.add(registered)
        formattingProviders.sortByDescending { it.priority }
        return Disposable { formattingProviders.remove(registered) }
    }

    /**
     * Register an LSP-backed code action provider.
     */
    public fun registerLspCodeActionProvider(
        provider: CodeActionProvider,
        priority: Int = -1,
    ): Disposable {
        val registered = RegisteredProvider(provider, ProviderSource.Lsp, priority)
        codeActionProviders.add(registered)
        codeActionProviders.sortByDescending { it.priority }
        return Disposable { codeActionProviders.remove(registered) }
    }

    // ========================================================================
    // Provider Invocation
    // ========================================================================

    /**
     * Get completions from the appropriate provider based on settings.
     */
    public suspend fun provideCompletions(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList {
        val languageConfig =
            settingsService.getCurrentSettings().languages.languages[config.languageId] ?: LanguageConfig()
        val preference = languageConfig.completionPreference

        val nativeProviders = completionProviders.filter { it.source == ProviderSource.InProcess }
        val lspProviders = completionProviders.filter { it.source == ProviderSource.Lsp }

        val allItems = mutableListOf<CompletionItem>()
        var allIncomplete = false

        val providersToRun: List<RegisteredProvider<CompletionProvider>> =
            when (preference) {
                CompletionProviderPreference.Native -> nativeProviders
                CompletionProviderPreference.Lsp -> lspProviders
                CompletionProviderPreference.Hybrid -> completionProviders // Both
            }

        for (registered in providersToRun.sortedByDescending { it.priority }) {
            try {
                val result = registered.provider.provideCompletionItems(document, position, context)
                if (result != null) {
                    allItems.addAll(result.items)
                    if (result.isIncomplete) {
                        allIncomplete = true
                    }
                }
            } catch (e: Exception) {
                // TODO: Log exception
                // Continue to next provider
            }
        }

        // For Hybrid, we merge. For Native/LSP, we also merge all of that type.
        // The old behavior of returning the first result is changed to be more robust.
        return CompletionList(allItems.distinctBy { it.label }, allIncomplete)
    }

    /**
     * Get hover information from the appropriate provider.
     */
    public suspend fun provideHover(
        document: TextDocument,
        position: TextPosition,
    ): Hover? {
        val providers = getOrderedProviders(hoverProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideHover(document, position)
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return null
    }

    /**
     * Get signature help from the appropriate provider.
     */
    public suspend fun provideSignatureHelp(
        document: TextDocument,
        position: TextPosition,
        context: SignatureHelpContext,
    ): SignatureHelp? {
        val providers = getOrderedProviders(signatureHelpProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideSignatureHelp(document, position, context)
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return null
    }

    /**
     * Get definition locations from the appropriate provider.
     */
    public suspend fun provideDefinition(
        document: TextDocument,
        position: TextPosition,
    ): List<Location> {
        val providers = getOrderedProviders(definitionProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideDefinition(document, position)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return emptyList()
    }

    /**
     * Get reference locations from the appropriate provider.
     */
    public suspend fun provideReferences(
        document: TextDocument,
        position: TextPosition,
        includeDeclaration: Boolean,
    ): List<Location> {
        val providers = getOrderedProviders(referencesProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideReferences(document, position, includeDeclaration)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return emptyList()
    }

    /**
     * Get formatting edits from the appropriate provider.
     */
    public suspend fun provideFormatting(
        document: TextDocument,
        options: FormattingOptions,
    ): List<TextEdit> {
        val providers = getOrderedProviders(formattingProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideDocumentFormattingEdits(document, options)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return emptyList()
    }

    /**
     * Get code actions from the appropriate provider.
     */
    public suspend fun provideCodeActions(
        document: TextDocument,
        range: TextRange,
        context: CodeActionContext,
    ): List<CodeAction> {
        val providers = getOrderedProviders(codeActionProviders)

        for (registered in providers) {
            try {
                val result = registered.provider.provideCodeActions(document, range, context)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return emptyList()
    }

    private fun <T> getOrderedProviders(providers: List<RegisteredProvider<T>>): List<RegisteredProvider<T>> =
        when (config.priority) {
            ProviderPriority.InProcessFirst -> {
                providers.sortedWith(
                    compareByDescending<RegisteredProvider<T>> { it.source == ProviderSource.InProcess }
                        .thenByDescending { it.priority },
                )
            }

            ProviderPriority.LspFirst -> {
                providers.sortedWith(
                    compareByDescending<RegisteredProvider<T>> { it.source == ProviderSource.Lsp }
                        .thenByDescending { it.priority },
                )
            }

            ProviderPriority.InProcessOnly -> {
                providers.filter { it.source == ProviderSource.InProcess }
            }

            ProviderPriority.LspOnly -> {
                providers.filter { it.source == ProviderSource.Lsp }
            }
        }
}
