package su.kidoz.jetaprog.plugins.java

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
 * Java language support backed by Eclipse JDT Language Server and
 * google-java-format when those tools are installed.
 *
 * @property languageServerCommand Eclipse JDT LS launcher command.
 * @property formatterCommand google-java-format command.
 */
public class JavaPlugin(
    private val languageServerCommand: String = DEFAULT_LANGUAGE_SERVER_COMMAND,
    private val formatterCommand: String = DEFAULT_FORMATTER_COMMAND,
) : BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Java Language Support",
                version = "1.0.0",
                description = "Java support with Eclipse JDT LS, navigation, diagnostics, and formatting",
                activationEvents =
                    listOf(
                        "onLanguage:java",
                        "workspaceContains:*.java",
                        "workspaceContains:pom.xml",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = LanguageId.JAVA.value,
                                    extensions = JAVA_EXTENSIONS,
                                    aliases = listOf("Java"),
                                ),
                            ),
                    ),
            ),
    ) {
    private var languageClient: LanguageClient? = null

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        logger.info { "Activating Java plugin" }

        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.JAVA,
                    extensions = JAVA_EXTENSIONS,
                    aliases = listOf("Java"),
                ),
            ).also { context.subscriptions.add(it) }

        val selector = DocumentSelector(languages = listOf(LanguageId.JAVA))
        val formatter =
            ExternalStdioFormatter(
                languageId = LanguageId.JAVA,
                command = listOf(formatterCommand, "-"),
                displayName = "google-java-format",
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
                        args = listOf("-data", "$workspacePath/.jetaprog/jdtls-workspace"),
                        documentSelector = selector,
                        workingDirectory = workspacePath,
                    ),
                )
            logger.info { "Eclipse JDT Language Server started" }
        } catch (error: Exception) {
            logger.warn { "Eclipse JDT Language Server is unavailable: ${error.message}" }
        }
    }

    override suspend fun onDeactivate() {
        languageClient?.stop()
        languageClient = null
        logger.info { "Deactivating Java plugin" }
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.java"

        /** File extensions handled by the Java plugin. */
        public val JAVA_EXTENSIONS: List<String> = listOf(".java")

        private const val LANGUAGE_SERVER_NAME = "eclipse-jdt-language-server"
        private const val DEFAULT_LANGUAGE_SERVER_COMMAND = "jdtls"
        private const val DEFAULT_FORMATTER_COMMAND = "google-java-format"
    }
}
