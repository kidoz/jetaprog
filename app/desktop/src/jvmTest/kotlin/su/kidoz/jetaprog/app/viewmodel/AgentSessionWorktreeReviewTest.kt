package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.agent.AgentWorktreeLauncher
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.DiffDecision
import su.kidoz.jetaprog.app.ui.agent.REVIEW_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.REVIEW_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_ALREADY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_UNAVAILABLE_NOTICE
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scripted ACP peer. The prompt at [holdFrom] waits until [allow]. */
private class ReviewTransport(
    private val holdFrom: Int = Int.MAX_VALUE,
    private val onPrompt: suspend (ReviewTransport) -> Unit = {},
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val prompts = mutableListOf<String>()
    private val gates = Channel<Unit>(Channel.UNLIMITED)
    private var promptCount = 0
    override val incoming: Flow<String> = inbound.receiveAsFlow()

    fun allow() {
        gates.trySend(Unit)
    }

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        when (request.getValue("method").jsonPrimitive.content) {
            "initialize" -> {
                reply(request, """{"protocolVersion":1,"agentCapabilities":{"loadSession":false}}""")
            }

            "session/new" -> {
                reply(request, """{"sessionId":"s1"}""")
            }

            "session/prompt" -> {
                prompts +=
                    request
                        .getValue("params")
                        .jsonObject
                        .getValue("prompt")
                        .jsonArray[0]
                        .jsonObject
                        .getValue("text")
                        .jsonPrimitive.content
                promptCount++
                onPrompt(this)
                if (promptCount >= holdFrom) gates.receive()
                reply(request, """{"stopReason":"end_turn"}""")
            }
        }
    }

    suspend fun diff() {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s1","update":""" +
                """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                """"status":"completed","content":[{"type":"diff","path":"/proj/src/Reader.kt",""" +
                """"oldText":"old","newText":"new"}]}}}""",
        )
    }

    private suspend fun reply(
        request: JsonObject,
        result: String,
    ) {
        val id = request["id"] ?: return
        inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
    }

    override suspend fun close() {
        inbound.close()
    }
}

/** `/worktree` opens one linked session, and `/review` decides the session diff locally. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionWorktreeReviewTest {
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val viewModels = mutableListOf<AgentSessionViewModel>()
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery { fileSystem.exists(any()) } answers
            { files.containsKey(firstArg()) || firstArg<String>().endsWith("agent-history") }
        coEvery { fileSystem.createDirectory(any()) } returns Result.success(Unit)
        coEvery { fileSystem.readText(any(), any()) } answers { Result.success(files.getValue(firstArg())) }
        coEvery { fileSystem.writeText(any(), any(), any()) } answers {
            files[firstArg()] = secondArg()
            Result.success(Unit)
        }
        coEvery { fileSystem.delete(any()) } answers {
            files.remove(firstArg<String>())
            Result.success(Unit)
        }
        coEvery { fileSystem.listDirectory(any()) } answers {
            val directory = firstArg<String>()
            Result.success(
                files.keys.filter { it.startsWith("$directory/") }.map { path ->
                    FileEntry(path.substringAfterLast('/'), path, false, true, false, 0, 0, false)
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.dispose() }
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun reviewWithoutChangesStaysLocal() =
        runTest {
            val viewModel = model()
            viewModel.dispatch(AgentIntent.Send("/review"))
            runCurrent()

            assertEquals(REVIEW_EMPTY_NOTICE, viewModel.state.value.reviewNotice)
            assertFalse(viewModel.state.value.reviewVisible)
        }

    @Test
    fun reviewAcceptsACitedFileWithoutAnotherPrompt() =
        runTest {
            val agent = ReviewTransport { it.diff() }
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("/review"))
            runCurrent()

            assertEquals(listOf("Edit the reader"), agent.prompts)
            assertTrue(viewModel.state.value.reviewVisible)
            assertNull(viewModel.state.value.reviewNotice)
            viewModel.dispatch(AgentIntent.AcceptReviewFile("/proj/src/Reader.kt"))
            runCurrent()

            val decision =
                viewModel.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<Block.Diff>()
                    .single()
                    .decision
            assertEquals(DiffDecision.ACCEPTED, decision)
            assertEquals(1, agent.prompts.size)
        }

    @Test
    fun anArgumentStillGoesToTheAgent() =
        runTest {
            val agent = ReviewTransport()
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("/review the diff"))
            runCurrent()

            assertEquals(listOf("/review the diff"), agent.prompts)
            assertFalse(viewModel.state.value.reviewVisible)
        }

    @Test
    fun worktreeIsCreatedOnceAndReopenedByTheSameCommand() =
        runTest {
            val launcher = FakeLauncher(Result.success(AgentWorktree("/repo.worktrees/abc12345", "jetaprog/abc12345")))
            val opened = mutableListOf<String>()
            val viewModel =
                model(
                    launcher = launcher,
                    onReady = { opened += it.path },
                )
            viewModel.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()

            assertEquals(1, launcher.calls)
            assertEquals(listOf("/repo.worktrees/abc12345", "/repo.worktrees/abc12345"), opened)
            assertNull(viewModel.state.value.worktreeNotice)
            assertTrue(agentPrompts(viewModel).isEmpty())
        }

    @Test
    fun aRunningTurnAndAWorktreeChatRefuseAnotherCheckout() =
        runTest {
            val launcher = FakeLauncher(Result.success(AgentWorktree("/repo.worktrees/abc12345", "jetaprog/abc12345")))
            val running = model(ReviewTransport(holdFrom = 1), launcher = launcher)
            connect(running)
            running.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            running.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()

            assertEquals(WORKTREE_BUSY_NOTICE, running.state.value.worktreeNotice)
            assertEquals(0, launcher.calls)

            val linked = model(checkout = AgentCheckout.WORKTREE)
            linked.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()
            assertEquals(WORKTREE_ALREADY_NOTICE, linked.state.value.worktreeNotice)

            val plain = model()
            plain.dispatch(AgentIntent.Send("/worktree"))
            runCurrent()
            assertEquals(WORKTREE_UNAVAILABLE_NOTICE, plain.state.value.worktreeNotice)
        }

    @Test
    fun theHostKeepsASingleWorktreeSession() =
        runTest {
            var created = 0
            val main = model()
            val host =
                AgentSessionHost(main) {
                    created++
                    model(checkout = AgentCheckout.WORKTREE)
                }
            val tree = AgentWorktree("/repo.worktrees/abc12345", "jetaprog/abc12345")
            host.attach(tree)
            host.attach(tree)
            host.showWorktree()

            assertEquals(1, created)
            assertTrue(host.active.value !== main)
            host.showMain()
            assertTrue(host.active.value === main)
        }

    @Test
    fun acceptingDuringATurnIsRefused() =
        runTest {
            val viewModel = model(ReviewTransport(holdFrom = 1) { it.diff() })
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            viewModel.dispatch(AgentIntent.AcceptReviewFile("/proj/src/Reader.kt"))
            runCurrent()

            assertEquals(REVIEW_BUSY_NOTICE, viewModel.state.value.reviewNotice)
        }

    private fun model(
        agent: ReviewTransport = ReviewTransport(),
        checkout: AgentCheckout = AgentCheckout.MAIN,
        launcher: AgentWorktreeLauncher? = null,
        onReady: (AgentWorktree) -> Unit = {},
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = "/proj",
            fileSystem = fileSystem,
            transportFactory = { agent },
            checkout = checkout,
            worktreeLauncher = launcher,
            onWorktreeReady = onReady,
        ).also { viewModels += it }

    private fun kotlinx.coroutines.test.TestScope.connect(viewModel: AgentSessionViewModel) {
        viewModel.dispatch(AgentIntent.EnsureConnected)
        runCurrent()
    }

    private fun agentPrompts(viewModel: AgentSessionViewModel): List<String> =
        viewModel.state.value.turns
            .filterIsInstance<Turn.User>()
            .map { it.text }

    private class FakeLauncher(
        private val result: Result<AgentWorktree>,
    ) : AgentWorktreeLauncher {
        var calls: Int = 0

        override suspend fun create(projectPath: String): Result<AgentWorktree> {
            calls++
            return result
        }
    }
}
