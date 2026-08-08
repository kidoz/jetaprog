package su.kidoz.jetaprog.plugins.go

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
import su.kidoz.jetaprog.plugins.support.formatters.ExternalStdioFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult

private val logger = KotlinLogging.logger {}

/**
 * Go language support backed by gopls and gofmt when those tools are installed.
 *
 * @property languageServerCommand gopls launcher command.
 * @property formatterCommand gofmt launcher command.
 */
public class GoPlugin(
    private val languageServerCommand: String = DEFAULT_LANGUAGE_SERVER_COMMAND,
    private val formatterCommand: String = DEFAULT_FORMATTER_COMMAND,
) : BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Go Language Support",
                version = "1.0.0",
                description = "Go support with gopls navigation, diagnostics, completion, and gofmt formatting",
                activationEvents =
                    listOf(
                        "onLanguage:go",
                        "workspaceContains:*.go",
                        "workspaceContains:go.mod",
                        "workspaceContains:go.work",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = LanguageId.GO.value,
                                    extensions = GO_EXTENSIONS,
                                    aliases = listOf("Go", "Golang"),
                                ),
                            ),
                    ),
            ),
    ) {
    private var languageClient: LanguageClient? = null

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        logger.info { "Activating Go plugin" }

        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.GO,
                    extensions = GO_EXTENSIONS,
                    aliases = listOf("Go", "Golang"),
                ),
            ).also { context.subscriptions.add(it) }

        val selector = DocumentSelector(languages = listOf(LanguageId.GO))
        val formatter =
            ExternalStdioFormatter(
                languageId = LanguageId.GO,
                command = listOf(formatterCommand),
                displayName = "gofmt",
                workingDirectory = workspacePath,
            )
        context.subscriptions.add(FormatterRegistry.register(formatter))
        context.languages
            .registerDocumentFormattingProvider(selector) { document, options ->
                when (val result = formatter.format(document.getText(), options)) {
                    is FormattingResult.Success -> result.edits
                    is FormattingResult.Failure -> emptyList()
                }
            }.also { context.subscriptions.add(it) }

        try {
            languageClient =
                context.languages.startLanguageServer(
                    LanguageServerConfig(
                        name = LANGUAGE_SERVER_NAME,
                        command = listOf(languageServerCommand),
                        args = listOf("serve"),
                        documentSelector = selector,
                        workingDirectory = workspacePath,
                    ),
                )
            logger.info { "gopls started" }
        } catch (error: Exception) {
            logger.warn { "gopls is unavailable: ${error.message}" }
        }
    }

    override suspend fun onDeactivate() {
        languageClient?.stop()
        languageClient = null
        logger.info { "Deactivating Go plugin" }
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.go"

        /** File extensions handled by the Go plugin. */
        public val GO_EXTENSIONS: List<String> = listOf(".go")

        private const val LANGUAGE_SERVER_NAME = "gopls"
        private const val DEFAULT_LANGUAGE_SERVER_COMMAND = "gopls"
        private const val DEFAULT_FORMATTER_COMMAND = "gofmt"
    }
}
