package su.kidoz.jetaprog.plugins.kotlin

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.kotlin.lint.KotlinStyleProvider
import su.kidoz.jetaprog.plugins.kotlin.providers.ConfigurableKotlinCompletionProvider
import su.kidoz.jetaprog.plugins.kotlin.providers.KotlinCompletionProvider
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry

private val logger = KotlinLogging.logger {}

/**
 * A dummy completion provider that returns an empty list.
 * Used as a placeholder for the real LSP-based provider.
 */
private val dummyLspProvider = CompletionProvider { _, _, _ -> CompletionList(emptyList(), false) }

/**
 * Kotlin language support plugin for JetaProg.
 *
 * Provides:
 * - Code completion
 * - Hover information
 * - Go to definition
 * - Find references
 * - Code formatting
 * - Lint rules (style checks)
 */
public class KotlinPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = "su.kidoz.jetaprog.kotlin",
                name = "Kotlin Language Support",
                version = "1.0.0",
                description = "Kotlin language support including code completion, navigation, and formatting",
                activationEvents = listOf("onLanguage:kotlin"),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "kotlin",
                                    extensions = listOf(".kt", ".kts"),
                                    aliases = listOf("Kotlin", "kt"),
                                ),
                            ),
                    ),
            ),
    ) {
    private var kotlinLanguageService: KotlinLanguageService? = null
    private var kotlinFormatter: KotlinFormatter? = null

    override suspend fun onActivate() {
        logger.info { "Activating Kotlin plugin" }

        val workspacePath = context.workspace.rootPath ?: return

        // Register language configuration
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.KOTLIN,
                    extensions = listOf(".kt", ".kts"),
                    aliases = listOf("Kotlin", "kt"),
                ),
            ).also { context.subscriptions.add(it) }

        // Initialize the Kotlin language service
        val service = KotlinLanguageService()
        service.initialize(workspacePath)
        kotlinLanguageService = service

        // Register completion provider
        val selector = DocumentSelector(languages = listOf(LanguageId.KOTLIN))
        val nativeProvider = KotlinCompletionProvider(service)
        val configurableProvider = ConfigurableKotlinCompletionProvider(nativeProvider, dummyLspProvider)

        context.languages
            .registerCompletionProvider(
                selector = selector,
                provider = configurableProvider,
                triggerCharacters = listOf('.', ':', '@'),
            ).also { context.subscriptions.add(it) }

        // Register hover provider
        context.languages
            .registerHoverProvider(
                selector = selector,
                provider = { document, position ->
                    service.getHover(document.uri.value.removePrefix("file://"), position)
                },
            ).also { context.subscriptions.add(it) }

        // Register definition provider
        context.languages
            .registerDefinitionProvider(
                selector = selector,
                provider = { document, position ->
                    val location =
                        service.goToDefinition(
                            document.uri.value.removePrefix("file://"),
                            position,
                            document.getText(),
                        )
                    location?.let {
                        listOf(
                            su.kidoz.jetaprog.plugins.api.language.Location(
                                uri = "file://${it.filePath}",
                                range = it.range,
                            ),
                        )
                    } ?: emptyList()
                },
            ).also { context.subscriptions.add(it) }

        // Register formatting provider
        val formatter = KotlinFormatter()
        kotlinFormatter = formatter
        FormatterRegistry.register(formatter)

        context.languages
            .registerDocumentFormattingProvider(
                selector = selector,
                provider = { document, options ->
                    val result = formatter.format(document.getText(), options)
                    when (result) {
                        is su.kidoz.jetaprog.plugins.support.formatters.FormattingResult.Success -> result.edits
                        is su.kidoz.jetaprog.plugins.support.formatters.FormattingResult.Failure -> emptyList()
                    }
                },
            ).also { context.subscriptions.add(it) }

        // Register Kotlin style lint provider
        val lintProvider = KotlinStyleProvider()
        context.lint.registerProvider(lintProvider).also { context.subscriptions.add(it) }

        logger.info { "Kotlin plugin activated" }
    }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Kotlin plugin" }

        kotlinLanguageService?.dispose()
        kotlinLanguageService = null
        kotlinFormatter = null

        logger.info { "Kotlin plugin deactivated" }
    }
}
