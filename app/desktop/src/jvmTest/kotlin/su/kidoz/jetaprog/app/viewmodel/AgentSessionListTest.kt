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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.DiffDecision
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A scripted ACP peer whose prompt stays open until [release]. */
private class SessionListTransport(
    private val stopReason: String = "end_turn",
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val release = CompletableDeferred<Unit>()
    override val incoming: Flow<String> = inbound.receiveAsFlow()

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
                release.await()
                reply(request, """{"stopReason":"$stopReason"}""")
            }
        }
    }

    fun update(body: String) {
        inbound.trySend(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s1","update":$body}}""",
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

/** A finished turn stores its diff counts on the session record. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionListTest {
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
    fun pendingReviewSurvivesTranscriptRestoreAndKeepIsPersisted() =
        runTest {
            val agent = SessionListTransport()
            val viewModel =
                AgentSessionViewModel(
                    projectPath = "/proj",
                    fileSystem = fileSystem,
                    transportFactory = { agent },
                ).also { viewModels += it }

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            assertTrue(viewModel.state.value.activeRecordId != null)
            val diff =
                """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                    """"status":"completed","content":[{"type":"diff","path":"/proj/src/Reader.kt",""" +
                    """"oldText":"old","newText":"new"}]}"""
            agent.update(diff)
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            viewModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()

            val record =
                viewModel.state.value.sessionHistory
                    .single()
            assertEquals(viewModel.state.value.activeRecordId, record.id)
            assertEquals("Build it", record.title)
            assertEquals(1, record.addedLines)
            assertEquals(1, record.removedLines)
            assertEquals(AgentSessionStatus.REVIEW_READY, record.status)
            assertEquals(listOf("/proj/src/Reader.kt"), record.pendingReviews.map { it.path })
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            val restored =
                viewModel.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<Block.Diff>()
                    .single()
            assertEquals(DiffDecision.PROPOSED, restored.decision)
            assertTrue(
                viewModel.state.value.reviewCounts
                    .isEmpty(),
            )
            viewModel.dispatch(AgentIntent.SetComposerInput("draft stays"))
            viewModel.dispatch(AgentIntent.ShowReview)
            runCurrent()
            assertEquals("draft stays", viewModel.state.value.composerInput)
            assertTrue(viewModel.state.value.reviewVisible)
            viewModel.dispatch(AgentIntent.DismissReviewFile("/proj/src/Reader.kt"))
            runCurrent()
            assertTrue(
                viewModel.state.value.reviewConflicts
                    .isNotEmpty(),
            )
            viewModel.dispatch(AgentIntent.AcceptReviewFile("/proj/src/Reader.kt"))
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession(record.id))
            runCurrent()
            assertTrue(
                viewModel.state.value.sessionChanges
                    .isEmpty(),
            )
        }

    @Test
    fun pendingSaveCannotRecreateADeletedSession() =
        runTest {
            val saveStarted = CompletableDeferred<Unit>()
            val allowSave = CompletableDeferred<Unit>()
            coEvery { fileSystem.writeText(match { it.contains("/agent-history/") }, any(), any()) } coAnswers {
                saveStarted.complete(Unit)
                allowSave.await()
                files[firstArg()] = secondArg()
                Result.success(Unit)
            }
            val agent = SessionListTransport()
            val viewModel =
                AgentSessionViewModel(
                    projectPath = "/proj",
                    fileSystem = fileSystem,
                    transportFactory = { agent },
                ).also { viewModels += it }
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            val recordId = viewModel.state.value.activeRecordId!!
            agent.release.complete(Unit)
            runCurrent()
            assertTrue(saveStarted.isCompleted)

            viewModel.dispatch(AgentIntent.DeleteSession(recordId))
            runCurrent()
            allowSave.complete(Unit)
            runCurrent()
            viewModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()
            assertTrue(
                viewModel.state.value.sessionHistory
                    .isEmpty(),
            )
        }

    @Test
    fun largeDiffCountsAreNotLimitedByThePreview() =
        runTest {
            val agent = SessionListTransport()
            val viewModel =
                AgentSessionViewModel(
                    projectPath = "/proj",
                    fileSystem = fileSystem,
                    transportFactory = { agent },
                ).also { viewModels += it }
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            val oldText = (1..12).joinToString("\\n") { "old-$it" }
            val newText = (1..14).joinToString("\\n") { "new-$it" }
            agent.update(
                """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                    """"status":"completed","content":[{"type":"diff","path":"/proj/src/Reader.kt",""" +
                    """"oldText":"$oldText","newText":"$newText"}]}""",
            )
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            viewModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()

            val record =
                viewModel.state.value.sessionHistory
                    .single()
            assertEquals(14, record.addedLines)
            assertEquals(12, record.removedLines)
            val diff =
                viewModel.state.value.turns
                    .filterIsInstance<Turn.Agent>()
                    .flatMap { it.blocks }
                    .filterIsInstance<Block.Diff>()
                    .single()
            assertEquals(14, diff.added)
            assertEquals(12, diff.removed)
            assertEquals(10, diff.lines.count { it.kind == su.kidoz.jetaprog.app.ui.agent.DiffLineKind.ADDED })
        }

    @Test
    fun stoppedExecutionIsSavedAndClearedByANewTurn() =
        runTest {
            val agent = SessionListTransport()
            val viewModel =
                AgentSessionViewModel(
                    projectPath = "/proj",
                    fileSystem = fileSystem,
                    transportFactory = { agent },
                ).also { viewModels += it }
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("First"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Stop)
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            assertTrue(viewModel.state.value.interrupted)
            viewModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()
            assertEquals(
                AgentSessionStatus.INTERRUPTED,
                viewModel.state.value.sessionHistory
                    .single()
                    .status,
            )
            viewModel.dispatch(AgentIntent.Send("Continue"))
            runCurrent()
            assertFalse(viewModel.state.value.interrupted)
        }

    @Test
    fun agentCancellationIsNotShownAsNormalCompletion() =
        runTest {
            val agent = SessionListTransport("cancelled")
            agent.release.complete(Unit)
            val viewModel =
                AgentSessionViewModel(
                    projectPath = "/proj",
                    fileSystem = fileSystem,
                    transportFactory = { agent },
                ).also { viewModels += it }
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("First"))
            runCurrent()
            assertTrue(viewModel.state.value.interrupted)
            assertFalse(viewModel.state.value.isStreaming)
        }
}
