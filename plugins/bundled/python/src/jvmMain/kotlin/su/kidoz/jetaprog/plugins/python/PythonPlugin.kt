package su.kidoz.jetaprog.plugins.python

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig
import su.kidoz.jetaprog.plugins.python.lint.RuffLintProvider
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry

private val logger = KotlinLogging.logger {}

/**
 * Python language support plugin for JetaProg.
 *
 * Provides:
 * - Language server integration (Pyright or Pylsp)
 * - Ruff linting
 * - Code formatting
 */
public class PythonPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = "su.kidoz.jetaprog.python",
                name = "Python Language Support",
                version = "1.0.0",
                description = "Python language support with LSP, Ruff linting, and formatting",
                activationEvents = listOf("onLanguage:python"),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "python",
                                    extensions = listOf(".py", ".pyi", ".pyw"),
                                    aliases = listOf("Python", "py"),
                                ),
                            ),
                    ),
            ),
    ) {
    override suspend fun onActivate() {
        logger.info { "Activating Python plugin" }

        val workspacePath = context.workspace.rootPath ?: return

        // Register language configuration
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.PYTHON,
                    extensions = listOf(".py", ".pyi", ".pyw"),
                    aliases = listOf("Python", "py"),
                ),
            ).also { context.subscriptions.add(it) }

        // Register formatting provider using PythonFormatter
        val selector = DocumentSelector(languages = listOf(LanguageId.PYTHON))
        val formatter = PythonFormatter()
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

        // Register Ruff lint provider
        val ruffProvider = RuffLintProvider()
        context.lint.registerProvider(ruffProvider).also { context.subscriptions.add(it) }

        // Try to start Pyright language server if available
        try {
            val lspConfig =
                LanguageServerConfig(
                    name = "pyright",
                    command = listOf("pyright-langserver"),
                    args = listOf("--stdio"),
                    documentSelector = selector,
                    workingDirectory = workspacePath,
                )
            context.languages.startLanguageServer(lspConfig)
            logger.info { "Pyright language server started" }
        } catch (e: Exception) {
            logger.warn { "Failed to start Pyright: ${e.message}. Trying Pylsp..." }
            try {
                val pylspConfig =
                    LanguageServerConfig(
                        name = "pylsp",
                        command = listOf("pylsp"),
                        documentSelector = selector,
                        workingDirectory = workspacePath,
                    )
                context.languages.startLanguageServer(pylspConfig)
                logger.info { "Pylsp language server started" }
            } catch (e2: Exception) {
                logger.warn { "No Python language server available: ${e2.message}" }
            }
        }

        logger.info { "Python plugin activated" }
    }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Python plugin" }
    }
}
