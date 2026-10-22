package su.kidoz.jetaprog.app.agent

import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import java.util.UUID

/**
 * Creates a linked worktree with `git worktree add` beside the repository.
 *
 * The directory is a sibling of the repository (`<name>.worktrees/<id>`), so the main checkout
 * is not modified. [newId] is injectable so tests can pin the path.
 *
 * @param processExecutor Runs Git.
 * @param fileSystem Creates the parent directory of the linked checkout.
 * @param newId Supplies the short id used in the branch and directory name.
 */
public class GitAgentWorktreeLauncher(
    private val processExecutor: ProcessExecutor,
    private val fileSystem: FileSystem,
    private val newId: () -> String = {
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(ID_LENGTH)
    },
) : AgentWorktreeLauncher {
    override suspend fun create(projectPath: String): Result<AgentWorktree> {
        val topLevel =
            git(projectPath, "rev-parse", "--show-toplevel").getOrElse { error ->
                return Result.failure(error)
            }
        if (!topLevel.isSuccess || topLevel.stdout.isBlank()) {
            return Result.failure(IllegalStateException(WORKTREE_NOT_GIT_NOTICE))
        }
        val root = topLevel.stdout.trim().replace('\\', '/')
        val name = root.substringAfterLast('/')
        if (name.isBlank()) return Result.failure(IllegalStateException(WORKTREE_NOT_GIT_NOTICE))
        val parent = root.substringBeforeLast('/')
        val directory = if (parent.isEmpty()) "/$name.worktrees" else "$parent/$name.worktrees"
        val id = newId().filter { it.isLetterOrDigit() }.take(ID_LENGTH).ifBlank { "session" }
        val path = "$directory/$id"
        if (path.startsWith("$root/")) {
            return Result.failure(IllegalStateException("The worktree must stay outside the main checkout."))
        }
        val branch = "jetaprog/$id"
        fileSystem.createDirectory(directory).getOrElse { error -> return Result.failure(error) }
        val added =
            git(root, "worktree", "add", "-b", branch, path, "HEAD").getOrElse { error ->
                return Result.failure(error)
            }
        if (!added.isSuccess) {
            val message = added.stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: "Could not create a worktree."
            return Result.failure(IllegalStateException(message))
        }
        return Result.success(AgentWorktree(path = path, branch = branch))
    }

    private suspend fun git(
        directory: String,
        vararg args: String,
    ) = processExecutor.execute(command = listOf("git") + args, workingDirectory = directory)

    private companion object {
        const val ID_LENGTH: Int = 8
    }
}
