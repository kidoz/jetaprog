package su.kidoz.jetaprog.plugins.rust

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.cargo.CargoOutput
import su.kidoz.jetaprog.build.cargo.CargoProfile
import su.kidoz.jetaprog.build.cargo.CargoProject
import su.kidoz.jetaprog.build.cargo.CargoRunner
import su.kidoz.jetaprog.build.cargo.JvmCargoRunner
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig

private val logger = KotlinLogging.logger {}

/**
 * Rust language support plugin for JetaProg.
 *
 * Provides:
 * - rust-analyzer Language Server integration
 * - Cargo build system integration
 * - Code completion with type inference
 * - Go to definition and references
 * - Hover information with type details
 * - Diagnostics and error checking
 * - Inlay hints for types and parameters
 * - Code actions and quick fixes
 */
public class RustPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = "su.kidoz.jetaprog.rust",
                name = "Rust Language Support",
                version = "1.0.0",
                description = "Rust language support with rust-analyzer and Cargo integration",
                activationEvents = listOf("onLanguage:rust", "workspaceContains:Cargo.toml"),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "rust",
                                    extensions = listOf(".rs"),
                                    aliases = listOf("Rust", "rust"),
                                ),
                            ),
                    ),
            ),
    ) {
    private val processExecutor = JvmProcessExecutor()
    private var _cargoRunner: CargoRunner? = null

    /**
     * The Cargo runner for this workspace.
     */
    public val cargoRunner: CargoRunner
        get() = _cargoRunner ?: error("Rust plugin not activated")

    /**
     * The Cargo project for this workspace, if available.
     */
    public var cargoProject: CargoProject? = null
        private set

    override suspend fun onActivate() {
        logger.info { "Activating Rust plugin" }

        val workspacePath = context.workspace.rootPath ?: return

        // Initialize Cargo runner
        _cargoRunner = JvmCargoRunner(processExecutor)

        // Check if this is a Cargo project
        val cargoTomlExists = context.workspace.exists("$workspacePath/Cargo.toml")
        if (cargoTomlExists) {
            cargoProject = CargoProject(rootPath = workspacePath)
            logger.info { "Detected Cargo project at $workspacePath" }
        }

        // Register language configuration for Rust
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.RUST,
                    extensions = listOf(".rs"),
                    aliases = listOf("Rust"),
                ),
            ).also { context.subscriptions.add(it) }

        // Register Cargo.toml as TOML language
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.TOML,
                    extensions = listOf(".toml"),
                    filenames = listOf("Cargo.toml", "Cargo.lock"),
                    aliases = listOf("TOML"),
                ),
            ).also { context.subscriptions.add(it) }

        // Try to start rust-analyzer
        val selector = DocumentSelector(languages = listOf(LanguageId.RUST))

        try {
            val lspConfig =
                LanguageServerConfig(
                    name = "rust-analyzer",
                    command = listOf(DEFAULT_COMMAND),
                    documentSelector = selector,
                    workingDirectory = workspacePath,
                )
            context.languages.startLanguageServer(lspConfig)
            logger.info { "rust-analyzer started" }
        } catch (e: Exception) {
            logger.warn { "Failed to start rust-analyzer: ${e.message}" }
            logger.info { "Install rust-analyzer: rustup component add rust-analyzer" }
        }

        // Register commands
        registerCargoCommands()

        logger.info { "Rust plugin activated" }
    }

    private fun registerCargoCommands() {
        // Register cargo.build command
        context.commands
            .registerCommand("cargo.build") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val release = args.getOrNull(0) == "release"
                val profile = if (release) CargoProfile.RELEASE else CargoProfile.DEBUG
                logger.info { "Running cargo build (release=$release)" }
                executeCargoCommand { cargoRunner.build(project, profile = profile) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.run command
        context.commands
            .registerCommand("cargo.run") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val release = args.contains("--release")
                val profile = if (release) CargoProfile.RELEASE else CargoProfile.DEBUG
                val runArgs = args.filterIsInstance<String>().filter { it != "--release" }
                logger.info { "Running cargo run with args: $runArgs" }
                executeCargoCommand { cargoRunner.run(project, profile = profile, args = runArgs) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.test command
        context.commands
            .registerCommand("cargo.test") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val testName = args.getOrNull(0) as? String
                logger.info { "Running cargo test: ${testName ?: "all"}" }
                executeCargoCommand { cargoRunner.test(project, testName = testName) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.check command
        context.commands
            .registerCommand("cargo.check") {
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                logger.info { "Running cargo check" }
                executeCargoCommand { cargoRunner.check(project) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.clippy command
        context.commands
            .registerCommand("cargo.clippy") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val fix = args.contains("--fix")
                logger.info { "Running cargo clippy (fix=$fix)" }
                executeCargoCommand { cargoRunner.clippy(project, fix = fix) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.fmt command
        context.commands
            .registerCommand("cargo.fmt") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val check = args.contains("--check")
                logger.info { "Running cargo fmt (check=$check)" }
                executeCargoCommand { cargoRunner.fmt(project, check = check) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.doc command
        context.commands
            .registerCommand("cargo.doc") { args ->
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                val open = args.contains("--open")
                logger.info { "Running cargo doc (open=$open)" }
                executeCargoCommand { cargoRunner.doc(project, open = open) }
            }.also { context.subscriptions.add(it) }

        // Register cargo.clean command
        context.commands
            .registerCommand("cargo.clean") {
                val project = cargoProject ?: return@registerCommand "No Cargo project found"
                logger.info { "Running cargo clean" }
                executeCargoCommand { cargoRunner.clean(project) }
            }.also { context.subscriptions.add(it) }
    }

    /**
     * Executes a cargo command and collects the output.
     *
     * @param command The suspend function that runs the cargo command.
     * @return The combined output as a string, or an error message.
     */
    private fun executeCargoCommand(command: suspend () -> Result<kotlinx.coroutines.flow.Flow<CargoOutput>>): String =
        runBlocking {
            command().fold(
                onSuccess = { flow ->
                    val outputs = flow.toList()
                    outputs.joinToString("\n") { output ->
                        formatCargoOutput(output)
                    }
                },
                onFailure = { error ->
                    "Command failed: ${error.message}"
                },
            )
        }

    /**
     * Formats a CargoOutput for display.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun formatCargoOutput(output: CargoOutput): String =
        when (output) {
            is CargoOutput.Stdout -> {
                output.line
            }

            is CargoOutput.Stderr -> {
                "[stderr] ${output.line}"
            }

            is CargoOutput.CommandStarted -> {
                "Running: ${output.command} ${output.args.joinToString(" ")}"
            }

            is CargoOutput.Compiling -> {
                "Compiling ${output.crateName}${output.version?.let { " v$it" } ?: ""}"
            }

            is CargoOutput.Downloading -> {
                "Downloading ${output.crateName} v${output.version}"
            }

            is CargoOutput.Downloaded -> {
                "Downloaded ${output.crateName} v${output.version}"
            }

            is CargoOutput.Building -> {
                "Building ${output.crateName}${output.progress?.let { " ($it)" } ?: ""}"
            }

            is CargoOutput.Checking -> {
                "Checking ${output.crateName}${output.version?.let { " v$it" } ?: ""}"
            }

            is CargoOutput.Running -> {
                "Running ${output.target}"
            }

            is CargoOutput.Documenting -> {
                "Documenting ${output.crateName}"
            }

            is CargoOutput.Fresh -> {
                "Fresh ${output.crateName}${output.version?.let { " v$it" } ?: ""}"
            }

            is CargoOutput.Finished -> {
                "Finished ${output.profile}${output.duration?.let { " in $it" } ?: ""}"
            }

            is CargoOutput.Warning -> {
                formatDiagnostic("warning", output.message, output.file, output.code)
            }

            is CargoOutput.Error -> {
                formatDiagnostic("error", output.message, output.file, output.code)
            }

            is CargoOutput.ClippyLint -> {
                formatClippyLint(output)
            }

            is CargoOutput.TestResult -> {
                formatTestResult(output)
            }

            is CargoOutput.TestSummary -> {
                formatTestSummary(output)
            }

            is CargoOutput.DependencyAdded -> {
                "Added ${output.name} v${output.version}"
            }

            is CargoOutput.DependencyRemoved -> {
                "Removed ${output.name}"
            }

            is CargoOutput.LockfileUpdated -> {
                "Updated ${output.path}"
            }

            is CargoOutput.CommandCompleted -> {
                val status = if (output.success) "SUCCESS" else "FAILED"
                "Command $status (exit code: ${output.exitCode})"
            }
        }

    /**
     * Formats a compiler diagnostic (warning or error).
     */
    private fun formatDiagnostic(
        level: String,
        message: String,
        file: String?,
        code: String?,
    ): String =
        buildString {
            append("[$level")
            code?.let { append(" $it") }
            append("] ")
            file?.let { append("$it: ") }
            append(message)
        }

    /**
     * Formats a Clippy lint.
     */
    private fun formatClippyLint(lint: CargoOutput.ClippyLint): String =
        buildString {
            append("[${lint.level}")
            lint.lintName?.let { append(" $it") }
            append("] ")
            lint.file?.let { append("$it: ") }
            append(lint.message)
            lint.suggestion?.let {
                appendLine()
                append("  suggestion: $it")
            }
        }

    /**
     * Formats a test result.
     */
    private fun formatTestResult(result: CargoOutput.TestResult): String =
        buildString {
            append("test ${result.name} ... ")
            append(
                when (result.outcome) {
                    su.kidoz.jetaprog.build.cargo.TestOutcome.PASSED -> "ok"
                    su.kidoz.jetaprog.build.cargo.TestOutcome.FAILED -> "FAILED"
                    su.kidoz.jetaprog.build.cargo.TestOutcome.IGNORED -> "ignored"
                    su.kidoz.jetaprog.build.cargo.TestOutcome.MEASURED -> "measured"
                },
            )
            result.duration?.let { append(" (${it}ms)") }
            result.message?.let {
                appendLine()
                append("  $it")
            }
        }

    /**
     * Formats a test summary.
     */
    private fun formatTestSummary(summary: CargoOutput.TestSummary): String =
        buildString {
            appendLine()
            append("test result: ")
            append(if (summary.failed == 0) "ok" else "FAILED")
            append(". ${summary.passed} passed; ${summary.failed} failed; ${summary.ignored} ignored; ")
            append("${summary.measured} measured; ${summary.filteredOut} filtered out; ")
            append("finished in ${summary.duration}ms")
        }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Rust plugin" }
        _cargoRunner?.cancel()
        _cargoRunner = null
        cargoProject = null
    }

    public companion object {
        /** Default command to start rust-analyzer. */
        public const val DEFAULT_COMMAND: String = "rust-analyzer"
    }
}
