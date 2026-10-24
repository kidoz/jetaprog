package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.agent.GitAgentWorkspaceReviewSource
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.app.ui.agent.SessionRestoration
import su.kidoz.jetaprog.app.ui.agent.ToolStatus
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.agentChecks
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import java.io.File
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opt-in real Claude ACP recovery scenario. Run :app:desktop:liveAgentRecoveryTest.
 * Uses existing CLI authentication and disposable files; never runs in ordinary build/test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentRecoveryLiveTest {
    private val fileSystem = JvmFileSystem()
    private val processes = JvmProcessExecutor()

    @Test
    fun stopRestartAndContinueInMainAndLinkedCheckout() =
        runBlocking {
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            Dispatchers.setMain(dispatcher)
            val root = createTempDirectory("jetaprog-live-recovery").toFile().canonicalFile
            val main = File(root, "main").apply { mkdir() }
            val tree = File(root, "linked")
            try {
                File(main, "counter.py").writeText("def increment(value):\n    return value + 1\n")
                File(
                    main,
                    "test_counter.py",
                ).writeText("from counter import increment\nassert increment(1) == 2\nprint('CHECK PASSED')\n")
                git(main, listOf("init", "-q"))
                git(main, listOf("add", "counter.py", "test_counter.py"))
                git(
                    main,
                    listOf(
                        "-c",
                        "user.name=Recovery Test",
                        "-c",
                        "user.email=test@example.invalid",
                        "-c",
                        "commit.gpgsign=false",
                        "commit",
                        "-qm",
                        "Fixture",
                    ),
                )
                git(main, listOf("worktree", "add", "-b", "recovery-test", tree.path))
                val untouched = File(tree, "counter.py").readText()
                withContext(Dispatchers.Main) { scenario(main, AgentCheckout.MAIN) }
                assertEquals(untouched, File(tree, "counter.py").readText())
                val mainResult = File(main, "counter.py").readText()
                withContext(Dispatchers.Main) { scenario(tree, AgentCheckout.WORKTREE) }
                assertEquals(mainResult, File(main, "counter.py").readText())
                println("RECOVERY: main and linked checkout isolation verified")
            } finally {
                Dispatchers.resetMain()
                dispatcher.close()
                root.deleteRecursively()
            }
        }

    private suspend fun scenario(
        root: File,
        checkout: AgentCheckout,
    ) {
        val models = mutableListOf<AgentSessionViewModel>()

        fun model(): AgentSessionViewModel =
            AgentSessionViewModel(
                projectPath = root.path,
                fileSystem = fileSystem,
                checkout = checkout,
                workspaceReviewSource = GitAgentWorkspaceReviewSource(processes, root.path),
            ).also { models += it }
        try {
            val first = model()
            first.dispatch(AgentIntent.EnsureConnected)
            await(first, "connect") { it.state.value.connection == AgentConnection.CONNECTED }
            for (kind in listOf(PermissionKind.EDIT, PermissionKind.RUN)) {
                first.dispatch(AgentIntent.SetPermission(kind, PermissionPolicy.AUTO))
                await(first, "fixture permission") { it.state.value.permissions[kind] == PermissionPolicy.AUTO }
            }
            first.dispatch(
                AgentIntent.Send(
                    "Recovery test in this disposable checkout only. " +
                        "Add a short docstring to increment in counter.py " +
                        "using your edit tool. Run python3 test_counter.py, then run sleep 45 " +
                        "as a separate foreground command " +
                        "so I can test cancellation. Do not access other directories, edit other files, " +
                        "or change Git history.",
                ),
            )
            await(first, "running sleep after edit") { vm ->
                File(root, "counter.py").readText().contains("\"\"\"") &&
                    vm.state.value.turns
                        .filterIsInstance<Turn.Agent>()
                        .flatMap { it.blocks }
                        .filterIsInstance<Block.Tool>()
                        .any {
                            it.status == ToolStatus.RUNNING &&
                                (it.args.contains("sleep") || it.name.contains("sleep"))
                        }
            }
            println("RECOVERY: $checkout edit and running command observed")
            first.dispatch(AgentIntent.Stop)
            await(first, "stop") { !it.state.value.isStreaming }
            assertTrue(first.state.value.interrupted)
            val manual = File(root, "counter.py").readText() + "\n# Manual change after Stop\n"
            File(root, "counter.py").writeText(manual)
            first.dispatch(AgentIntent.DismissReviewFile(File(root, "counter.py").path))
            await(first, "refuse revert over manual edits") {
                File(root, "counter.py").path in it.state.value.reviewConflicts
            }
            assertEquals(manual, File(root, "counter.py").readText())
            val recordId = first.state.value.activeRecordId!!
            first.shutdown()
            val saved = AgentHistoryStore(fileSystem, root.path).list().single { it.id == recordId }
            assertTrue(saved.interrupted)
            assertTrue(saved.pendingReviews.any { it.path == File(root, "counter.py").path })
            println("RECOVERY: $checkout Stop, manual-edit preservation and shutdown save verified")
            val classpath =
                checkNotNull(System.getProperty("jetaprog.recovery.classpath")) {
                    "Run this test through :app:desktop:liveAgentRecoveryTest"
                }
            val java = File(System.getProperty("java.home"), "bin/java").path
            val result =
                processes
                    .execute(
                        listOf(java, "-cp", classpath, javaClass.name, root.path, checkout.name, recordId),
                        workingDirectory = root.path,
                        timeoutMillis = 150_000,
                    ).getOrThrow()
            assertEquals(0, result.exitCode, result.stderr.takeLast(2000))
            assertTrue(result.stdout.contains("RECOVERY: restored in a fresh JVM"))
            assertEquals(manual, File(root, "counter.py").readText())
            println("RECOVERY: $checkout resumed original session in a fresh JVM; continuation completed")
        } finally {
            models.forEach { it.dispose() }
        }
    }

    private suspend fun resume(
        root: File,
        checkout: AgentCheckout,
        recordId: String,
    ) {
        val manual = File(root, "counter.py").readText()
        val restarted =
            AgentSessionViewModel(
                projectPath = root.path,
                fileSystem = fileSystem,
                checkout = checkout,
                workspaceReviewSource = GitAgentWorkspaceReviewSource(processes, root.path),
            )
        try {
            restarted.dispatch(AgentIntent.RestoreSession(recordId))
            await(restarted, "restore") {
                it.state.value.restoration != null && it.state.value.restoration != SessionRestoration.Loading
            }
            assertTrue(restarted.state.value.restoration is SessionRestoration.Resumed)
            assertFalse(restarted.state.value.isStreaming)
            assertTrue(restarted.state.value.interrupted)
            assertTrue(
                agentChecks(restarted.state.value.turns, restarted.state.value.workspaceRevision, false).isEmpty(),
            )
            restarted.dispatch(AgentIntent.DismissReviewFile(File(root, "counter.py").path))
            await(restarted, "refuse restored revert without a current baseline") {
                File(root, "counter.py").path in it.state.value.reviewConflicts
            }
            assertEquals(manual, File(root, "counter.py").readText())
            restarted.dispatch(
                AgentIntent.Send("Do not edit any files. Run python3 test_counter.py and report the result."),
            )
            await(restarted, "continuation start") { it.state.value.isStreaming }
            await(restarted, "continuation completion") { !it.state.value.isStreaming }
            assertFalse(restarted.state.value.interrupted)
            assertEquals(manual, File(root, "counter.py").readText())
            assertTrue(
                agentChecks(restarted.state.value.turns, restarted.state.value.workspaceRevision, false)
                    .any { it.tool.args.contains("test_counter.py") && it.tool.status == ToolStatus.OK },
            )
            restarted.shutdown()
            println("RECOVERY: restored in a fresh JVM")
        } finally {
            restarted.dispose()
        }
    }

    companion object {
        /** Separate-process restore entry point, invoked only by this opt-in test. */
        @JvmStatic
        fun main(args: Array<String>): Unit =
            runBlocking {
                require(args.size == 3)
                val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                Dispatchers.setMain(dispatcher)
                try {
                    withContext(Dispatchers.Main) {
                        AgentRecoveryLiveTest().resume(File(args[0]), AgentCheckout.valueOf(args[1]), args[2])
                    }
                } finally {
                    Dispatchers.resetMain()
                    dispatcher.close()
                }
            }
    }

    private suspend fun await(
        model: AgentSessionViewModel,
        phase: String,
        ready: (AgentSessionViewModel) -> Boolean,
    ) {
        withTimeout(120_000) {
            while (!ready(model)) {
                check(model.state.value.error == null) { "$phase: ${model.state.value.error}" }
                delay(100)
            }
        }
    }

    private suspend fun git(
        root: File,
        args: List<String>,
    ) {
        val result =
            processes
                .execute(
                    listOf("git") + args,
                    workingDirectory = root.path,
                    timeoutMillis = 10_000,
                ).getOrThrow()
        check(result.isSuccess) { result.stderr }
    }
}
