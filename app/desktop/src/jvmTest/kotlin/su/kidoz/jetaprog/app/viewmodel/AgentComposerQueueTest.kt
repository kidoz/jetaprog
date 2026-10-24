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
import kotlinx.coroutines.test.advanceTimeBy
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
    private val failAt: Int = -1,
    private val firstStopReason: String = "end_turn",
    private val holdSecondPrompt: Boolean = false,
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val release = CompletableDeferred<Unit>()
    val secondRelease = CompletableDeferred<Unit>()
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
                if (holdSecondPrompt && promptCount == 2) secondRelease.await()
                if (promptCount == failAt) {
                    inbound.send(
                        """{"jsonrpc":"2.0","id":${request["id"]},"error":{"code":-32603,"message":"Agent failed"}}""",
                    )
                } else {
                    val reason = if (promptCount == 1) firstStopReason else "end_turn"
                    reply(request, """{"stopReason":"$reason"}""")
                }
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
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.prompt,
            )
            assertFalse(
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.steer == true,
            )
            assertEquals("", viewModel.state.value.composerInput)
            assertEquals(0, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it", "Add tests"), agent.prompts)
            assertNull(
                viewModel.state.value.queuedPrompts
                    .firstOrNull(),
            )
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
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.prompt,
            )
            assertFalse(
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.steer == true,
            )
            assertEquals(1, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it"), agent.prompts)
            assertEquals(
                "Add tests",
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
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
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.steer == true,
            )
            assertEquals(0, agent.cancels)

            agent.tool(status = "completed", update = true)
            runCurrent()
            assertEquals(1, agent.cancels)

            agent.release.complete(Unit)
            runCurrent()

            assertEquals(listOf("Build it", "Follow up"), agent.prompts)
            assertNull(
                viewModel.state.value.queuedPrompts
                    .firstOrNull(),
            )
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
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
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

            assertNull(
                viewModel.state.value.queuedPrompts
                    .firstOrNull(),
            )

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
            viewModel.dispatch(
                AgentIntent.RemoveQueuedPrompt(
                    viewModel.state.value.queuedPrompts
                        .first()
                        .id,
                ),
            )
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()

            assertNull(
                viewModel.state.value.queuedPrompts
                    .firstOrNull(),
            )
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

    @Test
    fun multipleFollowUpsKeepTheirOrderAndDistinctIdentities() =
        runTest {
            val agent = HeldPromptTransport(true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            listOf("Test", "Test", "Document").forEach { vm.dispatch(AgentIntent.Send(it)) }
            runCurrent()
            assertEquals(
                listOf("Test", "Test", "Document"),
                vm.state.value.queuedPrompts
                    .map { it.prompt },
            )
            assertEquals(
                3,
                vm.state.value.queuedPrompts
                    .map { it.id }
                    .distinct()
                    .size,
            )
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Test", "Test", "Document"), agent.prompts)
            assertTrue(
                vm.state.value.queuedPrompts
                    .isEmpty(),
            )
        }

    @Test
    fun queueEditsMovesAndRemovalPreserveAttachmentsAndComposerDraft() =
        runTest {
            val agent = HeldPromptTransport(true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            vm.dispatch(AgentIntent.SetComposerInput("Explain"))
            vm.dispatch(AgentIntent.AttachCurrentSelection)
            vm.dispatch(AgentIntent.Send("Explain"))
            vm.dispatch(AgentIntent.Send("Drop"))
            vm.dispatch(AgentIntent.Send("Test"))
            runCurrent()
            val queue = vm.state.value.queuedPrompts
            vm.dispatch(AgentIntent.PauseQueue)
            vm.dispatch(AgentIntent.SetComposerInput("Unsent draft"))
            vm.dispatch(AgentIntent.UpdateQueuedPrompt(queue[0].id, "Explain better"))
            vm.dispatch(AgentIntent.RemoveQueuedPrompt(queue[1].id))
            vm.dispatch(AgentIntent.MoveQueuedPrompt(queue[2].id, earlier = true))
            runCurrent()
            assertEquals(
                listOf("Test", "Explain better"),
                vm.state.value.queuedPrompts
                    .map { it.prompt },
            )
            assertEquals(
                queue[0].attachments,
                vm.state.value.queuedPrompts
                    .last()
                    .attachments,
            )
            assertEquals("Unsent draft", vm.state.value.composerInput)
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build"), agent.prompts)
            vm.dispatch(AgentIntent.ResumeQueue)
            runCurrent()
            assertEquals("Test", agent.prompts[1])
            assertTrue(agent.prompts[2].contains("val x = 1"))
            assertTrue(agent.prompts[2].endsWith("Explain better"))
            assertEquals("Unsent draft", vm.state.value.composerInput)
        }

    @Test
    fun steeringPrioritizesTheNewMessageWithoutDroppingEarlierOnes() =
        runTest {
            val agent = HeldPromptTransport(true, firstStopReason = "cancelled")
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            vm.dispatch(AgentIntent.Send("Test"))
            vm.dispatch(AgentIntent.Send("Document"))
            vm.dispatch(AgentIntent.Steer("Change approach"))
            runCurrent()
            assertEquals(
                listOf("Change approach", "Test", "Document"),
                vm.state.value.queuedPrompts
                    .map { it.prompt },
            )
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Change approach", "Test", "Document"), agent.prompts)
        }

    @Test
    fun failedQueuedTurnPausesTheRemainingItemsUntilExplicitResume() =
        runTest {
            val agent = HeldPromptTransport(true, failAt = 2)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            vm.dispatch(AgentIntent.Send("Test"))
            vm.dispatch(AgentIntent.Send("Document"))
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Test"), agent.prompts)
            assertTrue(vm.state.value.queuePaused)
            assertEquals(
                "Document",
                vm.state.value.queuedPrompts
                    .single()
                    .prompt,
            )
            vm.dispatch(AgentIntent.ResumeQueue)
            runCurrent()
            assertEquals(listOf("Build", "Test", "Document"), agent.prompts)
        }

    @Test
    fun stopDuringFollowUpPreparationCannotBeOvertakenByDelivery() =
        runTest {
            val preparing = CompletableDeferred<Unit>()
            val releaseRead = CompletableDeferred<Unit>()
            var instructionReads = 0
            coEvery { fileSystem.exists("/proj/AGENTS.md") } coAnswers {
                instructionReads++
                if (instructionReads == 2) {
                    preparing.complete(Unit)
                    releaseRead.await()
                }
                false
            }
            val agent = HeldPromptTransport(true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            vm.dispatch(AgentIntent.Send("Test"))
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            assertTrue(preparing.isCompleted)
            vm.dispatch(AgentIntent.Stop)
            runCurrent()
            releaseRead.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build"), agent.prompts)
            assertEquals(
                "Test",
                vm.state.value.queuedPrompts
                    .single()
                    .prompt,
            )
            assertTrue(vm.state.value.queuePaused)
            vm.dispatch(AgentIntent.ResumeQueue)
            vm.dispatch(AgentIntent.ResumeQueue)
            runCurrent()
            assertEquals(listOf("Build", "Test"), agent.prompts)
        }

    @Test
    fun stoppedRunningToolIsMarkedInterrupted() =
        runTest {
            val agent = HeldPromptTransport(true, firstStopReason = "cancelled")
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            agent.tool("in_progress", update = false)
            runCurrent()
            vm.dispatch(AgentIntent.Stop)
            agent.release.complete(Unit)
            runCurrent()

            val tool =
                vm.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<su.kidoz.jetaprog.app.ui.agent.Block.Tool>()
                    .single()
            assertEquals(su.kidoz.jetaprog.app.ui.agent.ToolStatus.INTERRUPTED, tool.status)
        }

    @Test
    fun rapidInitialSendsAreDeliveredInOrder() =
        runTest {
            val agent = HeldPromptTransport(true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            vm.dispatch(AgentIntent.Send("Test"))
            vm.dispatch(AgentIntent.Send("Document"))
            runCurrent()
            assertEquals(listOf("Build"), agent.prompts)
            assertEquals(
                listOf("Test", "Document"),
                vm.state.value.queuedPrompts
                    .map { it.prompt },
            )

            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Test", "Document"), agent.prompts)
            assertTrue(
                vm.state.value.queuedPrompts
                    .isEmpty(),
            )
        }

    @Test
    fun unacknowledgedStopClosesConnectionWithoutStartingOverlappingPrompt() =
        runTest {
            val agent = HeldPromptTransport(true, holdSecondPrompt = true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            vm.dispatch(AgentIntent.Send("Test"))
            vm.dispatch(AgentIntent.Send("Document"))
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Test"), agent.prompts)
            vm.dispatch(AgentIntent.Stop)
            runCurrent()
            advanceTimeBy(5_001)
            runCurrent()
            assertFalse(vm.state.value.isStreaming)
            assertTrue(vm.state.value.queuePaused)
            assertEquals(su.kidoz.jetaprog.app.ui.agent.AgentConnection.DISCONNECTED, vm.state.value.connection)
            assertEquals(listOf("Build", "Test"), agent.prompts)
            assertEquals(
                "Document",
                vm.state.value.queuedPrompts
                    .single()
                    .prompt,
            )
        }

    @Test
    fun movingASteeringMessageBackDisarmsItsPendingCancellation() =
        runTest {
            val agent = HeldPromptTransport(true)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.Send("Build"))
            runCurrent()
            agent.tool("in_progress", update = false)
            runCurrent()
            vm.dispatch(AgentIntent.Send("Test"))
            vm.dispatch(AgentIntent.Steer("Change approach"))
            runCurrent()
            vm.dispatch(
                AgentIntent.MoveQueuedPrompt(
                    vm.state.value.queuedPrompts
                        .first()
                        .id,
                    earlier = false,
                ),
            )
            runCurrent()
            agent.tool("completed", update = true)
            runCurrent()
            assertEquals(0, agent.cancels)
            assertTrue(
                vm.state.value.queuedPrompts
                    .none { it.steer },
            )
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(listOf("Build", "Test", "Change approach"), agent.prompts)
        }

    @Test
    fun inspectorUsesCheckoutInstructionsAndRefreshesWithoutChangingQueuedContext() =
        runTest {
            files["/proj/AGENTS.md"] = " Main rules "
            files["/wt/AGENTS.md"] = " Worktree rules "
            val agent = HeldPromptTransport(holdFirstPrompt = true)
            val vm = viewModel(agent, projectPath = "/wt")
            vm.dispatch(AgentIntent.SetComposerInput("First"))
            vm.dispatch(AgentIntent.Send("First"))
            runCurrent()
            vm.dispatch(AgentIntent.SetComposerInput("Next"))
            vm.dispatch(AgentIntent.AttachCurrentSelection)
            vm.dispatch(AgentIntent.Send("Next"))
            runCurrent()
            val queued =
                vm.state.value.queuedPrompts
                    .single()
            vm.dispatch(AgentIntent.SetComposerInput("Draft"))
            vm.dispatch(AgentIntent.AttachCurrentFile)
            vm.dispatch(AgentIntent.ShowContext)
            runCurrent()
            assertTrue(vm.state.value.contextVisible)
            assertEquals("Worktree rules", vm.state.value.instructionPreview.text)
            assertFalse(vm.state.value.instructionPreview.loading)
            assertEquals(1, agent.prompts.size)
            files["/wt/AGENTS.md"] = " Refreshed rules "
            vm.dispatch(AgentIntent.RefreshContext)
            vm.dispatch(AgentIntent.RemoveAttachment("src/Reader.kt", false))
            runCurrent()
            assertEquals("Refreshed rules", vm.state.value.instructionPreview.text)
            assertTrue(
                vm.state.value.composerAttachments
                    .isEmpty(),
            )
            assertEquals(
                queued,
                vm.state.value.queuedPrompts
                    .single(),
            )
            vm.dispatch(AgentIntent.HideContext)
            runCurrent()
            assertFalse(vm.state.value.contextVisible)
            assertEquals("Draft", vm.state.value.composerInput)
            agent.release.complete(Unit)
            runCurrent()
            assertTrue(agent.prompts.last().contains("Refreshed rules"))
            assertTrue(agent.prompts.last().contains("Selection in src/Reader.kt:\nval x = 1"))
        }

    @Test
    fun inspectingDoesNotCacheInstructionsForSending() =
        runTest {
            files["/proj/AGENTS.md"] = "Before inspection"
            val agent = HeldPromptTransport(holdFirstPrompt = false)
            val vm = viewModel(agent)
            vm.dispatch(AgentIntent.ShowContext)
            runCurrent()
            assertEquals("Before inspection", vm.state.value.instructionPreview.text)
            assertTrue(agent.prompts.isEmpty())
            files["/proj/AGENTS.md"] = "After inspection"
            vm.dispatch(AgentIntent.Send("Explain this"))
            runCurrent()
            assertEquals("Project instructions (AGENTS.md):\nAfter inspection\n\nExplain this", agent.prompts.single())
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
