package su.kidoz.jetaprog.plugins.cfamily

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.PluginContext
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageServerConfig
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Builds the clangd invocation for a workspace.
 *
 * @property executable Path or name of the clangd binary.
 * @property compileCommandsDir Directory holding `compile_commands.json`, if one exists.
 * @property backgroundIndex Whether clangd should index the project in the background.
 * @property clangTidy Whether clang-tidy diagnostics are enabled.
 */
internal data class ClangdOptions(
    val executable: String = DEFAULT_EXECUTABLE,
    val compileCommandsDir: String? = null,
    val backgroundIndex: Boolean = true,
    val clangTidy: Boolean = true,
) {
    /**
     * The full command line, starting with the executable.
     */
    fun command(): List<String> =
        buildList {
            add(executable)
            if (backgroundIndex) add("--background-index")
            if (clangTidy) add("--clang-tidy")
            add("--completion-style=detailed")
            add("--function-arg-placeholders")
            add("--header-insertion=iwyu")
            add("--all-scopes-completion")
            add("--pch-storage=memory")
            // Honour project-local .clangd files, which is where per-language
            // -std flags belong for sources outside the compilation database.
            add("--enable-config")
            compileCommandsDir?.let { add("--compile-commands-dir=$it") }
        }

    companion object {
        /** The clangd binary name looked up on PATH. */
        const val DEFAULT_EXECUTABLE: String = "clangd"
    }
}

/**
 * Starts at most one clangd per workspace.
 *
 * The C and C++ plugins are separate so either can be enabled on its own, but clangd
 * handles both languages in a single process and indexing a project twice would be
 * wasteful. The first plugin to activate starts the server for both languages; the
 * second attaches to it. The server is stopped once both plugins have deactivated.
 */
internal object ClangdCoordinator {
    private val mutex = Mutex()
    private val activeUsers = mutableMapOf<String, MutableSet<String>>()

    /** The LSP server name; also the key [su.kidoz.jetaprog.plugins.support.LanguageServerManager] dedupes on. */
    const val SERVER_NAME: String = "clangd"

    /** Languages served by the single clangd instance. */
    val LANGUAGES: List<LanguageId> = listOf(LanguageId.C, LanguageId.CPP)

    /**
     * Ensures clangd is running for [workspacePath], registering [pluginId] as a user.
     *
     * @return true when clangd is running, false when it could not be started.
     */
    suspend fun acquire(
        context: PluginContext,
        workspacePath: String,
        pluginId: String,
        options: ClangdOptions,
    ): Boolean =
        mutex.withLock {
            val users = activeUsers.getOrPut(workspacePath) { mutableSetOf() }
            if (users.isNotEmpty()) {
                users.add(pluginId)
                logger.debug { "clangd already running for $workspacePath; attached $pluginId" }
                return@withLock true
            }

            val config =
                LanguageServerConfig(
                    name = SERVER_NAME,
                    command = options.command(),
                    documentSelector = DocumentSelector(languages = LANGUAGES),
                    workingDirectory = workspacePath,
                )

            try {
                context.languages.startLanguageServer(config)
                users.add(pluginId)
                logger.info { "clangd started for $workspacePath" }
                true
            } catch (e: Exception) {
                activeUsers.remove(workspacePath)
                logger.warn { "Failed to start clangd: ${e.message}" }
                false
            }
        }

    /**
     * Releases [pluginId]'s claim on clangd for [workspacePath].
     */
    suspend fun release(
        workspacePath: String,
        pluginId: String,
    ) {
        mutex.withLock {
            val users = activeUsers[workspacePath] ?: return@withLock
            users.remove(pluginId)
            if (users.isEmpty()) {
                activeUsers.remove(workspacePath)
            }
        }
    }
}

/**
 * Renders a `.clangd` configuration that applies the given language standards to
 * sources that are not covered by a compilation database.
 *
 * clangd's `fallbackFlags` initialization option cannot express per-language flags —
 * `-std=c++26` is rejected outright for a `.c` file — so the standards are expressed
 * as `If`-guarded blocks in a project-local config file instead.
 *
 * @param cStandard The C standard applied to C sources and `.h` headers.
 * @param cppStandard The C++ standard applied to C++ sources and headers.
 */
internal fun renderClangdConfig(
    cStandard: CStandard,
    cppStandard: CppStandard,
): String =
    """
    # Generated by JetaProg. Applies to sources without an entry in compile_commands.json.
    If:
      PathMatch: .*\.(c|h)$
    CompileFlags:
      Add: [-std=${cStandard.flag}]
    ---
    If:
      PathMatch: .*\.(cpp|cc|cxx|c\+\+|cppm|ixx|ccm|cxxm|hpp|hh|hxx|h\+\+|inl|ipp|tpp)$
    CompileFlags:
      Add: [-std=${cppStandard.flag}]
    """.trimIndent() + "\n"

/**
 * Writes [renderClangdConfig] to `<workspacePath>/.clangd`, refusing to overwrite a
 * config the user already maintains.
 *
 * @return A message describing what happened, for display in the command output.
 */
internal fun writeClangdConfig(
    workspacePath: String,
    cStandard: CStandard,
    cppStandard: CppStandard,
): String {
    val target = File(workspacePath, ".clangd")
    if (target.exists()) {
        return "${target.absolutePath} already exists; leaving it untouched."
    }
    return try {
        target.writeText(renderClangdConfig(cStandard, cppStandard))
        "Wrote ${target.absolutePath} (${cStandard.displayName}, ${cppStandard.displayName})."
    } catch (e: Exception) {
        "Failed to write ${target.absolutePath}: ${e.message}"
    }
}
