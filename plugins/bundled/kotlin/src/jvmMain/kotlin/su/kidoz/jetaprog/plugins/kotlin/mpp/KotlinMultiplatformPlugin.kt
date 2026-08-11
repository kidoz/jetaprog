package su.kidoz.jetaprog.plugins.kotlin.mpp

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
import su.kidoz.jetaprog.build.gradle.JvmGradleTaskRunner
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.kotlin.KotlinPlugin
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Kotlin Multiplatform framework support, layered on top of the Kotlin plugin.
 *
 * Detection is build-based: the plugin activates on any Gradle workspace but stays dormant
 * unless a build script applies the `org.jetbrains.kotlin.multiplatform` plugin (directly
 * or via the `kotlin("multiplatform")` shorthand), a module has a `src/commonMain` source
 * set, or the build files mention the plugin id. When detected it indexes the declared
 * targets per module and registers Gradle-backed commands for listing targets and for
 * compiling or testing a single target or the whole build.
 */
public class KotlinMultiplatformPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Kotlin Multiplatform Support",
                version = "1.0.0",
                description = "Kotlin Multiplatform detection, target discovery, and Gradle task commands",
                activationEvents =
                    listOf(
                        "workspaceContains:build.gradle",
                        "workspaceContains:build.gradle.kts",
                    ),
                dependencies = mapOf(KotlinPlugin.PLUGIN_ID to "*"),
            ),
    ) {
    private var runner: GradleTaskRunner? = null
    private var project: GradleProject? = null
    private var modules: List<KmpModule> = emptyList()

    override suspend fun onActivate() {
        val workspacePath = context.workspace.rootPath ?: return
        val detected = detectModules(workspacePath)
        if (detected.isEmpty() && !context.build.hasDependency(MULTIPLATFORM_PLUGIN_ID)) {
            logger.debug { "No Kotlin Multiplatform build detected; multiplatform support stays dormant" }
            return
        }
        logger.info { "Kotlin Multiplatform detected (${detected.size} module(s)); registering Gradle commands" }

        modules = detected
        runner = JvmGradleTaskRunner(JvmProcessExecutor())
        project = GradleProject(rootPath = workspacePath)
        registerCommands()
    }

    override suspend fun onDeactivate() {
        runner?.cancelTask()
        runner = null
        project = null
        modules = emptyList()
        logger.info { "Deactivating Kotlin Multiplatform support" }
    }

    private suspend fun detectModules(workspacePath: String): List<KmpModule> {
        val buildScripts =
            context.workspace.findFiles("build.gradle.kts", exclude = "build", maxResults = MAX_BUILD_SCRIPTS) +
                context.workspace.findFiles("build.gradle", exclude = "build", maxResults = MAX_BUILD_SCRIPTS)
        return buildScripts
            .mapNotNull { scriptPath -> detectModule(workspacePath, scriptPath) }
            .sortedBy { it.path }
    }

    private suspend fun detectModule(
        workspacePath: String,
        scriptPath: String,
    ): KmpModule? {
        val script = context.workspace.readFile(scriptPath).getOrDefault("")
        val moduleDir = File(scriptPath).parentFile ?: return null
        val sourceSetDirectories =
            context.workspace
                .listDirectory(File(moduleDir, "src").path)
                .getOrNull()
                .orEmpty()
                .filter { it.isDirectory }
                .map { it.name }

        val appliesPlugin = KotlinMultiplatformBuildScript.appliesMultiplatformPlugin(script)
        val hasCommonMain = COMMON_MAIN_SOURCE_SET in sourceSetDirectories
        if (!appliesPlugin && !hasCommonMain) return null

        val targets =
            KotlinMultiplatformBuildScript.declaredTargets(script) +
                KotlinMultiplatformBuildScript.targetsFromSourceSetDirectories(sourceSetDirectories)
        return KmpModule(
            path = moduleDir.relativeToOrSelf(File(workspacePath)).path.ifEmpty { "." },
            targets = targets.toSortedSet().toList(),
        )
    }

    private fun registerCommands() {
        context.commands
            .registerCommand(LIST_TARGETS_COMMAND) { formatTargets() }
            .also { context.subscriptions.add(it) }
        registerGradleCommand(COMPILE_COMMAND) { args ->
            targetArgument(args)?.let { KotlinMultiplatformBuildScript.compileTaskFor(it) } ?: ASSEMBLE_TASK
        }
        registerGradleCommand(TEST_COMMAND) { args ->
            targetArgument(args)?.let { KotlinMultiplatformBuildScript.testTaskFor(it) } ?: ALL_TESTS_TASK
        }
    }

    private fun targetArgument(args: List<Any?>): String? = args.filterIsInstance<String>().firstOrNull()

    private fun formatTargets(): String =
        if (modules.isEmpty()) {
            "No Kotlin Multiplatform modules detected"
        } else {
            modules.joinToString("\n") { module ->
                val targets =
                    if (module.targets.isEmpty()) "no targets declared" else module.targets.joinToString(", ")
                "${module.path}: $targets"
            }
        }

    private fun registerGradleCommand(
        commandId: String,
        taskFor: (List<Any?>) -> String,
    ) {
        context.commands
            .registerCommand(commandId) { args -> runGradleTask(taskFor(args)) }
            .also { context.subscriptions.add(it) }
    }

    private suspend fun runGradleTask(taskPath: String): String {
        val taskRunner = runner ?: return "Kotlin Multiplatform support is not active"
        val gradleProject = project ?: return "No Gradle project found"
        return taskRunner
            .runTask(gradleProject, taskPath)
            .fold(
                onSuccess = { output -> output.toList().joinToString("\n") { formatGradleOutput(it) } },
                onFailure = { error -> "Command failed: ${error.message}" },
            )
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.kotlin.multiplatform"

        internal const val LIST_TARGETS_COMMAND = "kotlin.mpp.targets.list"
        internal const val COMPILE_COMMAND = "kotlin.mpp.compile"
        internal const val TEST_COMMAND = "kotlin.mpp.test"

        private const val MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
        private const val COMMON_MAIN_SOURCE_SET = "commonMain"
        private const val ASSEMBLE_TASK = "assemble"
        private const val ALL_TESTS_TASK = "allTests"
        private const val MAX_BUILD_SCRIPTS = 64
    }
}

/**
 * A Gradle module that applies Kotlin Multiplatform.
 */
internal data class KmpModule(
    /** Module directory relative to the workspace root (`.` for the root module). */
    val path: String,
    /** Declared target names, e.g. `jvm`, `js`, `iosArm64`. */
    val targets: List<String>,
)
