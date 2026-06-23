package su.kidoz.jetaprog.plugins.kotlin

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageClient
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinPsiAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.lint.KotlinStyleProvider
import su.kidoz.jetaprog.plugins.kotlin.lint.KotlinSyntaxLintProvider
import su.kidoz.jetaprog.plugins.kotlin.providers.ConfigurableKotlinCompletionProvider
import su.kidoz.jetaprog.plugins.kotlin.providers.KotlinCompletionProvider
import su.kidoz.jetaprog.plugins.kotlin.providers.KotlinPsiCompletionProvider
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry
import java.io.File

private val logger = KotlinLogging.logger {}

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
    private var kotlinLanguageClient: LanguageClient? = null
    private var psiAnalyzer: KotlinPsiAnalyzer? = null

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

        // PSI-backed analyzer (parser-level completion + syntax diagnostics)
        val analyzer = KotlinPsiAnalyzer()
        psiAnalyzer = analyzer

        // Register completion provider
        val selector = DocumentSelector(languages = listOf(LanguageId.KOTLIN))
        val nativeProvider = KotlinCompletionProvider(service)
        val configurableProvider =
            ConfigurableKotlinCompletionProvider(nativeProvider, KotlinPsiCompletionProvider(analyzer))

        startKotlinLanguageServerIfAvailable(workspacePath, selector)

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

        // Register Kotlin syntax diagnostics (parser-backed)
        context.lint.registerProvider(KotlinSyntaxLintProvider(analyzer)).also { context.subscriptions.add(it) }

        logger.info { "Kotlin plugin activated" }
    }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Kotlin plugin" }

        kotlinLanguageService?.dispose()
        kotlinLanguageClient?.stop()
        psiAnalyzer?.dispose()
        kotlinLanguageService = null
        kotlinFormatter = null
        kotlinLanguageClient = null
        psiAnalyzer = null

        logger.info { "Kotlin plugin deactivated" }
    }

    private suspend fun startKotlinLanguageServerIfAvailable(
        workspacePath: String,
        selector: DocumentSelector,
    ) {
        val configuredCommand = System.getenv(KOTLIN_LSP_ENV)?.takeIf { it.isNotBlank() }
        val command = configuredCommand ?: DEFAULT_KOTLIN_LSP_COMMAND
        if (!isCommandAvailable(command)) {
            logger.info { "Kotlin LSP command '$command' is not available; using native Kotlin providers only" }
            return
        }

        runCatching {
            context.languages.startLanguageServer(
                LanguageServerConfig(
                    name = DEFAULT_KOTLIN_LSP_COMMAND,
                    command = listOf(command),
                    documentSelector = selector,
                    workingDirectory = workspacePath,
                ),
            )
        }.onSuccess { client ->
            kotlinLanguageClient = client
            logger.info { "Kotlin LSP started with command '$command'" }
        }.onFailure { error ->
            logger.warn(error) { "Failed to start Kotlin LSP with command '$command'" }
        }
    }

    private fun isCommandAvailable(command: String): Boolean {
        val file = File(command)
        if (file.isAbsolute || command.contains(File.separatorChar)) {
            return file.canExecute()
        }

        val path = System.getenv("PATH").orEmpty()
        if (path.isBlank()) return false

        val executableNames =
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                val extensions = System.getenv("PATHEXT")?.split(File.pathSeparator).orEmpty()
                listOf(command) + extensions.map { extension -> "$command$extension" }
            } else {
                listOf(command)
            }

        return path
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .any { directory ->
                executableNames.any { executableName ->
                    File(directory, executableName).canExecute()
                }
            }
    }

    private companion object {
        private const val DEFAULT_KOTLIN_LSP_COMMAND = "kotlin-language-server"
        private const val KOTLIN_LSP_ENV = "JETAPROG_KOTLIN_LSP"
    }
}
