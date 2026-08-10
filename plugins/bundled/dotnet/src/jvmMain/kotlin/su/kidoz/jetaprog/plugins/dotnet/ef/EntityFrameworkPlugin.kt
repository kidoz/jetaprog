package su.kidoz.jetaprog.plugins.dotnet.ef

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.build.dotnet.DotNetProject
import su.kidoz.jetaprog.build.dotnet.DotNetRunner
import su.kidoz.jetaprog.build.dotnet.JvmDotNetRunner
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import su.kidoz.jetaprog.plugins.dotnet.executeDotNetCommand
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Entity Framework Core support, layered on top of the .NET plugin.
 *
 * Detection is dependency-based: the plugin activates on any .NET workspace but stays
 * dormant unless a project references `Microsoft.EntityFrameworkCore`. When detected it
 * registers the day-to-day `dotnet ef` workflows (migrations, database update, DbContext
 * inspection) as IDE commands. The `dotnet-ef` global tool must be installed; when it is
 * missing the command output carries the CLI's install hint.
 */
public class EntityFrameworkPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Entity Framework Core Support",
                version = "1.0.0",
                description = "Entity Framework Core detection and dotnet-ef commands",
                activationEvents =
                    listOf(
                        "workspaceContains:*.sln",
                        "workspaceContains:*.csproj",
                    ),
                dependencies = mapOf(DotNetPlugin.PLUGIN_ID to "*"),
            ),
    ) {
    private var runner: DotNetRunner? = null
    private var project: DotNetProject? = null

    override suspend fun onActivate() {
        if (!context.build.hasDependency(EF_CORE_PACKAGE_PREFIX)) {
            logger.debug { "No Entity Framework Core dependency found; EF support stays dormant" }
            return
        }

        val workspacePath = context.workspace.rootPath ?: return
        logger.info { "Entity Framework Core detected; registering dotnet-ef commands" }

        runner = JvmDotNetRunner(JvmProcessExecutor())
        project = detectProject(workspacePath)
        registerEfCommands()
    }

    override suspend fun onDeactivate() {
        runner?.cancel()
        runner = null
        project = null
        logger.info { "Deactivating Entity Framework Core support" }
    }

    private suspend fun detectProject(workspacePath: String): DotNetProject? {
        val solutionPath =
            context.workspace.findFiles("*.sln", maxResults = 1).firstOrNull()
                ?: context.workspace.findFiles("*.slnx", maxResults = 1).firstOrNull()
        val projectPath = context.workspace.findFiles("*.csproj", maxResults = 1).firstOrNull()
        return if (solutionPath != null || projectPath != null) {
            DotNetProject(rootPath = workspacePath, solutionPath = solutionPath, projectPath = projectPath)
        } else {
            null
        }
    }

    private fun registerEfCommands() {
        registerEfCommand("dotnet.ef.migrations.add") { args ->
            val name = args.filterIsInstance<String>().firstOrNull() ?: defaultMigrationName()
            listOf("migrations", "add", name)
        }
        registerEfCommand("dotnet.ef.migrations.list") { listOf("migrations", "list") }
        registerEfCommand("dotnet.ef.migrations.remove") { listOf("migrations", "remove") }
        registerEfCommand("dotnet.ef.database.update") { args ->
            val target = args.filterIsInstance<String>().firstOrNull()
            listOfNotNull("database", "update", target)
        }
        registerEfCommand("dotnet.ef.dbcontext.list") { listOf("dbcontext", "list") }
        registerEfCommand("dotnet.ef.dbcontext.info") { listOf("dbcontext", "info") }
    }

    private fun registerEfCommand(
        commandId: String,
        arguments: (List<Any?>) -> List<String>,
    ) {
        context.commands
            .registerCommand(commandId) { args ->
                val efRunner = runner ?: return@registerCommand "Entity Framework support is not active"
                val efProject = project ?: return@registerCommand "No .NET project found"
                executeDotNetCommand { efRunner.ef(efProject, arguments(args)) }
            }.also { context.subscriptions.add(it) }
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.dotnet.ef"

        private const val EF_CORE_PACKAGE_PREFIX = "Microsoft.EntityFrameworkCore"

        private val MIGRATION_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        /**
         * Fallback migration name when the command is invoked without one, e.g. from
         * the command palette.
         */
        internal fun defaultMigrationName(now: LocalDateTime = LocalDateTime.now()): String =
            "Migration_${MIGRATION_NAME_FORMAT.format(now)}"
    }
}
