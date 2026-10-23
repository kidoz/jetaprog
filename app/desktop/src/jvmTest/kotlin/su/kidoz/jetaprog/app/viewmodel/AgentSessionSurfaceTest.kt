package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentUiState
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.app.ui.agent.PlanStepStatus
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.nextCycledModeId
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A scripted ACP peer that advertises modes, a plan, and slash commands. */
private class SurfaceAgentTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val requests = mutableListOf<JsonObject>()
    override val incoming: Flow<String> = inbound.receiveAsFlow()

    val methods: List<String>
        get() = requests.map { it.getValue("method").jsonPrimitive.content }

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        requests += request
        val id = request["id"] ?: return
        when (request.getValue("method").jsonPrimitive.content) {
            "initialize" -> {
                reply(id, """{"protocolVersion":1,"agentCapabilities":{"loadSession":false}}""")
            }

            "session/new" -> {
                sessionUpdate(
                    """{"sessionUpdate":"available_commands_update","availableCommands":[{"name":"review","description":"Review the diff"},{"name":"compact"},{"name":" "}]}""",
                )
                reply(
                    id,
                    """{"sessionId":"s1","modes":{"currentModeId":"ask","availableModes":[{"id":"ask","name":"Ask"},{"id":"code","name":"Code"}]}}""",
                )
            }

            "session/prompt" -> {
                sessionUpdate(
                    """{"sessionUpdate":"plan","entries":[{"content":"Discarded step","status":"pending"}]}""",
                )
                sessionUpdate(
                    """{"sessionUpdate":"plan","entries":[{"content":"Read the project","status":"completed"},{"content":"Edit the composer","status":"in_progress"},{"content":"Wait","status":"blocked"}]}""",
                )
                sessionUpdate(
                    """{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Done"}}""",
                )
                reply(id, """{"stopReason":"end_turn"}""")
            }

            "session/set_mode" -> {
                val mode =
                    request
                        .getValue("params")
                        .jsonObject
                        .getValue("modeId")
                        .jsonPrimitive.content
                sessionUpdate("""{"sessionUpdate":"current_mode_update","currentModeId":"$mode"}""")
                reply(id, "null")
            }
        }
    }

    private suspend fun reply(
        id: JsonElement,
        result: String,
    ) {
        inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
    }

    private suspend fun sessionUpdate(update: String) {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s1","update":$update}}""",
        )
    }

    override suspend fun close() {
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionSurfaceTest {
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val viewModels = mutableListOf<AgentSessionViewModel>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
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
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.dispose() }
        Dispatchers.resetMain()
    }

    @Test
    fun sessionReportsModesCommandsAndAReplacingPlanWithoutClearingTheChat() =
        runTest {
            val agent = SurfaceAgentTransport()
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()

            assertEquals(
                "ask",
                viewModel.state.value.mode
                    ?.id,
            )
            assertEquals(
                listOf("ask", "code"),
                viewModel.state.value.availableModes
                    .map { it.id },
            )
            assertEquals(
                listOf("review", "compact"),
                viewModel.state.value.commands
                    .map { it.name },
            )
            assertEquals(
                "Review the diff",
                viewModel.state.value.commands
                    .first()
                    .description,
            )

            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()

            val plan = viewModel.state.value.plan
            assertEquals(listOf("Read the project", "Edit the composer", "Wait"), plan.map { it.content })
            assertEquals(
                listOf(PlanStepStatus.COMPLETED, PlanStepStatus.IN_PROGRESS, PlanStepStatus.UNKNOWN),
                plan.map { it.status },
            )
            assertFalse(plan.any { it.content == "Discarded step" })
            val turns = viewModel.state.value.turns
            assertEquals(2, turns.size)
            assertTrue(turns.first() is Turn.User)

            viewModel.dispatch(AgentIntent.SetMode("code"))
            runCurrent()

            assertEquals(
                "code",
                viewModel.state.value.mode
                    ?.id,
            )
            assertEquals(turns, viewModel.state.value.turns)
            assertTrue(agent.methods.contains("session/set_mode"))
            val modeRequest = agent.requests.last { it.getValue("method").jsonPrimitive.content == "session/set_mode" }
            assertEquals(
                "code",
                modeRequest
                    .getValue("params")
                    .jsonObject
                    .getValue("modeId")
                    .jsonPrimitive.content,
            )

            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()

            assertEquals(emptyList(), viewModel.state.value.plan)
            assertEquals(emptyList(), viewModel.state.value.commands)
            assertEquals(null, viewModel.state.value.mode)
        }

    @Test
    fun cyclingTheModeKeepsTheConversationAndPermissions() =
        runTest {
            val agent = SurfaceAgentTransport()
            val viewModel = viewModel(agent)

            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            viewModel.dispatch(AgentIntent.AttachMention("src/Reader.kt", "Reader.kt"))
            viewModel.dispatch(AgentIntent.SetComposerInput("keep"))
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            runCurrent()

            val before = viewModel.state.value
            assertEquals("ask", before.mode?.id)
            assertTrue(before.turns.isNotEmpty())
            assertTrue(before.plan.isNotEmpty())

            val toCode = checkNotNull(nextCycledModeId(before.mode?.id, before.availableModes))
            viewModel.dispatch(AgentIntent.SetMode(toCode))
            runCurrent()
            val mid = viewModel.state.value
            assertEquals("code", mid.mode?.id)
            assertSameConversation(before, mid)

            val backToAsk = checkNotNull(nextCycledModeId(mid.mode?.id, mid.availableModes))
            viewModel.dispatch(AgentIntent.SetMode(backToAsk))
            runCurrent()
            val after = viewModel.state.value
            assertEquals("ask", after.mode?.id)
            assertSameConversation(before, after)
            assertEquals(
                listOf("code", "ask"),
                agent.requests
                    .filter { it.getValue("method").jsonPrimitive.content == "session/set_mode" }
                    .map {
                        it
                            .getValue("params")
                            .jsonObject
                            .getValue("modeId")
                            .jsonPrimitive.content
                    },
            )
        }

    private fun assertSameConversation(
        before: AgentUiState,
        current: AgentUiState,
    ) {
        assertEquals(before.turns, current.turns)
        assertEquals(before.plan, current.plan)
        assertEquals(before.permissions, current.permissions)
        assertEquals(before.composerInput, current.composerInput)
        assertEquals(before.composerAttachments, current.composerAttachments)
        assertEquals(before.queuedPrompts, current.queuedPrompts)
        assertEquals(before.goal, current.goal)
        assertEquals(before.commands, current.commands)
    }

    private fun viewModel(agent: SurfaceAgentTransport): AgentSessionViewModel =
        AgentSessionViewModel("/project", fileSystem, transportFactory = { agent }).also { viewModels += it }
}
