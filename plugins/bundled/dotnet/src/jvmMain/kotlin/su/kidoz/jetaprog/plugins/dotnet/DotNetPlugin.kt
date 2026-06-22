package su.kidoz.jetaprog.plugins.dotnet

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.dotnet.DotNetConfiguration
import su.kidoz.jetaprog.build.dotnet.DotNetOutput
import su.kidoz.jetaprog.build.dotnet.DotNetProject
import su.kidoz.jetaprog.build.dotnet.DotNetRunner
import su.kidoz.jetaprog.build.dotnet.JvmDotNetRunner
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
 * .NET language and build support plugin for JetaProg.
 *
 * Provides:
 * - C# language registration
 * - Roslyn language server startup when available
 * - .NET CLI build, run, test, restore, publish, and pack commands
 */
public class DotNetPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = "su.kidoz.jetaprog.dotnet",
                name = ".NET Language Support",
                version = "1.0.0",
                description = ".NET support with Roslyn LSP and dotnet CLI integration",
                activationEvents =
                    listOf(
                        "onLanguage:csharp",
                        "workspaceContains:*.sln",
                        "workspaceContains:*.csproj",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "csharp",
                                    extensions = listOf(".cs", ".csx"),
                                    aliases = listOf("C#", "csharp"),
                                ),
                                LanguageContribution(
                                    id = "msbuild",
                                    extensions = listOf(".csproj", ".fsproj", ".vbproj", ".props", ".targets"),
                                    aliases = listOf("MSBuild", ".NET Project"),
                                ),
                            ),
                    ),
            ),
    ) {
    private val processExecutor = JvmProcessExecutor()
    private var _dotNetRunner: DotNetRunner? = null

    /**
     * The .NET CLI runner for this workspace.
     */
    public val dotNetRunner: DotNetRunner
        get() = _dotNetRunner ?: error(".NET plugin not activated")

    /**
     * The detected .NET project for this workspace, if available.
     */
    public var dotNetProject: DotNetProject? = null
        private set

    override suspend fun onActivate() {
        logger.info { "Activating .NET plugin" }

        val workspacePath = context.workspace.rootPath ?: return
        _dotNetRunner = JvmDotNetRunner(processExecutor)
        dotNetProject = detectProject(workspacePath)

        registerLanguages()
        startRoslynLanguageServer(workspacePath)
        registerDotNetCommands()

        logger.info { ".NET plugin activated" }
    }

    override suspend fun onDeactivate() {
        _dotNetRunner?.cancel()
        logger.info { "Deactivating .NET plugin" }
    }

    private suspend fun detectProject(workspacePath: String): DotNetProject? {
        val solutionPath =
            context.workspace.findFiles("*.sln", maxResults = 1).firstOrNull()
                ?: context.workspace.findFiles("*.slnx", maxResults = 1).firstOrNull()
        val projectPath = context.workspace.findFiles("*.csproj", maxResults = 1).firstOrNull()

        return if (solutionPath != null || projectPath != null) {
            DotNetProject(
                rootPath = workspacePath,
                solutionPath = solutionPath,
                projectPath = projectPath,
            )
        } else {
            null
        }
    }

    private fun registerLanguages() {
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.CSHARP,
                    extensions = listOf(".cs", ".csx"),
                    aliases = listOf("C#", "csharp"),
                ),
            ).also { context.subscriptions.add(it) }

        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.MSBUILD,
                    extensions = listOf(".csproj", ".fsproj", ".vbproj", ".props", ".targets"),
                    aliases = listOf("MSBuild", ".NET Project"),
                ),
            ).also { context.subscriptions.add(it) }
    }

    private suspend fun startRoslynLanguageServer(workspacePath: String) {
        val selector = DocumentSelector(languages = listOf(LanguageId.CSHARP))

        try {
            val lspConfig =
                LanguageServerConfig(
                    name = "roslyn-language-server",
                    command = listOf("roslyn-language-server"),
                    args = listOf("--stdio", "--autoLoadProjects"),
                    documentSelector = selector,
                    workingDirectory = workspacePath,
                )
            context.languages.startLanguageServer(lspConfig)
            logger.info { "Roslyn language server started" }
        } catch (e: Exception) {
            logger.warn { "Failed to start Roslyn language server: ${e.message}" }
            logger.info {
                "Install Roslyn LSP with: dotnet tool install --global roslyn-language-server --prerelease"
            }
        }
    }

    private fun registerDotNetCommands() {
        context.commands
            .registerCommand("dotnet.restore") {
                val project = dotNetProject ?: return@registerCommand "No .NET project or solution found"
                executeDotNetCommand { dotNetRunner.restore(project) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.build") { args ->
                val project = dotNetProject ?: return@registerCommand "No .NET project or solution found"
                val configuration = args.toConfiguration()
                executeDotNetCommand { dotNetRunner.build(project, configuration = configuration) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.run") { args ->
                val project = dotNetProject ?: return@registerCommand "No .NET project found"
                val configuration = args.toConfiguration()
                val runArgs = args.filterIsInstance<String>().filterNot { it.isConfigurationArgument() }
                executeDotNetCommand {
                    dotNetRunner.run(project, configuration = configuration, arguments = runArgs)
                }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.test") { args ->
                val project = dotNetProject ?: return@registerCommand "No .NET project or solution found"
                val configuration = args.toConfiguration()
                val filter =
                    args
                        .filterIsInstance<String>()
                        .dropWhile { it != "--filter" }
                        .drop(1)
                        .firstOrNull()
                executeDotNetCommand {
                    dotNetRunner.test(project, configuration = configuration, filter = filter)
                }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.publish") { args ->
                val project = dotNetProject ?: return@registerCommand "No .NET project or solution found"
                val configuration = args.toConfiguration(default = DotNetConfiguration.RELEASE)
                executeDotNetCommand { dotNetRunner.publish(project, configuration = configuration) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.pack") { args ->
                val project = dotNetProject ?: return@registerCommand "No .NET project or solution found"
                val configuration = args.toConfiguration(default = DotNetConfiguration.RELEASE)
                executeDotNetCommand { dotNetRunner.pack(project, configuration = configuration) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand("dotnet.info") {
                dotNetRunner.info().getOrElse { error -> "Command failed: ${error.message}" }
            }.also { context.subscriptions.add(it) }
    }

    private fun executeDotNetCommand(
        command: suspend () -> Result<kotlinx.coroutines.flow.Flow<DotNetOutput>>,
    ): String =
        runBlocking {
            command().fold(
                onSuccess = { flow ->
                    flow.toList().joinToString("\n") { output -> formatDotNetOutput(output) }
                },
                onFailure = { error ->
                    "Command failed: ${error.message}"
                },
            )
        }

    private fun formatDotNetOutput(output: DotNetOutput): String =
        when (output) {
            is DotNetOutput.Stdout -> {
                output.line
            }

            is DotNetOutput.Stderr -> {
                "[stderr] ${output.line}"
            }

            is DotNetOutput.CommandStarted -> {
                "Running: ${output.command} ${output.args.joinToString(" ")}"
            }

            DotNetOutput.Restoring -> {
                "Restoring packages"
            }

            is DotNetOutput.Building -> {
                "Building ${output.target ?: "workspace"}"
            }

            is DotNetOutput.Testing -> {
                "Testing ${output.target ?: "workspace"}"
            }

            is DotNetOutput.Publishing -> {
                "Publishing ${output.target ?: "workspace"}"
            }

            is DotNetOutput.Packing -> {
                "Packing ${output.target ?: "workspace"}"
            }

            is DotNetOutput.CommandCompleted -> {
                val status = if (output.success) "succeeded" else "failed"
                "Command $status with exit code ${output.exitCode}"
            }
        }

    private fun List<Any?>.toConfiguration(
        default: DotNetConfiguration = DotNetConfiguration.DEBUG,
    ): DotNetConfiguration {
        val values = filterIsInstance<String>()
        return when {
            values.any { it.equals("release", ignoreCase = true) || it.equals("--release", ignoreCase = true) } -> {
                DotNetConfiguration.RELEASE
            }

            values.any { it.equals("debug", ignoreCase = true) || it.equals("--debug", ignoreCase = true) } -> {
                DotNetConfiguration.DEBUG
            }

            else -> {
                default
            }
        }
    }

    private fun String.isConfigurationArgument(): Boolean =
        equals("debug", ignoreCase = true) ||
            equals("release", ignoreCase = true) ||
            equals("--debug", ignoreCase = true) ||
            equals("--release", ignoreCase = true)
}
