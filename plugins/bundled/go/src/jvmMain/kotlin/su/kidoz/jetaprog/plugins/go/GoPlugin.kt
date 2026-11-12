package su.kidoz.jetaprog.plugins.go

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.build.go.GoModuleParser
import su.kidoz.jetaprog.build.go.GoOperation
import su.kidoz.jetaprog.build.go.GoProject
import su.kidoz.jetaprog.build.go.GoRunner
import su.kidoz.jetaprog.build.go.GoWorkParser
import su.kidoz.jetaprog.build.go.JvmGoRunner
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.CommandContribution
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageClient
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig
import su.kidoz.jetaprog.plugins.go.lint.GolangciLintProvider
import su.kidoz.jetaprog.plugins.support.formatters.ExternalStdioFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult

private val logger = KotlinLogging.logger {}

/**
 * Go language support backed by the Go toolchain.
 *
 * The plugin starts gopls when it is installed, formats with goimports and falls back to
 * gofmt, runs golangci-lint when that binary is installed, and exposes module, workspace,
 * build, test, and vet commands. A missing tool logs a warning and does not fail activation.
 *
 * @property languageServerCommand gopls launcher command.
 * @property formatterCommand gofmt launcher command.
 * @property importsCommand goimports launcher command.
 * @property lintCommand golangci-lint launcher command.
 * @property goCommand `go` executable used by toolchain commands.
 */
public class GoPlugin(
    private val languageServerCommand: String = DEFAULT_LANGUAGE_SERVER_COMMAND,
    private val formatterCommand: String = DEFAULT_FORMATTER_COMMAND,
    private val importsCommand: String = DEFAULT_IMPORTS_COMMAND,
    private val lintCommand: String = DEFAULT_LINT_COMMAND,
    private val goCommand: String = DEFAULT_GO_COMMAND,
) : BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Go Language Support",
                version = "1.0.0",
                description = PLUGIN_DESCRIPTION,
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
                                LanguageContribution(
                                    id = LanguageId.GO_MOD.value,
                                    filenames = listOf("go.mod"),
                                    aliases = listOf("Go Module"),
                                ),
                                LanguageContribution(
                                    id = LanguageId.GO_WORK.value,
                                    filenames = listOf("go.work"),
                                    aliases = listOf("Go Workspace"),
                                ),
                                LanguageContribution(
                                    id = LanguageId.GO_SUM.value,
                                    filenames = listOf("go.sum"),
                                    aliases = listOf("Go Checksum"),
                                ),
                            ),
                        commands =
                            COMMANDS.map { (id, title) ->
                                CommandContribution(command = id, title = title, category = "Go")
                            },
                    ),
            ),
    ) {
    private var languageClient: LanguageClient? = null
    private var runner: GoRunner? = null

    /**
     * The module or workspace loaded for the open folder.
     */
    public var goProject: GoProject? = null
        private set

    /**
     * Toolchain runner for [goProject]. Available after activation.
     */
    public val goRunner: GoRunner
        get() = runner ?: error("Go plugin is not activated")

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        logger.info { "Activating Go plugin" }
        goProject = loadProject(workspacePath)
        runner = JvmGoRunner(JvmProcessExecutor(), goCommand)
        registerLanguages()
        registerFormatter(workspacePath)
        context.lint
            .registerProvider(GolangciLintProvider(lintCommand = lintCommand))
            .also { context.subscriptions.add(it) }
        registerCommands()
        startLanguageServer(workspacePath)
    }

    override suspend fun onDeactivate() {
        languageClient?.stop()
        languageClient = null
        runner?.cancel()
        runner = null
        goProject = null
        logger.info { "Deactivating Go plugin" }
    }

    private suspend fun loadProject(workspacePath: String): GoProject {
        val module =
            context.workspace
                .readFile("$workspacePath/go.mod")
                .getOrNull()
                ?.let(GoModuleParser::parse)
        val workspace =
            context.workspace
                .readFile("$workspacePath/go.work")
                .getOrNull()
                ?.let(GoWorkParser::parse)
        val modulePath = module?.modulePath
        if (modulePath != null) logger.info { "Detected Go module $modulePath" }
        if (workspace != null) logger.info { "Detected Go workspace with ${workspace.use.size} modules" }
        return GoProject(rootPath = workspacePath, module = module, workspace = workspace)
    }

    private suspend fun registerLanguages() {
        listOf(
            LanguageConfiguration(
                id = LanguageId.GO,
                extensions = GO_EXTENSIONS,
                aliases = listOf("Go", "Golang"),
            ),
            LanguageConfiguration(
                id = LanguageId.GO_MOD,
                filenames = listOf("go.mod"),
                aliases = listOf("Go Module"),
            ),
            LanguageConfiguration(
                id = LanguageId.GO_WORK,
                filenames = listOf("go.work"),
                aliases = listOf("Go Workspace"),
            ),
            LanguageConfiguration(
                id = LanguageId.GO_SUM,
                filenames = listOf("go.sum"),
                aliases = listOf("Go Checksum"),
            ),
        ).forEach { configuration ->
            context.languages.registerLanguage(configuration).also { context.subscriptions.add(it) }
        }
    }

    private suspend fun registerFormatter(workspacePath: String) {
        val selector = DocumentSelector(languages = listOf(LanguageId.GO))
        val formatter =
            GoSourceFormatter(
                preferred =
                    ExternalStdioFormatter(
                        languageId = LanguageId.GO,
                        command = listOf(importsCommand),
                        displayName = "goimports",
                        workingDirectory = workspacePath,
                    ),
                fallback =
                    ExternalStdioFormatter(
                        languageId = LanguageId.GO,
                        command = listOf(formatterCommand),
                        displayName = "gofmt",
                        workingDirectory = workspacePath,
                    ),
            )
        context.subscriptions.add(FormatterRegistry.register(formatter))
        context.languages
            .registerDocumentFormattingProvider(selector) { document, options ->
                when (val result = formatter.format(document.getText(), options)) {
                    is FormattingResult.Success -> result.edits
                    is FormattingResult.Failure -> emptyList()
                }
            }.also { context.subscriptions.add(it) }
    }

    private fun registerCommands() {
        register("go.build") { args ->
            val packages = stringArgs(args).ifEmpty { listOf("./...") }
            GoOperation.Build(packages = packages)
        }
        register("go.run") { args -> GoOperation.Run(programArguments = stringArgs(args)) }
        register("go.test") { args -> GoOperation.Test(json = true, run = stringArgs(args).firstOrNull()) }
        register("go.vet") { args -> GoOperation.Vet(packages = stringArgs(args).ifEmpty { listOf("./...") }) }
        register("go.fmt") { args -> GoOperation.Fmt(paths = stringArgs(args).ifEmpty { listOf("./...") }) }
        register("go.generate") { args ->
            GoOperation.Generate(run = stringArgs(args).firstOrNull())
        }
        register("go.mod.tidy") { GoOperation.ModTidy }
        register("go.mod.download") { GoOperation.ModDownload }
        register("go.mod.vendor") { GoOperation.ModVendor }
        register("go.work.sync") { GoOperation.WorkSync }
        register("go.clean") { args -> cleanOperation(stringArgs(args)) }
        register("go.list") { args ->
            GoOperation.ListPackages(pattern = stringArgs(args).firstOrNull() ?: "./...")
        }
        register("go.env", requiresToolchainRoot = false) { GoOperation.Env() }
    }

    private fun register(
        id: String,
        requiresToolchainRoot: Boolean = true,
        operation: (List<Any?>) -> GoOperation,
    ) {
        context.commands
            .registerCommand(id) { args ->
                runGoCommand(
                    project = goProject,
                    runner = runner,
                    operation = operation(args),
                    requiresToolchainRoot = requiresToolchainRoot,
                )
            }.also { context.subscriptions.add(it) }
    }

    private suspend fun startLanguageServer(workspacePath: String) {
        val selector =
            DocumentSelector(
                languages = listOf(LanguageId.GO, LanguageId.GO_MOD, LanguageId.GO_WORK),
            )
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

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.go"

        /** Description shared with the bundled plugin catalog. */
        public const val PLUGIN_DESCRIPTION: String =
            "Go support with gopls, Go modules and workspaces, gofmt, golangci-lint, and Delve"

        /** File extensions handled by the Go plugin. */
        public val GO_EXTENSIONS: List<String> = listOf(".go")

        /**
         * Command ids contributed by this plugin, in catalog order.
         */
        public val COMMANDS: List<Pair<String, String>> =
            listOf(
                "go.build" to "Go: Build",
                "go.run" to "Go: Run",
                "go.test" to "Go: Test",
                "go.vet" to "Go: Vet",
                "go.fmt" to "Go: Format",
                "go.generate" to "Go: Generate",
                "go.mod.tidy" to "Go: Mod Tidy",
                "go.mod.download" to "Go: Mod Download",
                "go.mod.vendor" to "Go: Mod Vendor",
                "go.work.sync" to "Go: Work Sync",
                "go.clean" to "Go: Clean",
                "go.list" to "Go: List Packages",
                "go.env" to "Go: Environment",
            )

        private const val LANGUAGE_SERVER_NAME = "gopls"
        private const val DEFAULT_LANGUAGE_SERVER_COMMAND = "gopls"
        private const val DEFAULT_FORMATTER_COMMAND = "gofmt"
        private const val DEFAULT_IMPORTS_COMMAND = "goimports"
        private const val DEFAULT_LINT_COMMAND = "golangci-lint"
        private const val DEFAULT_GO_COMMAND = "go"
    }
}

private fun stringArgs(args: List<Any?>): List<String> = args.filterIsInstance<String>()

private fun cleanOperation(args: List<String>): GoOperation.Clean =
    GoOperation.Clean(
        cache = "--cache" in args,
        testCache = "--testcache" in args,
        modCache = "--modcache" in args,
        packages = args.filterNot { it.startsWith("--") }.ifEmpty { listOf("./...") },
    )
