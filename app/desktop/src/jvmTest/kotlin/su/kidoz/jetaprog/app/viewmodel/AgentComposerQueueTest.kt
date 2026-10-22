package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.EditorSnippet
import su.kidoz.jetaprog.app.ui.agent.MentionMatch
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scripted ACP peer whose first prompt can be held open until [release]. */
private class HeldPromptTransport(
    private val holdFirstPrompt: Boolean,
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val release = CompletableDeferred<Unit>()
    val requests = mutableListOf<JsonObject>()
    val prompts = mutableListOf<String>()
    private var promptCount = 0

    override val incoming: Flow<String> = inbound.receiveAsFlow()

    val cancels: Int
        get() = requests.count { it.getValue("method").jsonPrimitive.content == "session/cancel" }

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        requests += request
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
                if (holdFirstPrompt && promptCount == 1) release.await()
                reply(request, """{"stopReason":"end_turn"}""")
            }
        }
    }

    fun tool(
        status: String,
        update: Boolean,
    ) {
        val kind = if (update) "tool_call_update" else "tool_call"
        val titleField = if (update) "" else ",\"title\":\"Read\""
        inbound.trySend(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s1","update":{"sessionUpdate":"$kind","toolCallId":"t1"$titleField,"status":"$status"}}}""",
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

@OptIn(ExperimentalCoroutinesApi::class)
class AgentComposerQueueTest {
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val reads = mutableListOf<String>()
    private val viewModels = mutableListOf<AgentSessionViewModel>()
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        reads.clear()
        coEvery { fileSystem.exists(any()) } answers
            { files.containsKey(firstArg()) || firstArg<String>().endsWith("agent-history") }
        coEvery { fileSystem.createDirectory(any()) } returns Result.success(Unit)
        coEvery { fileSystem.readText(any(), any()) } answers {
            reads += firstArg<String>()
            Result.success(files.getValue(firstArg()))
        }
        coEvery { fileSystem.writeText(any(), any(), any()) } answers {
            files[firstArg()] = secondArg()
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
    fun queuedFollowUpIsSentWhenTheTurnEnds() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("Add tests"))
            viewModel.dispatch(AgentIntent.Send("Add tests"))
            runCurrent()

            assertTrue(viewModel.state.value.isStreaming)
            assertEquals(
                "Add tests",
                viewModel.state.value.queuedPrompt
                    ?.prompt,
            )
            assertFalse(
                viewModel.state.value.queuedPrompt
                    ?.steer == true,
            )
            assertEquals("", viewModel.state.value.composerInput)
            assertEquals(0, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it", "Add tests"), agent.prompts)
            assertNull(viewModel.state.value.queuedPrompt)
            assertFalse(viewModel.state.value.isStreaming)
        }

    @Test
    fun stopLeavesTheQueueUnsent() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Add tests"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Stop)
            runCurrent()

            assertEquals(
                "Add tests",
                viewModel.state.value.queuedPrompt
                    ?.prompt,
            )
            assertFalse(
                viewModel.state.value.queuedPrompt
                    ?.steer == true,
            )
            assertEquals(1, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it"), agent.prompts)
            assertEquals(
                "Add tests",
                viewModel.state.value.queuedPrompt
                    ?.prompt,
            )
            assertFalse(viewModel.state.value.isStreaming)
        }

    @Test
    fun steerCancelsAtTheNextToolBoundaryThenSendsTheFollowUp() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            agent.tool(status = "in_progress", update = false)
            runCurrent()
            viewModel.dispatch(AgentIntent.Steer("Follow up"))
            runCurrent()

            assertTrue(
                viewModel.state.value.queuedPrompt
                    ?.steer == true,
            )
            assertEquals(0, agent.cancels)

            agent.tool(status = "completed", update = true)
            runCurrent()
            assertEquals(1, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it", "Follow up"), agent.prompts)
            assertNull(viewModel.state.value.queuedPrompt)
        }

    @Test
    fun steerCancelsImmediatelyWhenNoToolIsRunning() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Steer("Follow up"))
            runCurrent()

            assertEquals(1, agent.cancels)
            assertTrue(
                viewModel.state.value.queuedPrompt
                    ?.steer == true,
            )

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it", "Follow up"), agent.prompts)
        }

    @Test
    fun newChatDropsTheQueue() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Add tests"))
            runCurrent()
            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()

            assertNull(viewModel.state.value.queuedPrompt)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it"), agent.prompts)
            assertTrue(
                viewModel.state.value.turns
                    .isEmpty(),
            )
        }

    @Test
    fun removingTheQueueKeepsItFromBeingSent() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Add tests"))
            runCurrent()
            viewModel.dispatch(AgentIntent.RemoveQueuedPrompt)
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()

            assertNull(viewModel.state.value.queuedPrompt)
            assertEquals(listOf("Build it"), agent.prompts)
        }

    @Test
    fun attachmentsAreIncludedInThePromptAndShownOnTheTurn() =
        runTest {
            val agent = HeldPromptTransport(holdFirstPrompt = false)
            val searches = mutableListOf<String>()
            val viewModel =
                viewModel(agent) { query ->
                    searches += query
                    listOf(
                        MentionMatch(label = "Reader.kt", path = "src/Reader.kt"),
                        MentionMatch(label = "Other.kt", path = "src/Other.kt"),
                    )
                }

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("see @Rea"))
            runCurrent()

            assertEquals(listOf("Rea"), searches)
            assertEquals(
                listOf("src/Reader.kt", "src/Other.kt"),
                viewModel.state.value.mentionFiles
                    .map { it.path },
            )

            viewModel.dispatch(AgentIntent.AttachMention(path = "src/Reader.kt", label = "Reader.kt"))
            viewModel.dispatch(AgentIntent.AttachCurrentSelection)
            runCurrent()

            assertEquals("see", viewModel.state.value.composerInput)
            assertEquals(emptyList(), viewModel.state.value.mentionFiles)
            assertEquals(
                "src/Reader.kt",
                viewModel.state.value.composerAttachments[0]
                    .path,
            )
            assertNull(
                viewModel.state.value.composerAttachments[0]
                    .excerpt,
            )
            assertEquals(
                "val x = 1",
                viewModel.state.value.composerAttachments[1]
                    .excerpt,
            )

            viewModel.dispatch(AgentIntent.SetComposerInput("Explain this"))
            viewModel.dispatch(AgentIntent.Send("Explain this"))
            runCurrent()

            assertEquals(
                "Context:\nFile: src/Reader.kt\n\nSelection in src/Reader.kt:\nval x = 1\n\nExplain this",
                agent.prompts.single(),
            )
            val user =
                viewModel.state.value.turns
                    .first() as Turn.User
            assertEquals(listOf("Reader.kt", "Reader.kt (selection)"), user.context)
            assertEquals("", viewModel.state.value.composerInput)
            assertTrue(
                viewModel.state.value.composerAttachments
                    .isEmpty(),
            )
        }

    @Test
    fun theRootAgentsFileIsSentWithTheTurn() =
        runTest {
            files["/proj/AGENTS.md"] = "Use spaces.\n@import ./src/AGENTS.md\n"
            files["/proj/src/AGENTS.md"] = "NESTED RULE"
            val agent = HeldPromptTransport(holdFirstPrompt = false)
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Explain this"))
            runCurrent()

            assertEquals(
                "Project instructions (AGENTS.md):\nUse spaces.\n@import ./src/AGENTS.md\n\nExplain this",
                agent.prompts.single(),
            )
            val user =
                viewModel.state.value.turns
                    .first() as Turn.User
            assertEquals("Explain this", user.text)
            assertEquals(listOf("AGENTS.md"), user.context)
            assertTrue(reads.none { it.replace('\\', '/').endsWith("/src/AGENTS.md") })
        }

    @Test
    fun aBlankAgentsFileAndAnotherCheckoutStayOutOfThePrompt() =
        runTest {
            files["/proj/AGENTS.md"] = " \n"
            files["/wt/AGENTS.md"] = "Worktree rules."
            val blank = HeldPromptTransport(holdFirstPrompt = false)
            val main = viewModel(blank)
            main.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            main.dispatch(AgentIntent.Send("Explain this"))
            runCurrent()

            assertEquals("Explain this", blank.prompts.single())
            assertEquals(
                emptyList(),
                (
                    main.state.value.turns
                        .first() as Turn.User
                ).context,
            )

            val worktreeAgent = HeldPromptTransport(holdFirstPrompt = false)
            val worktree = viewModel(worktreeAgent, projectPath = "/wt")
            worktree.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            worktree.dispatch(AgentIntent.Send("Edit the reader"))
            runCurrent()

            assertEquals(
                "Project instructions (AGENTS.md):\nWorktree rules.\n\nEdit the reader",
                worktreeAgent.prompts.single(),
            )
        }

    private fun viewModel(
        agent: HeldPromptTransport,
        projectPath: String = "/proj",
        search: suspend (String) -> List<MentionMatch> = { emptyList() },
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = projectPath,
            fileSystem = fileSystem,
            transportFactory = { agent },
            editorContext = { EditorSnippet(label = "Reader.kt", path = "src/Reader.kt", selection = "val x = 1") },
            mentionSearch = search,
        ).also { viewModels += it }
}
