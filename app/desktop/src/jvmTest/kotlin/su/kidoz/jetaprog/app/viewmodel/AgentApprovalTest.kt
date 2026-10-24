package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.PermissionOption
import su.kidoz.jetaprog.acp.protocol.PermissionOptionKind
import su.kidoz.jetaprog.acp.protocol.PermissionOutcome
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.ApprovalChoice
import su.kidoz.jetaprog.app.ui.agent.ApprovalChoiceRole
import su.kidoz.jetaprog.app.ui.agent.Block
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
import kotlin.test.assertTrue

/** A scripted ACP peer whose prompt stays open until [release]. */
private class ApprovalTransport : AcpTransport {
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

/** Allow once and Allow always each return the option id the agent offered. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentApprovalTest {
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
    fun eachOfferedOptionKeepsItsOwnId() =
        runTest {
            val viewModel = streamingViewModel()

            val both =
                async {
                    viewModel.requestPermission(
                        runRequest(
                            "call-both",
                            allowOnce = true,
                            allowAlways = true,
                            rejectOnce = true,
                            rejectAlways = true,
                        ),
                    )
                }
            runCurrent()
            assertEquals(
                listOf(
                    ApprovalChoice("once", "Allow once", ApprovalChoiceRole.ALLOW_ONCE),
                    ApprovalChoice("always", "Allow always", ApprovalChoiceRole.ALLOW_ALWAYS),
                    ApprovalChoice("reject-once", "Reject once", ApprovalChoiceRole.REJECT),
                    ApprovalChoice("reject-always", "Reject always", ApprovalChoiceRole.REJECT),
                ),
                approval(viewModel, "call-both").choices,
            )
            viewModel.dispatch(AgentIntent.SelectApproval("call-both", "always"))
            runCurrent()
            assertEquals(PermissionOutcome.Selected("always"), both.await().outcome)
            assertTrue(approval(viewModel, "call-both").resolved)

            val once =
                async { viewModel.requestPermission(runRequest("call-once", allowOnce = true, rejectOnce = true)) }
            runCurrent()
            assertEquals(
                listOf(
                    ApprovalChoice("once", "Allow once", ApprovalChoiceRole.ALLOW_ONCE),
                    ApprovalChoice("reject-once", "Deny", ApprovalChoiceRole.REJECT),
                ),
                approval(viewModel, "call-once").choices,
            )
            viewModel.dispatch(AgentIntent.SelectApproval("call-once", "once"))
            runCurrent()
            assertEquals(PermissionOutcome.Selected("once"), once.await().outcome)

            val always =
                async {
                    viewModel.requestPermission(
                        runRequest("call-always", allowAlways = true, rejectAlways = true),
                    )
                }
            runCurrent()
            assertEquals(
                listOf(
                    ApprovalChoice("always", "Allow always", ApprovalChoiceRole.ALLOW_ALWAYS),
                    ApprovalChoice("reject-always", "Deny", ApprovalChoiceRole.REJECT),
                ),
                approval(viewModel, "call-always").choices,
            )
            viewModel.dispatch(AgentIntent.SelectApproval("call-always", "reject-always"))
            runCurrent()
            assertEquals(PermissionOutcome.Selected("reject-always"), always.await().outcome)
        }

    @Test
    fun unknownOptionAndTurnEndCancelTheRequest() =
        runTest {
            val agent = ApprovalTransport()
            val viewModel = streamingViewModel(agent)

            val unknown =
                async { viewModel.requestPermission(runRequest("call-unknown", allowOnce = true, rejectOnce = true)) }
            runCurrent()
            viewModel.dispatch(AgentIntent.SelectApproval("call-unknown", "missing"))
            runCurrent()
            assertEquals(PermissionOutcome.Cancelled, unknown.await().outcome)
            assertTrue(approval(viewModel, "call-unknown").resolved)

            val pending =
                async {
                    viewModel.requestPermission(
                        runRequest("call-end", allowOnce = true, allowAlways = true, rejectOnce = true),
                    )
                }
            runCurrent()
            assertFalse(approval(viewModel, "call-end").resolved)
            agent.release.complete(Unit)
            runCurrent()
            assertEquals(PermissionOutcome.Cancelled, pending.await().outcome)
            assertTrue(approval(viewModel, "call-end").resolved)
        }

    @Test
    fun allowAlwaysAuthorizesTheFollowingEdit() =
        runTest {
            val viewModel = streamingViewModel()
            files["/proj/src/Reader.kt"] = "old"
            val response =
                async {
                    viewModel.requestPermission(
                        RequestPermissionRequest(
                            sessionId = "s1",
                            toolCall =
                                PermissionToolCall(
                                    toolCallId = "edit-1",
                                    title = "Edit Reader.kt",
                                    rawInput =
                                        buildJsonObject {
                                            put("file_path", "/proj/src/Reader.kt")
                                            put("content", "new")
                                        },
                                ),
                            options =
                                listOf(
                                    PermissionOption("once", "Allow once", PermissionOptionKind.ALLOW_ONCE),
                                    PermissionOption("always", "Allow always", PermissionOptionKind.ALLOW_ALWAYS),
                                    PermissionOption("reject-once", "Deny", PermissionOptionKind.REJECT_ONCE),
                                ),
                        ),
                    )
                }
            runCurrent()
            viewModel.dispatch(AgentIntent.SelectApproval("edit-1", "always"))
            runCurrent()
            assertEquals(PermissionOutcome.Selected("always"), response.await().outcome)

            val write = launch { viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "new")) }
            runCurrent()
            val wrote = files["/proj/src/Reader.kt"]
            val done = write.isCompleted
            if (!done) write.cancel()
            assertEquals("new", wrote)
            assertTrue(done)
            assertEquals(listOf("edit-1"), approvalIds(viewModel))
        }

    @Test
    fun directFileAccessKeepsApproveAndDeny() =
        runTest {
            val viewModel = streamingViewModel()
            files["/proj/src/Reader.kt"] = "old"
            val write = launch { viewModel.writeTextFile(WriteTextFileRequest("s1", "/proj/src/Reader.kt", "newer")) }
            runCurrent()
            val card = approvalIds(viewModel).single().let { approval(viewModel, it) }
            assertEquals(
                listOf(
                    ApprovalChoice("allow", "Approve & run", ApprovalChoiceRole.ALLOW_ONCE),
                    ApprovalChoice("reject", "Deny", ApprovalChoiceRole.REJECT),
                ),
                card.choices,
            )
            viewModel.dispatch(AgentIntent.SelectApproval(card.id, "allow"))
            runCurrent()
            val wrote = files["/proj/src/Reader.kt"]
            val done = write.isCompleted
            if (!done) write.cancel()
            assertEquals("newer", wrote)
            assertTrue(done)
            assertTrue(approval(viewModel, card.id).resolved)
        }

    @Test
    fun autoAndNeverStillPickOneOptionWithoutACard() =
        runTest {
            val viewModel = streamingViewModel()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.RUN, PermissionPolicy.AUTO))
            runCurrent()
            val allowed =
                viewModel.requestPermission(
                    runRequest("auto", allowOnce = true, allowAlways = true, rejectOnce = true),
                )
            assertEquals(PermissionOutcome.Selected("once"), allowed.outcome)

            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.RUN, PermissionPolicy.NEVER))
            runCurrent()
            val rejected = viewModel.requestPermission(runRequest("never", allowOnce = true, rejectAlways = true))
            assertEquals(PermissionOutcome.Selected("reject-always"), rejected.outcome)
            assertTrue(approvalIds(viewModel).isEmpty())
        }

    @Test
    fun staleSessionCallbacksAreRejectedBeforeAccess() =
        runTest {
            val viewModel = streamingViewModel()
            files["/proj/src/Reader.kt"] = "old"
            val staleWrite = WriteTextFileRequest("old-session", "/proj/src/Reader.kt", "new")
            kotlin.test.assertFailsWith<su.kidoz.jetaprog.acp.protocol.AcpRequestException> {
                viewModel.writeTextFile(staleWrite)
            }
            kotlin.test.assertFailsWith<su.kidoz.jetaprog.acp.protocol.AcpRequestException> {
                viewModel.requestPermission(runRequest("stale").copy(sessionId = "old-session"))
            }
            assertEquals("old", files["/proj/src/Reader.kt"])
            assertTrue(approvalIds(viewModel).isEmpty())
        }

    @Test
    fun writesProtectDirtyEditorsAndReloadCleanBuffers() =
        runTest {
            val path = "/proj/src/Reader.kt"
            files[path] = "old"
            val reloaded = mutableListOf<List<String>>()
            var dirty = true
            val viewModel =
                streamingViewModel(
                    isEditorDirty = { dirty },
                    onWorkspaceFilesChanged = { reloaded += it },
                )
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            runCurrent()
            kotlin.test.assertFailsWith<su.kidoz.jetaprog.acp.protocol.AcpRequestException> {
                viewModel.writeTextFile(WriteTextFileRequest("s1", path, "dirty overwrite"))
            }
            assertEquals("old", files[path])
            assertTrue(reloaded.isEmpty())

            dirty = false
            viewModel.writeTextFile(WriteTextFileRequest("s1", path, "agent content"))
            assertEquals("agent content", files[path])
            assertEquals(listOf(listOf(path)), reloaded)
        }

    private fun TestScope.streamingViewModel(
        agent: ApprovalTransport = ApprovalTransport(),
        isEditorDirty: (String) -> Boolean = { false },
        onWorkspaceFilesChanged: (List<String>) -> Unit = {},
    ): AgentSessionViewModel {
        val viewModel = viewModel(agent, isEditorDirty, onWorkspaceFilesChanged)
        viewModel.dispatch(AgentIntent.EnsureConnected)
        runCurrent()
        viewModel.dispatch(AgentIntent.Send("Build it"))
        runCurrent()
        assertTrue(viewModel.state.value.isStreaming)
        return viewModel
    }

    private fun viewModel(
        agent: ApprovalTransport,
        isEditorDirty: (String) -> Boolean = { false },
        onWorkspaceFilesChanged: (List<String>) -> Unit = {},
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = "/proj",
            fileSystem = fileSystem,
            transportFactory = { agent },
            isEditorDirty = isEditorDirty,
            onWorkspaceFilesChanged = onWorkspaceFilesChanged,
        ).also { viewModels += it }

    private fun approval(
        viewModel: AgentSessionViewModel,
        id: String,
    ): Block.Approval =
        viewModel.state.value.turns
            .filterIsInstance<Turn.Agent>()
            .flatMap { it.blocks }
            .filterIsInstance<Block.Approval>()
            .single { it.id == id }

    private fun approvalIds(viewModel: AgentSessionViewModel): List<String> =
        viewModel.state.value.turns
            .filterIsInstance<Turn.Agent>()
            .flatMap { it.blocks }
            .filterIsInstance<Block.Approval>()
            .map { it.id }

    private fun runRequest(
        callId: String,
        allowOnce: Boolean = false,
        allowAlways: Boolean = false,
        rejectOnce: Boolean = false,
        rejectAlways: Boolean = false,
    ): RequestPermissionRequest {
        val options = mutableListOf<PermissionOption>()
        if (allowOnce) options += PermissionOption("once", "Allow once", PermissionOptionKind.ALLOW_ONCE)
        if (allowAlways) options += PermissionOption("always", "Allow always", PermissionOptionKind.ALLOW_ALWAYS)
        if (rejectOnce) options += PermissionOption("reject-once", "Reject once", PermissionOptionKind.REJECT_ONCE)
        if (rejectAlways) {
            options +=
                PermissionOption("reject-always", "Reject always", PermissionOptionKind.REJECT_ALWAYS)
        }
        return RequestPermissionRequest(
            sessionId = "s1",
            toolCall =
                PermissionToolCall(
                    toolCallId = callId,
                    title = "Run command",
                    rawInput = buildJsonObject { put("command", "gradle test") },
                ),
            options = options,
        )
    }
}
