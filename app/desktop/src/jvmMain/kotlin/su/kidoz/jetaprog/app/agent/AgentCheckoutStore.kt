package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import java.nio.file.Path

/** The linked checkout and selected agent tab for a project. */
@Serializable
public data class AgentCheckoutState(
    /** Existing linked checkout; restoring never creates one. */
    val worktree: AgentWorktree? = null,
    /** Whether the linked checkout was visible instead of main. */
    val worktreeActive: Boolean = false,
)

@Serializable
private data class SavedAgentCheckouts(
    val projectPath: String,
    val state: AgentCheckoutState,
)

/** Persists the tab pair and validates it against Git before another session can use it. */
internal class AgentCheckoutStore(
    private val fileSystem: FileSystem,
    private val processExecutor: ProcessExecutor,
    private val projectPath: String,
) {
    private val directory = "$projectPath/.jetaprog"
    private val path = "$directory/agent-checkouts.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Result<AgentCheckoutState?> =
        try {
            if (!fileSystem.exists(path) && !fileSystem.exists("$path.bak")) {
                Result.success(null)
            } else {
                val saved = read(path) ?: read("$path.bak") ?: error("Saved checkout tabs could not be read.")
                check(
                    saved.projectPath == realPath(projectPath),
                ) { "Saved checkout tabs belong to another project location." }
                val tree = saved.state.worktree
                Result.success(if (tree == null) AgentCheckoutState() else saved.state.copy(worktree = validate(tree)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(
                IllegalStateException("${error.message ?: "Saved worktree is unavailable."} Main checkout is open."),
            )
        }

    suspend fun save(state: AgentCheckoutState) {
        try {
            val saved = SavedAgentCheckouts(realPath(projectPath), state)
            if (!fileSystem.exists(directory)) fileSystem.createDirectory(directory).getOrThrow()
            read(path)?.let { fileSystem.writeText("$path.bak", json.encodeToString(it)).getOrThrow() }
            fileSystem.writeText(path, json.encodeToString(saved)).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Tab persistence is best-effort, like the per-checkout conversation stores.
        }
    }

    private suspend fun read(file: String): SavedAgentCheckouts? =
        try {
            json.decodeFromString<SavedAgentCheckouts>(fileSystem.readText(file).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private suspend fun validate(tree: AgentWorktree): AgentWorktree {
        check(Path.of(tree.path).isAbsolute && fileSystem.isDirectory(tree.path)) { "Saved worktree is missing." }
        val target = realPath(tree.path)
        val mainRoot = realPath(git(projectPath, "rev-parse", "--show-toplevel").trimEnd('\r', '\n'))
        check(target != mainRoot) { "Saved worktree points to the main checkout." }
        val listing = git(projectPath, "worktree", "list", "--porcelain", "-z")
        val entry =
            listing.split("\u0000\u0000").firstOrNull { record ->
                record
                    .split('\u0000')
                    .firstOrNull { it.startsWith("worktree ") }
                    ?.removePrefix("worktree ")
                    ?.let {
                        Path
                            .of(it)
                            .toAbsolutePath()
                            .normalize()
                            .toString()
                    } == target
            } ?: error("Saved path is no longer a linked worktree of this repository.")
        val fields = entry.split('\u0000')
        check(fields.none { it == "bare" || it.startsWith("prunable") }) { "Saved worktree is unavailable." }
        val common =
            realPath(git(projectPath, "rev-parse", "--path-format=absolute", "--git-common-dir").trimEnd('\r', '\n'))
        val targetCommon =
            realPath(git(target, "rev-parse", "--path-format=absolute", "--git-common-dir").trimEnd('\r', '\n'))
        check(common == targetCommon) { "Saved worktree now belongs to another repository." }
        val branch =
            fields.firstOrNull { it.startsWith("branch ") }?.removePrefix("branch refs/heads/") ?: "detached HEAD"
        return AgentWorktree(target, branch)
    }

    private suspend fun git(
        directory: String,
        vararg args: String,
    ): String {
        val result =
            processExecutor
                .executeRaw(
                    ProcessConfig(
                        command = listOf("git") + args,
                        workingDirectory = directory,
                        timeoutMillis = GIT_TIMEOUT_MS,
                    ),
                ).getOrThrow()
        check(result.isSuccess) { "Git could not validate the saved checkout tabs." }
        return result.stdout
    }

    private suspend fun realPath(value: String): String =
        withContext(Dispatchers.IO) {
            Path.of(value).toRealPath().toString()
        }

    private companion object {
        const val GIT_TIMEOUT_MS = 10_000L
    }
}
