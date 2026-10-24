package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real Git fixtures validate membership; no agent process or user checkout is involved. */
class AgentCheckoutStoreTest {
    private val directory = Files.createTempDirectory("jetaprog-checkout-tabs").toFile().canonicalFile
    private val main = File(directory, "main project").apply { mkdirs() }
    private val linked = File(directory, "linked workspace")
    private val executor = JvmProcessExecutor()
    private val store = AgentCheckoutStore(JvmFileSystem(), executor, main.path)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun reopensExistingWorktreeAndTracksItsCurrentBranchAndActiveTab() =
        runTest {
            initializePair()
            val saved = AgentCheckoutState(AgentWorktree(linked.path, "task"), true)
            store.save(saved)
            assertEquals(saved, store.load().getOrThrow())
            git(linked, "switch", "-c", "renamed-task")
            val restored = store.load().getOrThrow()!!
            assertEquals("renamed-task", restored.worktree!!.branch)
            assertTrue(restored.worktreeActive)
            store.save(restored.copy(worktreeActive = false))
            assertFalse(store.load().getOrThrow()!!.worktreeActive)
            git(linked, "switch", "--detach")
            assertEquals(
                "detached HEAD",
                store
                    .load()
                    .getOrThrow()!!
                    .worktree!!
                    .branch,
            )
        }

    @Test
    fun missingAndReplacedWorktreesAreRejectedWithoutRecreation() =
        runTest {
            initializePair()
            store.save(AgentCheckoutState(AgentWorktree(linked.path, "task"), true))
            linked.deleteRecursively()
            assertTrue(store.load().isFailure)
            assertFalse(linked.exists())
            linked.mkdirs()
            git(linked, "init")
            assertTrue(store.load().isFailure)
            assertFalse(File(linked, "fixture.txt").exists())
        }

    @Test
    fun mainCheckoutUnregisteredRepositoryAndCopiedConfigurationAreRejected() =
        runTest {
            initializePair()
            store.save(AgentCheckoutState(AgentWorktree(main.path, "main"), true))
            assertTrue(store.load().isFailure)
            val unrelated = File(directory, "unrelated").apply { mkdirs() }
            git(unrelated, "init")
            store.save(AgentCheckoutState(AgentWorktree(unrelated.path, "main"), true))
            assertTrue(store.load().isFailure)
            store.save(AgentCheckoutState(AgentWorktree(linked.path, "task"), true))
            File(unrelated, ".jetaprog").mkdirs()
            File(main, ".jetaprog/agent-checkouts.json").copyTo(File(unrelated, ".jetaprog/agent-checkouts.json"))
            assertTrue(AgentCheckoutStore(JvmFileSystem(), executor, unrelated.path).load().isFailure)
        }

    @Test
    fun freshProjectsNeedNoGitAndCorruptCurrentFileCanUseTheBackup() =
        runTest {
            assertNull(store.load().getOrThrow())
            initializePair()
            val previous = AgentCheckoutState(AgentWorktree(linked.path, "task"), true)
            store.save(previous)
            store.save(previous.copy(worktreeActive = false))
            File(main, ".jetaprog/agent-checkouts.json").writeText("{broken")
            assertEquals(previous, store.load().getOrThrow())
            File(main, ".jetaprog/agent-checkouts.json.bak").writeText("{broken")
            assertTrue(store.load().isFailure)
        }

    private suspend fun initializePair() {
        git(main, "init", "-b", "main")
        git(
            main,
            "-c",
            "user.name=Fixture",
            "-c",
            "user.email=fixture@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "--allow-empty",
            "-m",
            "fixture",
        )
        git(main, "worktree", "add", "-b", "task", linked.path)
    }

    private suspend fun git(
        directory: File,
        vararg arguments: String,
    ) {
        val result = executor.execute(listOf("git") + arguments, workingDirectory = directory.path).getOrThrow()
        check(result.isSuccess) { result.stderr }
    }
}
