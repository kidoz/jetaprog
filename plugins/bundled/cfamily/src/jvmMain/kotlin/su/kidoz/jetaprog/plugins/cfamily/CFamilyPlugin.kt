package su.kidoz.jetaprog.plugins.cfamily

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.support.formatters.FormatterRegistry

private val logger = KotlinLogging.logger {}

/**
 * Shared activation logic for the C and C++ plugins.
 *
 * Both languages are served by one clangd process and one build system integration, so
 * everything except the language identity lives here. [CPlugin] and [CppPlugin] stay
 * separate so a project can enable either on its own.
 *
 * @property languageId The language this plugin owns.
 * @property commandPrefix Namespace for the commands this plugin registers, e.g. `cpp`.
 * @property extensions File extensions mapped to [languageId].
 * @property aliases Display aliases for [languageId].
 * @property cStandard C standard applied to CMake configuration and generated clangd config.
 * @property cppStandard C++ standard applied to CMake configuration and generated clangd config.
 * @property clangdExecutable Path or name of the clangd binary.
 * @property clangFormatExecutable Path or name of the clang-format binary.
 */
public abstract class CFamilyPlugin(
    manifest: PluginManifest,
    protected val languageId: LanguageId,
    protected val commandPrefix: String,
    protected val extensions: List<String>,
    protected val aliases: List<String>,
    protected val cStandard: CStandard = CStandard.LATEST,
    protected val cppStandard: CppStandard = CppStandard.LATEST,
    protected val clangdExecutable: String = ClangdOptions.DEFAULT_EXECUTABLE,
    protected val clangFormatExecutable: String = "clang-format",
) : BasePlugin(manifest) {
    private val processExecutor = JvmProcessExecutor()

    private var workspacePath: String? = null

    /**
     * Build system integration for this workspace, available after activation.
     */
    public var buildSupport: CFamilyBuildSupport? = null
        private set

    /**
     * Whether clangd is running for this workspace.
     */
    public var isLanguageServerRunning: Boolean = false
        private set

    override suspend fun onActivate() {
        logger.info { "Activating ${manifest.name}" }

        val root = context.workspace.rootPath ?: return
        workspacePath = root

        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = languageId,
                    extensions = extensions,
                    aliases = aliases,
                ),
            ).also { context.subscriptions.add(it) }

        val support =
            CFamilyBuildSupport.detect(
                workspace = context.workspace,
                workspacePath = root,
                processExecutor = processExecutor,
                cStandard = cStandard,
                cppStandard = cppStandard,
            )
        buildSupport = support
        logger.info { "Detected ${support.kind} build system at $root" }

        FormatterRegistry.register(
            ClangFormatFormatter(
                languageId = languageId,
                workspacePath = root,
                clangFormatPath = clangFormatExecutable,
            ),
        )

        isLanguageServerRunning =
            ClangdCoordinator.acquire(
                context = context,
                workspacePath = root,
                pluginId = manifest.id,
                options =
                    ClangdOptions(
                        executable = clangdExecutable,
                        compileCommandsDir = support.compileCommandsDirectory(),
                    ),
            )
        if (!isLanguageServerRunning) {
            logger.info { "clangd is not available; install it via LLVM or your distribution's clang tools" }
        }

        registerCommands(support)

        logger.info { "${manifest.name} activated" }
    }

    private fun registerCommands(support: CFamilyBuildSupport) {
        val root = workspacePath ?: return

        register("$commandPrefix.configure") {
            logger.info { "Configuring ${support.kind} project" }
            support.configure()
        }

        register("$commandPrefix.build") { args ->
            val targets = args.filterIsInstance<String>().filter { !it.startsWith("-") }
            logger.info { "Building targets: ${targets.ifEmpty { listOf("<default>") }}" }
            support.build(targets)
        }

        register("$commandPrefix.test") { args ->
            val filter = args.filterIsInstance<String>().firstOrNull { !it.startsWith("-") }
            logger.info { "Running tests: ${filter ?: "all"}" }
            support.test(filter)
        }

        register("$commandPrefix.clean") {
            logger.info { "Cleaning build outputs" }
            support.clean()
        }

        register("$commandPrefix.install") { args ->
            val prefix =
                args
                    .filterIsInstance<String>()
                    .firstOrNull { it.startsWith("--prefix=") }
                    ?.substringAfter("=")
            logger.info { "Installing${prefix?.let { " to $it" } ?: ""}" }
            support.install(prefix)
        }

        register("$commandPrefix.writeClangdConfig") {
            writeClangdConfig(root, cStandard, cppStandard)
        }
    }

    private fun register(
        id: String,
        handler: suspend (args: List<Any?>) -> Any?,
    ) {
        context.commands.registerCommand(id, handler).also { context.subscriptions.add(it) }
    }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating ${manifest.name}" }
        buildSupport?.cancel()
        buildSupport = null
        workspacePath?.let { ClangdCoordinator.release(it, manifest.id) }
        workspacePath = null
        isLanguageServerRunning = false
    }
}
