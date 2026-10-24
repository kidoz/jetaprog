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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.agent.AgentNavigationStore
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.EditorSnippet
import su.kidoz.jetaprog.app.ui.agent.SessionRestoration
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scripted ACP peer exercising the real client, including asynchronous history replay. */
private class RestoringAgentTransport(
    private val canLoad: Boolean = true,
    private val rejectLoad: Boolean = false,
    private val pauseLoad: Boolean = false,
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val requests = mutableListOf<JsonObject>()
    var closed = false
    private var newSessionCount = 0

    override val incoming: Flow<String> = inbound.receiveAsFlow()

    val methods: List<String>
        get() = requests.map { it.getValue("method").jsonPrimitive.content }

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        assertEquals("2.0", request.getValue("jsonrpc").jsonPrimitive.content)
        requests += request
        val id = request["id"] ?: return
        when (request.getValue("method").jsonPrimitive.content) {
            "initialize" -> {
                reply(id, """{"protocolVersion":1,"agentCapabilities":{"loadSession":$canLoad}}""")
            }

            "session/new" -> {
                reply(id, """{"sessionId":"new-${++newSessionCount}"}""")
            }

            "session/load" -> {
                if (!pauseLoad) {
                    if (rejectLoad) {
                        inbound.send(
                            """{"jsonrpc":"2.0","id":$id,"error":{"code":-32002,"message":"Saved session not found"}}""",
                        )
                    } else {
                        val session =
                            request
                                .getValue("params")
                                .jsonObject
                                .getValue("sessionId")
                                .jsonPrimitive.content
                        update(session, "Replayed answer")
                        finishLoad(id)
                    }
                }
            }

            "session/prompt" -> {
                val session =
                    request
                        .getValue("params")
                        .jsonObject
                        .getValue("sessionId")
                        .jsonPrimitive.content
                update(session, "New answer")
                reply(id, """{"stopReason":"end_turn"}""")
            }
        }
    }

    suspend fun finishLoad(id: JsonElement = requests.last().getValue("id")) {
        reply(
            id,
            """{"models":{"currentModelId":"saved-model","availableModels":[{"modelId":"saved-model","name":"Saved model"}]}}""",
        )
    }

    private suspend fun reply(
        id: JsonElement,
        result: String,
    ) {
        inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
    }

    private suspend fun update(
        session: String,
        text: String,
    ) {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"$session","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"$text"}}}}""",
        )
    }

    override suspend fun close() {
        closed = true
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionRestorationTest {
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val history = AgentHistoryStore(fileSystem, "/project")
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
        coEvery { fileSystem.listDirectory(any()) } answers {
            val directory = firstArg<String>()
            Result.success(
                files.keys.filter { it.startsWith("$directory/") }.map { path ->
                    FileEntry(path.substringAfterLast('/'), path, false, true, false, 0, 0, false)
                },
            )
        }
        coEvery { fileSystem.delete(any()) } answers {
            files.remove(firstArg())
            Result.success(Unit)
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.dispose() }
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun disconnectedRestoreReconnectsAndContinuesTheOriginalSessionWithoutDuplicatingReplay() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport()
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            assertEquals(listOf("initialize", "session/load"), agent.methods)
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
            assertEquals(
                "saved-model",
                viewModel.state.value.model
                    ?.id,
            )
            assertEquals(listOf("Old question", "Old answer"), transcript(viewModel))

            viewModel.dispatch(AgentIntent.Send("Continue"))
            runCurrent()

            val prompt = agent.requests.last { it["method"]?.jsonPrimitive?.content == "session/prompt" }
            assertEquals(
                "old-session",
                prompt
                    .getValue("params")
                    .jsonObject
                    .getValue("sessionId")
                    .jsonPrimitive.content,
            )
            assertEquals(listOf("Old question", "Old answer", "Continue", "New answer"), transcript(viewModel))
            assertEquals("old-session", history.list().single().agentSessionId)
        }

    @Test
    fun connectedRestoreReusesTheAgentAndLoadsTheSavedSession() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport()
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()

            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            assertEquals(listOf("initialize", "session/new", "session/load"), agent.methods)
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
        }

    @Test
    fun unsupportedLoadKeepsTheOriginalRecordAndStartsASeparateChatOnTheNextPrompt() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(canLoad = false)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            val outcome = assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertTrue(outcome.reason.contains("does not support"))
            assertEquals(listOf("initialize"), agent.methods)
            assertEquals(listOf("Old question", "Old answer"), transcript(viewModel))

            viewModel.dispatch(AgentIntent.Send("Fresh question"))
            runCurrent()

            assertNull(viewModel.state.value.restoration)
            assertEquals(listOf("Fresh question", "New answer"), transcript(viewModel))
            val records = history.list()
            assertEquals(2, records.size)
            assertEquals(record, records.single { it.id == record.id })
            assertEquals("new-1", records.single { it.id != record.id }.agentSessionId)
        }

    @Test
    fun failedLoadShowsTheFailureAndDoesNotCreateAnUnrelatedSession() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(rejectLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            val outcome = assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertTrue(outcome.reason.contains("Saved session not found"))
            assertEquals(listOf("initialize", "session/load"), agent.methods)
            assertEquals(record, history.list().single())
            assertEquals(AgentConnection.CONNECTED, viewModel.state.value.connection)
        }

    @Test
    fun failedReconnectKeepsTheTranscriptAndCanBeRetriedFromHistory() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport()
            var attempts = 0
            val viewModel =
                AgentSessionViewModel("/project", fileSystem, transportFactory = {
                    if (attempts++ == 0) error("Agent executable missing")
                    agent
                }).also { viewModels += it }
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertEquals(AgentConnection.ERROR, viewModel.state.value.connection)
            assertEquals(listOf("Old question", "Old answer"), transcript(viewModel))

            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
            assertNull(viewModel.state.value.error)
            assertEquals(2, attempts)
        }

    @Test
    fun legacyRecordWithoutASessionIdIsClearlyTranscriptOnly() =
        runTest {
            val record = savedRecord().copy(agentSessionId = null)
            history.save(record)
            val agent = RestoringAgentTransport()
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()

            assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertTrue(agent.requests.isEmpty())
            assertEquals(record, history.list().single())
        }

    @Test
    fun timedOutLoadFallsBackToTranscriptOnlyAndClosesTheStalledConnection() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(pauseLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            assertEquals(SessionRestoration.Loading, viewModel.state.value.restoration)

            advanceTimeBy(120_001)
            runCurrent()

            assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertEquals(AgentConnection.DISCONNECTED, viewModel.state.value.connection)
            assertTrue(agent.closed)
            assertEquals(record, history.list().single())
        }

    @Test
    fun newChatCancelsAnInProgressRestoreAndIgnoresItsLateResponse() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(pauseLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            assertEquals(SessionRestoration.Loading, viewModel.state.value.restoration)
            val abandonedLoadId = agent.requests.last().getValue("id")

            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()
            agent.finishLoad(abandonedLoadId)
            runCurrent()

            assertNull(viewModel.state.value.restoration)
            assertTrue(
                viewModel.state.value.turns
                    .isEmpty(),
            )
            assertNull(viewModel.state.value.model)
            viewModel.dispatch(AgentIntent.Send("New topic"))
            runCurrent()
            assertEquals(listOf("New topic", "New answer"), transcript(viewModel))
            assertEquals(record, history.list().single { it.id == record.id })
        }

    @Test
    fun selectingAnotherRecordCancelsThePreviousRestore() =
        runTest {
            val first = savedRecord()
            val second = savedRecord().copy(id = "second-record", agentSessionId = "second-session")
            history.save(first)
            history.save(second)
            val agent = RestoringAgentTransport(pauseLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(first.id))
            runCurrent()
            val abandonedLoadId = agent.requests.last().getValue("id")
            viewModel.dispatch(AgentIntent.RestoreSession(second.id))
            runCurrent()

            agent.finishLoad()
            runCurrent()
            agent.finishLoad(abandonedLoadId)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Continue second"))
            runCurrent()

            val promptSession =
                agent.requests
                    .last()
                    .getValue(
                        "params",
                    ).jsonObject
                    .getValue("sessionId")
                    .jsonPrimitive.content
            assertEquals("second-session", promptSession)
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
        }

    @Test
    fun reconnectAfterAgentExitReloadsContextBeforeSendingTheNextPrompt() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val firstAgent = RestoringAgentTransport()
            val secondAgent = RestoringAgentTransport()
            val agents = ArrayDeque(listOf(firstAgent, secondAgent))
            val viewModel =
                AgentSessionViewModel("/project", fileSystem, transportFactory = { agents.removeFirst() })
                    .also { viewModels += it }
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            firstAgent.inbound.close()
            runCurrent()

            viewModel.dispatch(AgentIntent.Send("Continue after reconnect"))
            runCurrent()

            assertEquals(listOf("initialize", "session/load", "session/prompt"), secondAgent.methods)
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
            assertEquals(
                listOf("Old question", "Old answer", "Continue after reconnect", "New answer"),
                transcript(viewModel),
            )
        }

    @Test
    fun failedReloadAfterAgentExitPreservesTheDraftAndExplainsTheFallbackBeforeSending() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val firstAgent = RestoringAgentTransport()
            val secondAgent = RestoringAgentTransport(canLoad = false)
            val agents = ArrayDeque(listOf(firstAgent, secondAgent))
            val viewModel =
                AgentSessionViewModel("/project", fileSystem, transportFactory = { agents.removeFirst() })
                    .also { viewModels += it }
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            firstAgent.inbound.close()
            runCurrent()

            viewModel.dispatch(AgentIntent.SetComposerInput("Keep this draft"))
            viewModel.dispatch(AgentIntent.Send("Keep this draft"))
            runCurrent()

            assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertEquals("Keep this draft", viewModel.state.value.composerInput)
            assertEquals(listOf("initialize"), secondAgent.methods)
            assertEquals(listOf("Old question", "Old answer"), transcript(viewModel))

            viewModel.dispatch(AgentIntent.Send("Keep this draft"))
            runCurrent()
            assertEquals(listOf("Keep this draft", "New answer"), transcript(viewModel))
            assertEquals(record, history.list().single { it.id == record.id })
        }

    @Test
    fun deletingTheSelectedRecordCancelsItsRestoreAndIgnoresTheLateResponse() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(pauseLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            val abandonedLoadId = agent.requests.last().getValue("id")

            viewModel.dispatch(AgentIntent.DeleteSession(record.id))
            runCurrent()
            agent.finishLoad(abandonedLoadId)
            runCurrent()

            assertTrue(
                viewModel.state.value.turns
                    .isEmpty(),
            )
            assertNull(viewModel.state.value.restoration)
            assertTrue(history.list().isEmpty())
        }

    @Test
    fun sendIsIgnoredWhileHistoryIsLoading() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val agent = RestoringAgentTransport(pauseLoad = true)
            val viewModel = viewModel(agent)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("Keep this draft"))
            viewModel.dispatch(AgentIntent.Send("Keep this draft"))
            runCurrent()

            assertFalse(agent.methods.contains("session/prompt"))
            assertEquals("Keep this draft", viewModel.state.value.composerInput)
            agent.finishLoad()
            runCurrent()
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
        }

    @Test
    fun switchingTasksKeepsSeparateDraftsAndSelectionContextAcrossRestart() =
        runTest {
            val first = savedRecord()
            val second = first.copy(id = "second", agentSessionId = "second-session")
            history.save(first)
            history.save(second)
            val viewModel = viewModel(RestoringAgentTransport())
            viewModel.dispatch(AgentIntent.SetComposerInput("New task draft"))
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession(first.id))
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            viewModel.dispatch(AgentIntent.SetComposerInput("First task draft"))
            viewModel.dispatch(AgentIntent.AttachCurrentSelection)
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession(second.id))
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            assertTrue(
                viewModel.state.value.composerAttachments
                    .isEmpty(),
            )
            viewModel.dispatch(AgentIntent.SetComposerInput("Second task draft"))
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession(first.id))
            runCurrent()
            assertEquals("First task draft", viewModel.state.value.composerInput)
            assertEquals(
                "/project/Main.kt",
                viewModel.state.value.composerAttachments
                    .single()
                    .path,
            )
            viewModel.shutdown()

            val restarted = viewModel(RestoringAgentTransport())
            runCurrent()
            assertEquals("New task draft", restarted.state.value.composerInput)
            restarted.dispatch(AgentIntent.RestoreSession(second.id))
            runCurrent()
            assertEquals("Second task draft", restarted.state.value.composerInput)
            restarted.dispatch(AgentIntent.RestoreSession(first.id))
            runCurrent()
            assertEquals("First task draft", restarted.state.value.composerInput)
            assertEquals(
                "/project/Main.kt",
                restarted.state.value.composerAttachments
                    .single()
                    .path,
            )
            restarted.dispatch(AgentIntent.NewChat)
            runCurrent()
            assertEquals("New task draft", restarted.state.value.composerInput)
        }

    @Test
    fun pinAndArchiveSurviveTranscriptUpdatesAndDeletionRemovesNavigation() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val viewModel = viewModel(RestoringAgentTransport())
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            viewModel.dispatch(AgentIntent.SetSessionPinned(record.id, true))
            viewModel.dispatch(AgentIntent.SetSessionArchived(record.id, true))
            runCurrent()
            assertEquals(record.id, viewModel.state.value.activeRecordId)
            assertEquals(SessionRestoration.Resumed, viewModel.state.value.restoration)
            viewModel.dispatch(AgentIntent.Send("Continue this archived task"))
            runCurrent()
            val navigation = AgentNavigationStore(fileSystem, "/project").load()
            assertTrue(navigation.tasks.getValue(record.id).pinned)
            assertTrue(navigation.tasks.getValue(record.id).archived)
            assertTrue(
                history
                    .list()
                    .single()
                    .messages
                    .any { it.text == "Continue this archived task" },
            )
            viewModel.dispatch(AgentIntent.DeleteSession(record.id))
            runCurrent()
            assertFalse(AgentNavigationStore(fileSystem, "/project").load().tasks.containsKey(record.id))
        }

    @Test
    fun submittedDraftDoesNotReappearInNewChatOrOriginalTranscript() =
        runTest {
            val record = savedRecord()
            history.save(record)
            val viewModel = viewModel(RestoringAgentTransport(canLoad = false))
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("A fresh continuation"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("A fresh continuation"))
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            assertEquals(record, history.list().single { it.id == record.id })
        }

    @Test
    fun textTypedDuringInitialConnectionMovesToTheNewConversation() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            val agent = RestoringAgentTransport()
            val viewModel =
                AgentSessionViewModel("/project", fileSystem, transportFactory = {
                    ready.await()
                    agent
                }).also { viewModels += it }
            viewModel.dispatch(AgentIntent.Send("First question"))
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("Follow up draft"))
            runCurrent()
            ready.complete(Unit)
            runCurrent()
            val id = viewModel.state.value.activeRecordId!!
            assertEquals("Follow up draft", viewModel.state.value.composerInput)
            assertEquals(
                "Follow up draft",
                viewModel.state.value.taskNavigation.tasks
                    .getValue(id)
                    .draft.text,
            )
            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()
            assertEquals("", viewModel.state.value.composerInput)
            viewModel.dispatch(AgentIntent.RestoreSession(id))
            runCurrent()
            assertEquals("Follow up draft", viewModel.state.value.composerInput)
        }

    @Test
    fun editingAnUnsentDraftIsSavedWithoutNeedingTaskSwitchOrShutdown() =
        runTest {
            val viewModel = viewModel(RestoringAgentTransport())
            viewModel.dispatch(AgentIntent.SetComposerInput("Persist while typing"))
            advanceUntilIdle()
            assertEquals("Persist while typing", AgentNavigationStore(fileSystem, "/project").load().newChatDraft.text)
        }

    private fun viewModel(agent: RestoringAgentTransport): AgentSessionViewModel =
        AgentSessionViewModel(
            "/project",
            fileSystem,
            transportFactory = { agent },
            editorContext = { EditorSnippet("Main.kt", "/project/Main.kt", "val savedSelection = 1") },
        ).also { viewModels += it }

    private fun savedRecord(): AgentSessionRecord =
        AgentSessionRecord(
            id = "old-record",
            projectPath = "/project",
            agentSessionId = "old-session",
            messages =
                listOf(
                    AgentHistoryMessage(AgentHistoryRole.USER, "Old question"),
                    AgentHistoryMessage(AgentHistoryRole.AGENT, "Old answer"),
                ),
        )

    private fun transcript(viewModel: AgentSessionViewModel): List<String> =
        viewModel.state.value.turns.flatMap { turn ->
            when (turn) {
                is Turn.User -> listOf(turn.text)
                is Turn.Agent -> turn.blocks.filterIsInstance<Block.Text>().map { it.text }
            }
        }
}
