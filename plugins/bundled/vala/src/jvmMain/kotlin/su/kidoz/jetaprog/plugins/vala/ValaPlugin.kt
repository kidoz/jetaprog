package su.kidoz.jetaprog.plugins.vala

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.meson.JvmMesonRunner
import su.kidoz.jetaprog.build.meson.MesonOutput
import su.kidoz.jetaprog.build.meson.MesonProject
import su.kidoz.jetaprog.build.meson.MesonRunner
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
 * Vala language support plugin for JetaProg.
 *
 * Provides:
 * - Vala Language Server integration (vls)
 * - Code completion
 * - Go to definition
 * - Find references
 * - Hover information
 * - Diagnostics
 */
public class ValaPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = "su.kidoz.jetaprog.vala",
                name = "Vala Language Support",
                version = "1.0.0",
                description = "Vala language support with Language Server integration",
                activationEvents = listOf("onLanguage:vala", "workspaceContains:meson.build"),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "vala",
                                    extensions = listOf(".vala", ".vapi"),
                                    aliases = listOf("Vala", "vala"),
                                ),
                            ),
                    ),
            ),
    ) {
    private val processExecutor = JvmProcessExecutor()
    private var _mesonRunner: MesonRunner? = null

    /**
     * The Meson runner for this workspace.
     */
    public val mesonRunner: MesonRunner
        get() = _mesonRunner ?: error("Vala plugin not activated")

    /**
     * The Meson project for this workspace, if available.
     */
    public var mesonProject: MesonProject? = null
        private set

    override suspend fun onActivate() {
        logger.info { "Activating Vala plugin" }

        val workspacePath = context.workspace.rootPath ?: return

        // Initialize Meson runner
        _mesonRunner = JvmMesonRunner(processExecutor)

        // Check if this is a Meson project
        val mesonBuildExists = context.workspace.exists("$workspacePath/meson.build")
        if (mesonBuildExists) {
            mesonProject =
                MesonProject(
                    rootPath = workspacePath,
                    buildDir = "$workspacePath/build",
                )
            logger.info { "Detected Meson project at $workspacePath" }
        }

        // Register language configuration for Vala
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.VALA,
                    extensions = listOf(".vala"),
                    aliases = listOf("Vala"),
                ),
            ).also { context.subscriptions.add(it) }

        // Register language configuration for VAPI (Vala API bindings)
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.VAPI,
                    extensions = listOf(".vapi"),
                    aliases = listOf("Vala API", "VAPI"),
                ),
            ).also { context.subscriptions.add(it) }

        // Try to start Vala Language Server
        val selector = DocumentSelector(languages = listOf(LanguageId.VALA, LanguageId.VAPI))

        try {
            val lspConfig =
                LanguageServerConfig(
                    name = "vala-language-server",
                    command = listOf(DEFAULT_COMMAND),
                    documentSelector = selector,
                    workingDirectory = workspacePath,
                )
            context.languages.startLanguageServer(lspConfig)
            logger.info { "Vala Language Server started" }
        } catch (e: Exception) {
            logger.warn { "Failed to start vala-language-server: ${e.message}. Trying vls..." }
            try {
                val vlsConfig =
                    LanguageServerConfig(
                        name = "vls",
                        command = listOf(ALTERNATIVE_COMMAND),
                        documentSelector = selector,
                        workingDirectory = workspacePath,
                    )
                context.languages.startLanguageServer(vlsConfig)
                logger.info { "VLS started" }
            } catch (e2: Exception) {
                logger.warn { "No Vala language server available: ${e2.message}" }
            }
        }

        // Register Meson build commands
        registerMesonCommands()

        logger.info { "Vala plugin activated" }
    }

    private fun registerMesonCommands() {
        // Register meson.setup command
        context.commands
            .registerCommand("meson.setup") { args ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                val reconfigure = args.contains("--reconfigure")
                logger.info { "Running meson setup (reconfigure=$reconfigure)" }
                executeMesonCommand { mesonRunner.setup(project, reconfigure = reconfigure) }
            }.also { context.subscriptions.add(it) }

        // Register meson.compile command
        context.commands
            .registerCommand("meson.compile") { args ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                val targets = args.filterIsInstance<String>().filter { !it.startsWith("-") }
                logger.info { "Running meson compile with targets: $targets" }
                executeMesonCommand { mesonRunner.compile(project, targets = targets) }
            }.also { context.subscriptions.add(it) }

        // Register meson.test command
        context.commands
            .registerCommand("meson.test") { args ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                val suites = args.filterIsInstance<String>().filter { !it.startsWith("-") }
                logger.info { "Running meson test with suites: ${suites.ifEmpty { listOf("all") }}" }
                executeMesonCommand { mesonRunner.test(project, suites = suites) }
            }.also { context.subscriptions.add(it) }

        // Register meson.clean command
        context.commands
            .registerCommand("meson.clean") {
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                logger.info { "Running meson clean" }
                executeMesonCommand { mesonRunner.clean(project) }
            }.also { context.subscriptions.add(it) }

        // Register meson.install command
        context.commands
            .registerCommand("meson.install") { args ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                val destDir =
                    args
                        .filterIsInstance<String>()
                        .find { it.startsWith("--destdir=") }
                        ?.substringAfter("=")
                logger.info { "Running meson install${destDir?.let { " to $it" } ?: ""}" }
                executeMesonCommand { mesonRunner.install(project, destDir = destDir) }
            }.also { context.subscriptions.add(it) }

        // Register vala.build as an alias for meson.compile
        context.commands
            .registerCommand("vala.build") { _ ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                logger.info { "Running Vala build (via meson compile)" }
                executeMesonCommand { mesonRunner.compile(project) }
            }.also { context.subscriptions.add(it) }

        // Register vala.test as an alias for meson.test
        context.commands
            .registerCommand("vala.test") { _ ->
                val project = mesonProject ?: return@registerCommand "No Meson project found"
                logger.info { "Running Vala tests (via meson test)" }
                executeMesonCommand { mesonRunner.test(project) }
            }.also { context.subscriptions.add(it) }
    }

    /**
     * Executes a Meson command and collects the output.
     *
     * @param command The suspend function that runs the Meson command.
     * @return The combined output as a string, or an error message.
     */
    private fun executeMesonCommand(command: suspend () -> Result<kotlinx.coroutines.flow.Flow<MesonOutput>>): String =
        runBlocking {
            command().fold(
                onSuccess = { flow ->
                    val outputs = flow.toList()
                    outputs.joinToString("\n") { output ->
                        formatMesonOutput(output)
                    }
                },
                onFailure = { error ->
                    "Command failed: ${error.message}"
                },
            )
        }

    /**
     * Formats a MesonOutput for display.
     */
    private fun formatMesonOutput(output: MesonOutput): String =
        when (output) {
            is MesonOutput.Stdout -> {
                output.line
            }

            is MesonOutput.Stderr -> {
                "[stderr] ${output.line}"
            }

            is MesonOutput.CompileStarted -> {
                "Compiling ${output.target}..."
            }

            is MesonOutput.CompileProgress -> {
                "[${output.current}/${output.total}] ${output.target}"
            }

            is MesonOutput.CompileCompleted -> {
                val status = if (output.success) "OK" else "FAILED"
                "Compiled ${output.target}: $status"
            }

            is MesonOutput.TestStarted -> {
                "Running test: ${output.testName}"
            }

            is MesonOutput.TestCompleted -> {
                val status = output.outcome.name.lowercase()
                val duration = output.duration?.let { " (${it}ms)" } ?: ""
                "Test ${output.testName}: $status$duration"
            }

            is MesonOutput.BuildFinished -> {
                val status = if (output.success) "SUCCESS" else "FAILED"
                "Build $status (exit code: ${output.exitCode})"
            }
        }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Vala plugin" }
        _mesonRunner?.cancel()
        _mesonRunner = null
        mesonProject = null
    }

    public companion object {
        /** Default command to start the Vala Language Server. */
        public const val DEFAULT_COMMAND: String = "vala-language-server"

        /** Alternative command name used by some distributions. */
        public const val ALTERNATIVE_COMMAND: String = "vls"
    }
}
