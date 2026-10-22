package su.kidoz.jetaprog.app.agent

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A linked worktree is created beside the repository, not inside it. */
class GitAgentWorktreeLauncherTest {
    @Test
    fun createPutsTheCheckoutOutsideTheMainTree() =
        runTest {
            val git = ScriptedGit(toplevel = ok("/repo\n"))
            val directories = mutableListOf<String>()
            val fileSystem = mockk<FileSystem>()
            coEvery { fileSystem.createDirectory(any()) } answers {
                directories += firstArg<String>()
                Result.success(Unit)
            }
            val worktree =
                GitAgentWorktreeLauncher(git, fileSystem) { "abc12345" }
                    .create("/repo/app")
                    .getOrThrow()

            assertEquals("/repo.worktrees/abc12345", worktree.path)
            assertEquals("jetaprog/abc12345", worktree.branch)
            assertEquals(listOf("/repo.worktrees"), directories)
            assertEquals("/repo/app" to listOf("git", "rev-parse", "--show-toplevel"), git.commands[0])
            assertEquals(
                "/repo" to
                    listOf(
                        "git",
                        "worktree",
                        "add",
                        "-b",
                        "jetaprog/abc12345",
                        "/repo.worktrees/abc12345",
                        "HEAD",
                    ),
                git.commands[1],
            )
            assertFalse(worktree.path.startsWith("/repo/"))
        }

    @Test
    fun aProjectThatIsNotARepositoryDoesNotAddAWorktree() =
        runTest {
            val git = ScriptedGit(toplevel = ProcessResult(exitCode = 128, stdout = "", stderr = "not a git repo"))
            val fileSystem = mockk<FileSystem>()
            val result = GitAgentWorktreeLauncher(git, fileSystem).create("/tmp/notes")

            assertEquals(WORKTREE_NOT_GIT_NOTICE, result.exceptionOrNull()?.message)
            assertEquals(1, git.commands.size)
        }

    private class ScriptedGit(
        private val toplevel: ProcessResult,
    ) : ProcessExecutor {
        val commands = mutableListOf<Pair<String?, List<String>>>()

        override suspend fun execute(
            command: List<String>,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> {
            commands += workingDirectory to command
            return Result.success(if (command.getOrNull(1) == "rev-parse") toplevel else ok(""))
        }

        override suspend fun executeShell(
            command: String,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.failure(UnsupportedOperationException())

        override suspend fun start(config: ProcessConfig): Result<RunningProcess> =
            Result.failure(UnsupportedOperationException())
    }
}

private fun ok(stdout: String): ProcessResult = ProcessResult(exitCode = 0, stdout = stdout, stderr = "")
