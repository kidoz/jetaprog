package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.agent.AgentCheckoutState
import su.kidoz.jetaprog.app.agent.AgentDraft
import su.kidoz.jetaprog.app.agent.AgentNavigationState
import su.kidoz.jetaprog.app.agent.AgentNavigationStore
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.agent.AgentWorktreeLauncher
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionHostRestoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val directory = Files.createTempDirectory("jetaprog-host-restore").toFile()
    private val mainPath = File(directory, "main").apply { mkdirs() }.path
    private val tree = AgentWorktree(File(directory, "linked").apply { mkdirs() }.path, "task")
    private val fileSystem = JvmFileSystem()
    private val hosts = mutableListOf<AgentSessionHost>()
    private var launches = 0
    private var creations = 0

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun cleanup() {
        hosts.forEach { it.dispose() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    @Test
    fun restoredPairKeepsDraftsSeparateReusesWorktreeAndDoesNotLaunchAgents() =
        runTest {
            AgentNavigationStore(
                fileSystem,
                mainPath,
            ).save(AgentNavigationState(newChatDraft = AgentDraft("Main draft")))
            AgentNavigationStore(
                fileSystem,
                tree.path,
            ).save(AgentNavigationState(newChatDraft = AgentDraft("Linked draft")))
            val saved = mutableListOf<AgentCheckoutState>()
            val host = host(load = { Result.success(AgentCheckoutState(tree, true)) }, save = { saved += it })
            host.restore()
            assertFalse(host.restoring.value)
            val linked = host.worktree.value!!
            assertSame(linked, host.active.value)
            assertEquals("Main draft", host.main.state.value.composerInput)
            assertEquals("Linked draft", linked.state.value.composerInput)
            host.showMain()
            host.main.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()
            assertSame(linked, host.active.value)
            assertEquals(0, creations)
            assertEquals(0, launches)
            host.showMain()
            host.shutdown()
            assertEquals(AgentCheckoutState(tree, false), saved.last())

            val reopened = host(load = { Result.success(saved.last()) })
            reopened.restore()
            assertSame(reopened.main, reopened.active.value)
            assertNotNull(reopened.worktree.value)
            assertEquals(
                "Linked draft",
                reopened.worktree.value!!
                    .state.value.composerInput,
            )
        }

    @Test
    fun unavailableWorktreeLeavesMainOpenAndPreservesSavedConfigurationForRetry() =
        runTest {
            val saved = mutableListOf<AgentCheckoutState>()
            val host =
                host(
                    load = {
                        Result.failure(
                            IllegalStateException("Saved worktree is missing. Main checkout is open."),
                        )
                    },
                    save = { saved += it },
                )
            host.restore()
            assertSame(host.main, host.active.value)
            assertNull(host.worktree.value)
            assertTrue(
                host.main.state.value.worktreeNotice!!
                    .contains("missing"),
            )
            assertFalse(host.restoring.value)
            host.shutdown()
            assertTrue(saved.isEmpty())
            assertEquals(0, launches)
        }

    @Test
    fun userAttachmentWinsOverASlowRestore() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val host =
                host(load = {
                    release.await()
                    Result.success(AgentCheckoutState(tree, true))
                })
            val restore = launch { host.restore() }
            runCurrent()
            assertTrue(host.restoring.value)
            val other = AgentWorktree(File(directory, "new-linked").apply { mkdirs() }.path, "new")
            host.attach(other)
            host.showMain()
            release.complete(Unit)
            restore.join()
            assertSame(host.main, host.active.value)
            assertEquals(
                other.path,
                host.worktree.value!!
                    .state.value.workspacePath,
            )
            assertFalse(host.restoring.value)
        }

    @Test
    fun shutdownCancelsValidationAndFlushesTheLastSelectedCheckout() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val saved = mutableListOf<AgentCheckoutState>()
            val host =
                host(load = {
                    release.await()
                    Result.success(AgentCheckoutState(tree, true))
                }, save = { saved += it })
            val restore = launch { host.restore() }
            runCurrent()
            host.attach(tree)
            host.showMain()
            host.showWorktree()
            runCurrent()
            host.shutdown()
            release.complete(Unit)
            advanceUntilIdle()
            assertTrue(restore.isCancelled)
            assertEquals(AgentCheckoutState(tree, true), saved.last())
            assertEquals(0, launches)
        }

    private fun host(
        load: suspend () -> Result<AgentCheckoutState?>,
        save: suspend (AgentCheckoutState) -> Unit = {},
    ): AgentSessionHost {
        lateinit var host: AgentSessionHost
        val main = model(mainPath, onTree = { host.attach(it) })
        host = AgentSessionHost(main, loadCheckouts = load, saveCheckouts = save, openWorktree = { model(it.path) })
        hosts += host
        return host
    }

    private fun model(
        path: String,
        onTree: (AgentWorktree) -> Unit = {},
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            path,
            fileSystem,
            transportFactory = {
                launches++
                error("Restoring tabs must not launch an agent")
            },
            discoverAgents = { emptyList() },
            discoverCatalog = { emptyList() },
            worktreeLauncher =
                AgentWorktreeLauncher {
                    creations++
                    Result.success(tree)
                },
            onWorktreeReady = onTree,
        )
}
