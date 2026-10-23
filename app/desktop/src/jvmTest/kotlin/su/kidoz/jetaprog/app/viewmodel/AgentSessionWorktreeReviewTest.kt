package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
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
import su.kidoz.jetaprog.app.agent.AgentBringOutcome
import su.kidoz.jetaprog.app.agent.AgentBroughtChange
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.agent.AgentWorktreeLauncher
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.BRING_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_DIRTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_FROM_WORKTREE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_PENDING_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_UNAVAILABLE_NOTICE
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

    suspend fun diff(path: String = "/proj/src/Reader.kt") {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s1","update":""" +
                """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                """"status":"completed","content":[{"type":"diff","path":"$path",""" +
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

    @Test
    fun bringCopiesTheWorktreeFileIntoTheMainReview() =
        runTest {
            files["/wt/src/Reader.kt"] = "new"
            files["/proj/src/Reader.kt"] = "old"
            val agent = ReviewTransport { it.diff("/wt/src/Reader.kt") }
            val main = model()
            lateinit var host: AgentSessionHost
            val worktree =
                model(
                    agent = agent,
                    checkout = AgentCheckout.WORKTREE,
                    projectPath = "/wt",
                    onBringBack = { changes ->
                        val outcome = main.importBroughtChanges(changes)
                        if (outcome.imported > 0) host.showMain()
                        outcome
                    },
                )
            host = AgentSessionHost(main) { worktree }
            host.attach(AgentWorktree("/wt", "jetaprog/wt"))
            host.showWorktree()
            connect(worktree)
            worktree.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            worktree.dispatch(AgentIntent.Send("/bring"))
            runCurrent()

            assertEquals(listOf("Edit the reader"), agent.prompts)
            assertTrue(host.active.value === main)
            assertTrue(main.state.value.reviewVisible)
            assertEquals("new", files["/proj/src/Reader.kt"])
            assertNull(worktree.state.value.worktreeNotice)
            main.dispatch(AgentIntent.DismissReviewFile("/proj/src/Reader.kt"))
            runCurrent()

            assertEquals("old", files["/proj/src/Reader.kt"])
            val decision =
                main.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<Block.Diff>()
                    .single()
                    .decision
            assertEquals(DiffDecision.REJECTED, decision)
        }

    @Test
    fun bringOnTheMainChatAndAnArgumentStayOutOfTheCopy() =
        runTest {
            val main = model()
            main.dispatch(AgentIntent.Send("/bring"))
            runCurrent()
            assertEquals(BRING_FROM_WORKTREE_NOTICE, main.state.value.worktreeNotice)

            val worktree = model(checkout = AgentCheckout.WORKTREE, projectPath = "/wt")
            worktree.dispatch(AgentIntent.Send("/bring"))
            runCurrent()
            assertEquals(BRING_EMPTY_NOTICE, worktree.state.value.worktreeNotice)

            files["/wt/src/Reader.kt"] = "new"
            val unlinked = ReviewTransport { it.diff("/wt/src/Reader.kt") }
            val linked = model(unlinked, checkout = AgentCheckout.WORKTREE, projectPath = "/wt")
            connect(linked)
            linked.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            linked.dispatch(AgentIntent.Send("/bring"))
            runCurrent()
            assertEquals(BRING_UNAVAILABLE_NOTICE, linked.state.value.worktreeNotice)
            assertFalse(files.containsKey("/proj/src/Reader.kt"))
            assertEquals("new", files["/wt/src/Reader.kt"])

            val agent = ReviewTransport()
            val chatting = model(agent, checkout = AgentCheckout.WORKTREE, projectPath = "/wt")
            connect(chatting)
            chatting.dispatch(AgentIntent.Send("/bring the changes"))
            runCurrent()
            assertEquals(listOf("/bring the changes"), agent.prompts)
            assertNull(chatting.state.value.worktreeNotice)
        }

    @Test
    fun aRunningTurnRefusesToBringChangesBack() =
        runTest {
            val agent = ReviewTransport(holdFrom = 1) { it.diff("/wt/src/Reader.kt") }
            files["/wt/src/Reader.kt"] = "new"
            val worktree =
                model(
                    agent = agent,
                    checkout = AgentCheckout.WORKTREE,
                    projectPath = "/wt",
                    onBringBack = { AgentBringOutcome(imported = 1) },
                )
            connect(worktree)
            worktree.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            worktree.dispatch(AgentIntent.Send("/bring"))
            runCurrent()

            assertEquals(BRING_BUSY_NOTICE, worktree.state.value.worktreeNotice)
            assertEquals(listOf("Edit the reader"), agent.prompts)
        }

    @Test
    fun dirtyAndPendingMainFilesStayInPlace() =
        runTest {
            files["/wt/src/Reader.kt"] = "from-worktree"
            files["/proj/src/Reader.kt"] = "old"
            val dirtyMain = model(editorDirty = { path -> path.endsWith("Reader.kt") })
            val dirtyAgent = ReviewTransport { it.diff("/wt/src/Reader.kt") }
            val dirtyWorktree =
                model(
                    agent = dirtyAgent,
                    checkout = AgentCheckout.WORKTREE,
                    projectPath = "/wt",
                    onBringBack = { changes -> dirtyMain.importBroughtChanges(changes) },
                )
            connect(dirtyWorktree)
            dirtyWorktree.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()
            dirtyWorktree.dispatch(AgentIntent.Send("/bring"))
            runCurrent()

            assertEquals(BRING_DIRTY_NOTICE, dirtyWorktree.state.value.worktreeNotice)
            assertEquals("old", files["/proj/src/Reader.kt"])
            assertFalse(dirtyMain.state.value.reviewVisible)

            files["/proj/src/Writer.kt"] = "kept"
            val main = model(ReviewTransport { it.diff("/proj/src/Writer.kt") })
            connect(main)
            main.dispatch(AgentIntent.Send("Edit the writer"))
            runCurrent()
            val pending =
                main.importBroughtChanges(
                    listOf(
                        AgentBroughtChange("src/Reader.kt", exists = true, content = "from-worktree"),
                        AgentBroughtChange("src/Writer.kt", exists = true, content = "fresh"),
                    ),
                )

            assertEquals(1, pending.imported)
            assertEquals(1, pending.skippedPending)
            assertEquals("from-worktree", files["/proj/src/Reader.kt"])
            assertEquals("kept", files["/proj/src/Writer.kt"])
            assertEquals(BRING_PENDING_NOTICE, main.state.value.reviewNotice)
            assertTrue(main.state.value.reviewVisible)
        }

    @Test
    fun revertPreservesLaterHumanEditsAndUnsavedBuffers() =
        runTest {
            val path = "/proj/src/Reader.kt"
            files[path] = "original"
            var dirty = false
            val main = model(editorDirty = { dirty })
            main.importBroughtChanges(listOf(AgentBroughtChange("src/Reader.kt", true, "agent")))
            files[path] = "human"
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("human", files[path])
            assertTrue(
                main.state.value.reviewConflicts
                    .getValue(path)
                    .contains("outside"),
            )

            files[path] = "agent"
            dirty = true
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("agent", files[path])
            assertTrue(
                main.state.value.reviewConflicts
                    .getValue(path)
                    .contains("unsaved"),
            )

            dirty = false
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("original", files[path])
            assertTrue(
                main.state.value.reviewConflicts
                    .isEmpty(),
            )
        }

    @Test
    fun keepDoesNotWriteAndTheNextRevertRetainsKeptEdits() =
        runTest {
            val path = "/proj/src/Reader.kt"
            files[path] = "original"
            val main = model()
            main.importBroughtChanges(listOf(AgentBroughtChange("src/Reader.kt", true, "kept")))
            main.dispatch(AgentIntent.AcceptReviewFile(path))
            runCurrent()
            assertEquals("kept", files[path])
            coVerify(exactly = 1) { fileSystem.writeText(path, any(), any()) }
            main.importBroughtChanges(listOf(AgentBroughtChange("src/Reader.kt", true, "next")))
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("kept", files[path])
            val diffs =
                main.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<Block.Diff>()
            assertEquals(listOf(DiffDecision.ACCEPTED, DiffDecision.REJECTED), diffs.map { it.decision })
        }

    @Test
    fun failedRevertRetainsItsBaselineForRetry() =
        runTest {
            val path = "/proj/src/Reader.kt"
            files[path] = "original"
            val main = model()
            main.importBroughtChanges(listOf(AgentBroughtChange("src/Reader.kt", true, "agent")))
            coEvery { fileSystem.writeText(path, "original", any()) } returns
                Result.failure(IllegalStateException("read only"))
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("agent", files[path])
            assertTrue(
                main.state.value.reviewConflicts
                    .getValue(path)
                    .contains("read only"),
            )
            coEvery { fileSystem.writeText(path, "original", any()) } answers {
                files[path] = "original"
                Result.success(Unit)
            }
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("original", files[path])
            assertTrue(
                main.state.value.reviewConflicts
                    .isEmpty(),
            )
        }

    @Test
    fun recreatedDeletedFileIsPreservedAndCreatedFileCanBeSafelyRemoved() =
        runTest {
            val path = "/proj/src/Reader.kt"
            files[path] = "original"
            val main = model()
            main.importBroughtChanges(listOf(AgentBroughtChange("src/Reader.kt", false, null)))
            files[path] = "recreated by human"
            main.dispatch(AgentIntent.DismissReviewFile(path))
            runCurrent()
            assertEquals("recreated by human", files[path])
            assertTrue(path in main.state.value.reviewConflicts)

            main.importBroughtChanges(listOf(AgentBroughtChange("src/New.kt", true, "created")))
            main.dispatch(AgentIntent.DismissReviewFile("/proj/src/New.kt"))
            runCurrent()
            assertFalse(files.containsKey("/proj/src/New.kt"))
        }

    private fun model(
        agent: ReviewTransport = ReviewTransport(),
        checkout: AgentCheckout = AgentCheckout.MAIN,
        launcher: AgentWorktreeLauncher? = null,
        onReady: (AgentWorktree) -> Unit = {},
        projectPath: String = "/proj",
        onBringBack: (suspend (List<AgentBroughtChange>) -> AgentBringOutcome)? = null,
        editorDirty: (String) -> Boolean = { false },
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = projectPath,
            fileSystem = fileSystem,
            transportFactory = { agent },
            isEditorDirty = editorDirty,
            checkout = checkout,
            worktreeLauncher = launcher,
            onWorktreeReady = onReady,
            onBringBack = onBringBack,
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
