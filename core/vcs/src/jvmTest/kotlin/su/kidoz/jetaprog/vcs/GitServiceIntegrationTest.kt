package su.kidoz.jetaprog.vcs

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end test driving [DefaultGitService] against a real temporary
 * repository via the `git` CLI, exercising status, stage, commit and diff.
 */
class GitServiceIntegrationTest {
    private val repoDir: File = Files.createTempDirectory("jetaprog-git-it").toFile()
    private val executor = JvmProcessExecutor()
    private val git = DefaultGitService(executor, repoDir.absolutePath)

    @AfterTest
    fun tearDown() {
        repoDir.deleteRecursively()
    }

    private suspend fun git(vararg args: String) {
        executor.execute(listOf("git", *args), workingDirectory = repoDir.absolutePath).getOrThrow()
    }

    @Test
    fun statusStageCommitAndDiffFlow() =
        runTest {
            git("init", "--initial-branch=main")
            git("config", "user.email", "test@example.com")
            git("config", "user.name", "Test")

            assertTrue(git.isRepository())

            File(repoDir, "a.kt").writeText("val x = 1\n")

            val initial = git.status().getOrThrow()
            assertTrue(
                initial.unstaged.any { it.path == "a.kt" && it.type == GitChangeType.UNTRACKED },
                "expected a.kt untracked, got ${initial.changes}",
            )

            git.stage(listOf("a.kt")).getOrThrow()
            val staged = git.status().getOrThrow()
            assertTrue(staged.staged.any { it.path == "a.kt" }, "expected a.kt staged, got ${staged.changes}")

            git.commit("Add a.kt").getOrThrow()
            assertTrue(git.status().getOrThrow().isClean, "tree should be clean after commit")

            File(repoDir, "a.kt").writeText("val x = 2\n")
            val diff = git.diff("a.kt", staged = false).getOrThrow()
            assertTrue(diff.contains("val x = 2"), "diff should show the new content: $diff")
        }
}
