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
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.DiffDecision
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A scripted ACP peer whose prompt stays open until [release]. */
private class CheckpointTransport : AcpTransport {
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
                reply(request, """{"stopReason":"end_turn"}""")
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

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCheckpointTest {
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val viewModels = mutableListOf<AgentSessionViewModel>()
    private val mainDispatcher = StandardTestDispatcher()
    private val opened = mutableListOf<String>()
    private var dirty: (String) -> Boolean = { false }
    private val reloaded = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        opened.clear()
        reloaded.clear()
        dirty = { false }
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
    fun restoreRollsFilesBackAndKeepsTheTranscript() =
        runTest {
            val agent = CheckpointTransport()
            val viewModel = viewModel(agent)
            files["/proj/src/Reader.kt"] = "old"

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            agent.update(
                """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit","status":"completed","content":[{"type":"diff","path":"/proj/src/Reader.kt","oldText":"old","newText":"new"}]}""",
            )
            agent.update(
                """{"sessionUpdate":"tool_call","toolCallId":"run","title":"Test","kind":"execute","status":"completed"}""",
            )
            runCurrent()
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "new"))
            runCurrent()

            val user =
                viewModel.state.value.turns
                    .first() as Turn.User
            assertTrue(user.canRestore)
            assertTrue(viewModel.state.value.isStreaming)
            viewModel.dispatch(AgentIntent.RestoreCheckpoint(user.id))
            runCurrent()
            assertEquals("Wait until the agent finishes before restoring files.", viewModel.state.value.error)
            assertEquals("new", files["/proj/src/Reader.kt"])

            agent.release.complete(Unit)
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreCheckpoint(user.id))
            runCurrent()

            assertEquals("old", files["/proj/src/Reader.kt"])
            assertEquals(2, viewModel.state.value.turns.size)
            val notice =
                (
                    viewModel.state.value.turns
                        .first() as Turn.User
                ).restoreNotice.orEmpty()
            assertTrue(notice.contains("Restored Reader.kt."), notice)
            assertTrue(notice.contains("Commands ran after this message."), notice)
            val diff =
                (viewModel.state.value.turns[1] as Turn.Agent).blocks.filterIsInstance<Block.Diff>().single()
            assertEquals(DiffDecision.REJECTED, diff.decision)
            assertEquals(listOf("/proj/src/Reader.kt"), reloaded)

            viewModel.dispatch(AgentIntent.OpenDiff(diff.id))
            runCurrent()
            assertEquals(listOf("/proj/src/Reader.kt"), opened)
        }

    @Test
    fun handEditedAndDirtyFilesStayOnDisk() =
        runTest {
            val agent = CheckpointTransport()
            val viewModel = viewModel(agent)
            files["/proj/src/Reader.kt"] = "old"
            files["/proj/src/Other.kt"] = "keep"

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "new"))
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Other.kt", "changed"))
            runCurrent()
            files["/proj/src/Reader.kt"] = "typed by hand"
            dirty = { it.endsWith("Other.kt") }
            agent.release.complete(Unit)
            runCurrent()

            val user =
                viewModel.state.value.turns
                    .first() as Turn.User
            viewModel.dispatch(AgentIntent.RestoreCheckpoint(user.id))
            runCurrent()

            assertEquals("typed by hand", files["/proj/src/Reader.kt"])
            assertEquals("changed", files["/proj/src/Other.kt"])
            val notice =
                (
                    viewModel.state.value.turns
                        .first() as Turn.User
                ).restoreNotice.orEmpty()
            assertTrue(notice.contains("Skipped Reader.kt"), notice)
            assertTrue(notice.contains("Skipped Other.kt"), notice)
            assertTrue(reloaded.isEmpty())
        }

    @Test
    fun newChatDropsTheCheckpoint() =
        runTest {
            val agent = CheckpointTransport()
            val viewModel = viewModel(agent)
            files["/proj/src/Reader.kt"] = "old"

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "new"))
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()
            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()
            assertTrue(
                viewModel.state.value.turns
                    .isEmpty(),
            )

            viewModel.dispatch(AgentIntent.Send("Next"))
            runCurrent()
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Other.kt", "created"))
            runCurrent()

            val user =
                viewModel.state.value.turns
                    .first() as Turn.User
            assertEquals("Next", user.text)
            viewModel.dispatch(AgentIntent.RestoreCheckpoint(user.id))
            runCurrent()
            assertEquals("new", files["/proj/src/Reader.kt"])
            assertFalse(files.containsKey("/proj/src/Other.kt"))
        }

    @Test
    fun aReopenedSessionRestoresTheSavedCheckpoint() =
        runTest {
            val agent = CheckpointTransport()
            val viewModel = viewModel(agent)
            files["/proj/src/Reader.kt"] = "old"

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            agent.update(
                """{"sessionUpdate":"tool_call","toolCallId":"run","title":"Test","kind":"execute","status":"completed"}""",
            )
            viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "new"))
            runCurrent()
            agent.release.complete(Unit)
            runCurrent()

            val savedId = viewModel.state.value.activeRecordId
            assertNotNull(savedId)
            val originalId =
                (
                    viewModel.state.value.turns
                        .first() as Turn.User
                ).id

            viewModel.dispatch(AgentIntent.RestoreSession(savedId))
            runCurrent()
            val reopened =
                viewModel.state.value.turns
                    .first() as Turn.User
            assertEquals(originalId, reopened.id)
            assertEquals("Build it", reopened.text)
            assertTrue(reopened.canRestore)
            assertEquals("new", files["/proj/src/Reader.kt"])

            val resumed = viewModel(CheckpointTransport())
            resumed.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            resumed.dispatch(AgentIntent.RestoreSession(savedId))
            runCurrent()
            val user =
                resumed.state.value.turns
                    .first() as Turn.User
            assertEquals(originalId, user.id)
            assertTrue(user.canRestore)
            resumed.dispatch(AgentIntent.RestoreCheckpoint(user.id))
            runCurrent()

            assertEquals("old", files["/proj/src/Reader.kt"])
            val notice =
                (
                    resumed.state.value.turns
                        .first() as Turn.User
                ).restoreNotice.orEmpty()
            assertTrue(notice.contains("Restored Reader.kt."), notice)
            assertTrue(notice.contains("Commands ran after this message."), notice)
        }

    private fun viewModel(agent: CheckpointTransport): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = "/proj",
            fileSystem = fileSystem,
            transportFactory = { agent },
            openInIdeDiff = { opened += it },
            isEditorDirty = { dirty(it) },
            onWorkspaceFilesChanged = { reloaded += it },
        ).also { viewModels += it }
}
