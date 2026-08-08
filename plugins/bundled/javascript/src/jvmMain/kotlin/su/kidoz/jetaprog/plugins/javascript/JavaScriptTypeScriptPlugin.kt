package su.kidoz.jetaprog.plugins.javascript

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
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * JavaScript and TypeScript language support backed by typescript-language-server
 * and Prettier when those tools are installed.
 *
 * @property languageServerCommand typescript-language-server launcher command.
 * @property formatterCommand Prettier launcher command.
 */
public class JavaScriptTypeScriptPlugin(
    private val languageServerCommand: String = DEFAULT_LANGUAGE_SERVER_COMMAND,
    private val formatterCommand: String = DEFAULT_FORMATTER_COMMAND,
) : BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "JavaScript and TypeScript Language Support",
                version = "1.0.0",
                description = "JavaScript and TypeScript support with LSP diagnostics, navigation, and Prettier",
                activationEvents =
                    listOf(
                        "onLanguage:javascript",
                        "onLanguage:typescript",
                        "workspaceContains:package.json",
                        "workspaceContains:tsconfig.json",
                        "workspaceContains:*.js",
                        "workspaceContains:*.mjs",
                        "workspaceContains:*.cjs",
                        "workspaceContains:*.jsx",
                        "workspaceContains:*.ts",
                        "workspaceContains:*.mts",
                        "workspaceContains:*.cts",
                        "workspaceContains:*.tsx",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = LanguageId.JAVASCRIPT.value,
                                    extensions = JAVASCRIPT_EXTENSIONS,
                                    aliases = listOf("JavaScript", "JS"),
                                ),
                                LanguageContribution(
                                    id = LanguageId.TYPESCRIPT.value,
                                    extensions = TYPESCRIPT_EXTENSIONS,
                                    aliases = listOf("TypeScript", "TS"),
                                ),
                            ),
                    ),
            ),
    ) {
    private var languageClient: LanguageClient? = null

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        logger.info { "Activating JavaScript and TypeScript plugin" }

        registerLanguage(LanguageId.JAVASCRIPT, JAVASCRIPT_EXTENSIONS, listOf("JavaScript", "JS"))
        registerLanguage(LanguageId.TYPESCRIPT, TYPESCRIPT_EXTENSIONS, listOf("TypeScript", "TS"))

        val selector = DocumentSelector(languages = listOf(LanguageId.JAVASCRIPT, LanguageId.TYPESCRIPT))
        registerFormatter(LanguageId.JAVASCRIPT, "js", workspacePath)
        registerFormatter(LanguageId.TYPESCRIPT, "ts", workspacePath)

        try {
            languageClient =
                context.languages.startLanguageServer(
                    LanguageServerConfig(
                        name = LANGUAGE_SERVER_NAME,
                        command = listOf(languageServerCommand),
                        args = listOf("--stdio"),
                        documentSelector = selector,
                        workingDirectory = workspacePath,
                    ),
                )
            logger.info { "TypeScript language server started" }
        } catch (error: Exception) {
            logger.warn { "TypeScript language server is unavailable: ${error.message}" }
        }
    }

    private fun registerLanguage(
        languageId: LanguageId,
        extensions: List<String>,
        aliases: List<String>,
    ) {
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = languageId,
                    extensions = extensions,
                    aliases = aliases,
                ),
            ).also { context.subscriptions.add(it) }
    }

    private fun registerFormatter(
        languageId: LanguageId,
        probeExtension: String,
        workspacePath: String,
    ) {
        val probePath = File(workspacePath, "jetaprog-format-probe.$probeExtension").absolutePath
        val formatter =
            ExternalStdioFormatter(
                languageId = languageId,
                command = listOf(formatterCommand, "--stdin-filepath", probePath),
                displayName = "Prettier",
                workingDirectory = workspacePath,
            )
        context.subscriptions.add(FormatterRegistry.register(formatter))
        context.languages
            .registerDocumentFormattingProvider(
                selector = DocumentSelector(languages = listOf(languageId)),
                provider = { document, options ->
                    when (val result = formatter.format(document.getText(), options)) {
                        is FormattingResult.Success -> result.edits
                        is FormattingResult.Failure -> emptyList()
                    }
                },
            ).also { context.subscriptions.add(it) }
    }

    override suspend fun onDeactivate() {
        languageClient?.stop()
        languageClient = null
        logger.info { "Deactivating JavaScript and TypeScript plugin" }
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.javascript-typescript"

        /** JavaScript module and JSX extensions handled by the plugin. */
        public val JAVASCRIPT_EXTENSIONS: List<String> = listOf(".js", ".mjs", ".cjs", ".jsx")

        /** TypeScript module and TSX extensions handled by the plugin. */
        public val TYPESCRIPT_EXTENSIONS: List<String> = listOf(".ts", ".mts", ".cts", ".tsx")

        private const val LANGUAGE_SERVER_NAME = "typescript-language-server"
        private const val DEFAULT_LANGUAGE_SERVER_COMMAND = "typescript-language-server"
        private const val DEFAULT_FORMATTER_COMMAND = "prettier"
    }
}
