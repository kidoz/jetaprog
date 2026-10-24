package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.platform.filesystem.WorkspacePathGuard
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import java.io.File

/** One Git status entry. Paths are relative to the agent's checkout. */
public data class WorkspaceChange(
    /** Current path, including untracked files. */
    val path: String,
    /** Git's index status character. */
    val index: Char,
    /** Git's working-tree status character. */
    val workingTree: Char,
    /** Source path for a rename or copy. */
    val originalPath: String? = null,
)

/** Read-only checkout inspection, independent of agent-reported tool calls. */
public interface AgentWorkspaceReviewSource {
    /** Enumerates staged, unstaged, conflicted and non-ignored untracked paths. */
    public suspend fun changes(): Result<List<WorkspaceChange>>

    /** Returns a read-only preview, distinguishing staged and working-tree edits. */
    public suspend fun preview(change: WorkspaceChange): Result<String>
}

/** Git-backed review for one checkout; never stages, discards, or writes project files. */
public class GitAgentWorkspaceReviewSource(
    private val processExecutor: ProcessExecutor,
    private val projectPath: String,
) : AgentWorkspaceReviewSource {
    private val guard = WorkspacePathGuard { projectPath }

    override suspend fun changes(): Result<List<WorkspaceChange>> {
        val output =
            git(
                listOf(
                    "status",
                    "--porcelain=v1",
                    "-z",
                    "--untracked-files=all",
                    "--ignore-submodules=none",
                    "--",
                    ".",
                ),
            )
        if (output.isFailure) return Result.failure(output.exceptionOrNull()!!)
        // Porcelain paths are repository-root-relative, even for a nested project.
        val prefixResult = git(listOf("rev-parse", "--show-prefix"))
        if (prefixResult.isFailure) return Result.failure(prefixResult.exceptionOrNull()!!)
        val prefix = prefixResult.getOrThrow().removeSuffix("\n")
        return output.mapCatching { text ->
            parseWorkspaceStatus(text).mapNotNull { change ->
                if (!change.path.startsWith(prefix)) return@mapNotNull null
                change.copy(
                    path = change.path.removePrefix(prefix),
                    originalPath = change.originalPath?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix),
                )
            }
        }
    }

    override suspend fun preview(change: WorkspaceChange): Result<String> {
        val path = runCatching { guard.resolve(change.path) }.getOrElse { return Result.failure(it) }
        if (change.index == '?') {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    require(file.isFile) { "This entry is not a regular file." }
                    val bytes = file.inputStream().use { it.readNBytes(PREVIEW_LIMIT + 1) }
                    when {
                        bytes.size > PREVIEW_LIMIT -> "File is too large for an inline preview. Open it in the editor."
                        bytes.any { it == 0.toByte() } -> "Binary file. No text preview."
                        else -> "Untracked file · current contents\n\n${bytes.toString(Charsets.UTF_8)}"
                    }
                }
            }
        }
        val paths = listOfNotNull(change.originalPath, change.path).distinct()
        val staged = git(listOf("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-color", "--") + paths)
        if (staged.isFailure) return staged
        val working = git(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--") + paths)
        return working
            .map { output ->
                "Staged changes\n${staged.getOrThrow().ifBlank { "No staged diff." }}\n" +
                    "Working-tree changes\n${output.ifBlank {
                        "No working-tree text diff. Submodules may require separate review."
                    }}"
            }.map { output ->
                if (output.length > PREVIEW_LIMIT) output.take(PREVIEW_LIMIT) + "\nPreview truncated." else output
            }
    }

    private suspend fun git(args: List<String>): Result<String> =
        processExecutor
            .executeRaw(
                ProcessConfig(
                    command = listOf("git", "--no-optional-locks", "--literal-pathspecs") + args,
                    workingDirectory = projectPath,
                    timeoutMillis = GIT_TIMEOUT_MS,
                ),
            ).mapCatching {
                check(it.isSuccess) { it.stderr.ifBlank { "Git workspace review is unavailable." } }
                it.stdout
            }

    private companion object {
        const val PREVIEW_LIMIT: Int = 1_000_000
        const val GIT_TIMEOUT_MS: Long = 10_000
    }
}

/** Parses NUL-delimited porcelain v1, including rename/copy source records and unusual filenames. */
internal fun parseWorkspaceStatus(output: String): List<WorkspaceChange> {
    val fields = output.split('\u0000')
    val result = mutableListOf<WorkspaceChange>()
    var cursor = 0
    while (cursor < fields.size) {
        val entry = fields[cursor++]
        if (entry.isEmpty()) continue
        require(entry.length > 3 && entry[2] == ' ') { "Invalid Git status record." }
        val renamed = entry[0] in "RC" || entry[1] in "RC"
        val original = if (renamed) fields.getOrNull(cursor++) else null
        require(!renamed || !original.isNullOrEmpty()) { "Missing rename source." }
        result += WorkspaceChange(entry.substring(3), entry[0], entry[1], original)
    }
    return result
}
