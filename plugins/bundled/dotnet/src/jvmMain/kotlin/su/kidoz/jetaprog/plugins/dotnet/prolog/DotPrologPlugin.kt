package su.kidoz.jetaprog.plugins.dotnet.prolog

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.build.dotnet.DotNetProject
import su.kidoz.jetaprog.build.dotnet.DotNetRunner
import su.kidoz.jetaprog.build.dotnet.JvmDotNetRunner
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.LanguageContribution
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import su.kidoz.jetaprog.plugins.dotnet.executeDotNetCommand
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * DotProlog framework support, layered on top of the .NET plugin.
 *
 * DotProlog (https://github.com/kidoz/dotprolog) brings Prolog to .NET as an SDK-style
 * project language: `.dplproj` MSBuild projects reference `DotProlog.Sdk`, sources live
 * in `.pl` files, and `.dpli` contracts declare the generated .NET facades. Detection is
 * project-based: the plugin activates on any workspace with a `.dplproj` file but stays
 * dormant unless one references the DotProlog SDK or a project depends on a `DotProlog`
 * package. When detected it registers the Prolog language for `.pl`/`.dpli` documents and
 * dotnet-CLI-backed commands for listing, building, running, and testing the projects.
 */
public class DotPrologPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "DotProlog Support",
                version = "1.0.0",
                description = "DotProlog project detection, Prolog language registration, and dotnet CLI commands",
                activationEvents =
                    listOf(
                        "onLanguage:dotprolog",
                        "workspaceContains:*.dplproj",
                    ),
                contributes =
                    Contributions(
                        languages =
                            listOf(
                                LanguageContribution(
                                    id = "dotprolog",
                                    extensions = listOf(".pl", ".dpli"),
                                    aliases = listOf("DotProlog", "Prolog"),
                                ),
                            ),
                    ),
                dependencies = mapOf(DotNetPlugin.PLUGIN_ID to "*"),
            ),
    ) {
    private var runner: DotNetRunner? = null
    private var project: DotNetProject? = null
    private var projects: List<DotPrologProject> = emptyList()

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        val detected = detectProjects(workspacePath)
        if (detected.isEmpty() && !context.build.hasDependency(DOTPROLOG_PACKAGE_PREFIX)) {
            logger.debug { "No DotProlog project detected; DotProlog support stays dormant" }
            return
        }
        logger.info { "DotProlog detected (${detected.size} project(s)); registering dotnet commands" }

        projects = detected
        runner = JvmDotNetRunner(JvmProcessExecutor())
        project = detectWorkspaceProject(workspacePath)
        registerLanguage()
        registerCommands()
    }

    override suspend fun onDeactivate() {
        runner?.cancel()
        runner = null
        project = null
        projects = emptyList()
        logger.info { "Deactivating DotProlog support" }
    }

    private suspend fun detectProjects(workspacePath: String): List<DotPrologProject> =
        context.workspace
            .findFiles("*.dplproj", maxResults = MAX_PROJECT_FILES)
            .mapNotNull { path -> detectProject(workspacePath, path) }
            .sortedBy { it.path }

    private suspend fun detectProject(
        workspacePath: String,
        projectFilePath: String,
    ): DotPrologProject? {
        val content = context.workspace.readFile(projectFilePath).getOrDefault("")
        if (content.isNotEmpty() && !DotPrologProjectFile.isDotPrologProject(content)) return null
        return DotPrologProject(
            path = File(projectFilePath).relativeToOrSelf(File(workspacePath)).path,
            absolutePath = projectFilePath,
            sdkVersion = DotPrologProjectFile.sdkVersion(content),
        )
    }

    private suspend fun detectWorkspaceProject(workspacePath: String): DotNetProject {
        val solutionPath =
            context.workspace.findFiles("*.sln", maxResults = 1).firstOrNull()
                ?: context.workspace.findFiles("*.slnx", maxResults = 1).firstOrNull()
        return DotNetProject(
            rootPath = workspacePath,
            solutionPath = solutionPath,
            projectPath = projects.firstOrNull()?.absolutePath,
        )
    }

    private fun registerLanguage() {
        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = LanguageId.DOTPROLOG,
                    extensions = listOf(".pl", ".dpli"),
                    aliases = listOf("DotProlog", "Prolog"),
                ),
            ).also { context.subscriptions.add(it) }
    }

    private fun registerCommands() {
        context.commands
            .registerCommand(LIST_PROJECTS_COMMAND) { formatProjects() }
            .also { context.subscriptions.add(it) }

        context.commands
            .registerCommand(BUILD_COMMAND) {
                val target = project ?: return@registerCommand NO_PROJECT_MESSAGE
                val dotNetRunner = runner ?: return@registerCommand NOT_ACTIVE_MESSAGE
                executeDotNetCommand { dotNetRunner.build(target) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand(RUN_COMMAND) { args ->
                val target = project ?: return@registerCommand NO_PROJECT_MESSAGE
                val dotNetRunner = runner ?: return@registerCommand NOT_ACTIVE_MESSAGE
                val projectPath = projectArgument(args) ?: target.projectPath
                executeDotNetCommand { dotNetRunner.run(target, projectPath = projectPath) }
            }.also { context.subscriptions.add(it) }

        context.commands
            .registerCommand(TEST_COMMAND) {
                val target = project ?: return@registerCommand NO_PROJECT_MESSAGE
                val dotNetRunner = runner ?: return@registerCommand NOT_ACTIVE_MESSAGE
                executeDotNetCommand { dotNetRunner.test(target) }
            }.also { context.subscriptions.add(it) }
    }

    /**
     * Resolves an optional command argument naming a project (relative or absolute
     * `.dplproj` path) to the matching detected project's absolute path.
     */
    private fun projectArgument(args: List<Any?>): String? {
        val requested = args.filterIsInstance<String>().firstOrNull() ?: return null
        return projects
            .firstOrNull { it.path == requested || it.absolutePath == requested }
            ?.absolutePath
    }

    private fun formatProjects(): String =
        if (projects.isEmpty()) {
            "No DotProlog projects detected"
        } else {
            projects.joinToString("\n") { detected ->
                val sdk = detected.sdkVersion?.let { "DotProlog.Sdk $it" } ?: "DotProlog.Sdk"
                "${detected.path}: $sdk"
            }
        }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.dotnet.prolog"

        internal const val LIST_PROJECTS_COMMAND = "dotprolog.projects.list"
        internal const val BUILD_COMMAND = "dotprolog.build"
        internal const val RUN_COMMAND = "dotprolog.run"
        internal const val TEST_COMMAND = "dotprolog.test"

        private const val DOTPROLOG_PACKAGE_PREFIX = "DotProlog"
        private const val MAX_PROJECT_FILES = 64
        private const val NO_PROJECT_MESSAGE = "No DotProlog project found"
        private const val NOT_ACTIVE_MESSAGE = "DotProlog support is not active"
    }
}

/**
 * A detected DotProlog project in the workspace.
 */
internal data class DotPrologProject(
    /** Project file path relative to the workspace root. */
    val path: String,
    /** Absolute project file path, used as the dotnet CLI target. */
    val absolutePath: String,
    /** Requested `DotProlog.Sdk` version, when declared. */
    val sdkVersion: String?,
)
