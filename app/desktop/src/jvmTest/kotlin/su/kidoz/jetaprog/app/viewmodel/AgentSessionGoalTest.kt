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
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.GOAL_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_PLAN_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_RESTORE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.goalCheckLabel
import su.kidoz.jetaprog.app.ui.agent.goalContinueLabel
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scripted ACP peer. Prompts at or after [holdFrom] wait for [allow]. */
private class GoalTransport(
    private val planMode: Boolean = false,
    private val holdFrom: Int = Int.MAX_VALUE,
    private val onPrompt: suspend (Int, GoalTransport) -> Unit = { _, _ -> },
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
                val modes =
                    if (planMode) {
                        """"modes":{"currentModeId":"plan","availableModes":[{"id":"plan","name":"Plan"}]},"""
                    } else {
                        ""
                    }
                reply(request, """{$modes"sessionId":"s1"}""")
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
                onPrompt(promptCount, this)
                if (promptCount >= holdFrom) gates.receive()
                reply(request, """{"stopReason":"end_turn"}""")
            }
        }
    }

    suspend fun message(text: String) {
        update("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"$text"}}""")
    }

    suspend fun diff() {
        update(
            """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                """"status":"completed","content":[{"type":"diff","path":"/proj/src/Reader.kt",""" +
                """"oldText":"old","newText":"new"}]}""",
        )
    }

    suspend fun command(exitCode: Int? = 0) {
        update(
            """{"sessionUpdate":"tool_call","toolCallId":"run","title":"Test","kind":"execute","status":"in_progress","rawInput":{"command":"./gradlew test"}}""",
        )
        val raw = exitCode?.let { ""","rawOutput":{"exitCode":$it}""" }.orEmpty()
        update(
            """{"sessionUpdate":"tool_call_update","toolCallId":"run","status":"completed","content":[{"type":"content","content":{"type":"text","text":"BUILD SUCCESSFUL"}}]$raw}""",
        )
        update("""{"sessionUpdate":"tool_call_update","toolCallId":"run","title":"Test finished"}""")
    }

    private suspend fun update(body: String) {
        inbound.send(
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

/** The session goal starts a check after each round and is restored with the chat. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionGoalTest {
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
    fun aConclusiveReplyContinuesAndASuccessfulCommandMeetsTheGoal() =
        runTest {
            val unmet =
                GoalTransport(holdFrom = 3) { count, agent ->
                    if (count == 2) agent.message("All done. The goal is met.")
                }
            val unmetModel = model(unmet)
            connect(unmetModel)
            unmetModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()

            assertEquals(3, unmet.prompts.size)
            assertTrue(unmet.prompts[0].contains("Work toward this goal: Make tests pass"))
            assertTrue(unmet.prompts[1].contains("Check whether this goal is met"))
            assertTrue(unmet.prompts[2].contains("Continue the goal."))
            assertEquals(
                listOf("Make tests pass", goalCheckLabel(1), goalContinueLabel(2)),
                userTexts(unmetModel),
            )
            assertEquals(
                AgentGoalStatus.ACTIVE,
                unmetModel.state.value.goal
                    ?.status,
            )
            assertEquals(
                2,
                unmetModel.state.value.goal
                    ?.iteration,
            )

            // Both scenarios share one history directory. Drop the first session
            // so the met goal is the only record ShowHistory can list.
            files.clear()

            val met =
                GoalTransport { count, agent ->
                    when (count) {
                        1 -> {
                            agent.diff()
                            agent.command()
                        }

                        2 -> {
                            agent.message("GOAL_MET: BUILD SUCCESSFUL")
                        }
                    }
                }
            val metModel = model(met)
            connect(metModel)
            metModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()

            assertEquals(2, met.prompts.size)
            assertEquals(
                AgentGoalStatus.MET,
                metModel.state.value.goal
                    ?.status,
            )
            assertEquals(
                1,
                metModel.state.value.goal
                    ?.iteration,
            )
            metModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()
            assertEquals(
                AgentGoalStatus.MET,
                metModel.state.value.sessionHistory
                    .single()
                    .goal
                    ?.status,
            )
        }

    @Test
    fun completedCommandsExposeExitStatusAndBecomeStaleAfterEdits() =
        runTest {
            val agent = GoalTransport { _, peer -> peer.command() }
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("Run tests"))
            runCurrent()
            val state = viewModel.state.value
            assertEquals(
                su.kidoz.jetaprog.app.ui.agent.CheckStatus.PASSED,
                su.kidoz.jetaprog.app.ui.agent
                    .agentChecks(state.turns, state.workspaceRevision, false)
                    .single()
                    .status,
            )
            viewModel.invalidateCheckEvidence()
            assertEquals(
                su.kidoz.jetaprog.app.ui.agent.CheckStatus.STALE,
                su.kidoz.jetaprog.app.ui.agent
                    .agentChecks(
                        state.turns,
                        viewModel.state.value.workspaceRevision,
                        false,
                    ).single()
                    .status,
            )
        }

    @Test
    fun aFilenameOrLaunchAcknowledgementPausesInsteadOfCompletingAGoal() =
        runTest {
            val agent =
                GoalTransport { count, peer ->
                    if (count == 1) {
                        peer.diff()
                        peer.command(exitCode = null)
                    } else {
                        peer.message("GOAL_MET: Reader.kt")
                    }
                }
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()
            assertEquals(2, agent.prompts.size)
            assertEquals(
                AgentGoalStatus.PAUSED,
                viewModel.state.value.goal
                    ?.status,
            )
            assertTrue(
                viewModel.state.value.goalNotice
                    .orEmpty()
                    .contains("unverified"),
            )
        }

    @Test
    fun planModeAndARunningTurnRefuseANewGoal() =
        runTest {
            val plan = GoalTransport(planMode = true)
            val planModel = model(plan)
            connect(planModel)
            planModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()

            assertNull(planModel.state.value.goal)
            assertEquals(GOAL_PLAN_NOTICE, planModel.state.value.goalNotice)
            assertTrue(plan.prompts.isEmpty())

            val idle = model(GoalTransport())
            connect(idle)
            idle.dispatch(AgentIntent.Send("/goal"))
            runCurrent()
            assertNull(idle.state.value.goal)
            assertEquals(GOAL_EMPTY_NOTICE, idle.state.value.goalNotice)

            val running = GoalTransport(holdFrom = 1)
            val runningModel = model(running)
            connect(runningModel)
            runningModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()
            runningModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()

            assertNull(runningModel.state.value.goal)
            assertNull(
                runningModel.state.value.queuedPrompts
                    .firstOrNull(),
            )
            assertEquals(GOAL_BUSY_NOTICE, runningModel.state.value.goalNotice)
            assertEquals(1, running.prompts.size)
            running.allow()
        }

    @Test
    fun aQueuedFollowUpIsSentBeforeTheGoalCheck() =
        runTest {
            val agent = GoalTransport(holdFrom = 1)
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Also docs"))
            runCurrent()
            assertEquals(
                "Also docs",
                viewModel.state.value.queuedPrompts
                    .firstOrNull()
                    ?.prompt,
            )

            agent.allow()
            runCurrent()
            assertEquals("Also docs", agent.prompts[1])
            assertEquals(2, agent.prompts.size)

            agent.allow()
            runCurrent()
            assertTrue(agent.prompts[2].contains("Check whether this goal is met"))
        }

    @Test
    fun pauseStopsTheLoopAndRestoreBringsTheGoalBack() =
        runTest {
            val agent = GoalTransport(holdFrom = 1)
            val viewModel = model(agent)
            connect(viewModel)
            viewModel.dispatch(AgentIntent.Send("/goal Make tests pass"))
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("/goal pause"))
            runCurrent()
            agent.allow()
            runCurrent()

            assertEquals(
                AgentGoalStatus.PAUSED,
                viewModel.state.value.goal
                    ?.status,
            )
            assertEquals(1, agent.prompts.size)
            viewModel.dispatch(AgentIntent.ShowHistory)
            runCurrent()
            val saved =
                viewModel.state.value.sessionHistory
                    .single()
            assertEquals("Make tests pass", saved.goal?.objective)
            assertEquals(AgentGoalStatus.PAUSED, saved.goal?.status)

            viewModel.dispatch(AgentIntent.NewChat)
            runCurrent()
            assertNull(viewModel.state.value.goal)

            viewModel.dispatch(AgentIntent.RestoreSession(saved.id))
            runCurrent()
            assertEquals(
                "Make tests pass",
                viewModel.state.value.goal
                    ?.objective,
            )
            assertEquals(
                AgentGoalStatus.PAUSED,
                viewModel.state.value.goal
                    ?.status,
            )
            assertEquals(
                1,
                viewModel.state.value.goal
                    ?.iteration,
            )
        }

    @Test
    fun anActiveSavedGoalIsPausedWhenTheSessionIsReopened() =
        runTest {
            val viewModel = model(GoalTransport())
            connect(viewModel)
            files["/proj/.jetaprog/agent-history/g1.json"] =
                """{"id":"g1","title":"Make tests pass","savedAtEpochMillis":10,""" +
                """"messages":[{"role":"USER","text":"Make tests pass"}],""" +
                """"goal":{"objective":"Make tests pass","status":"ACTIVE","elapsedMillis":90000,"iteration":4}}"""
            viewModel.dispatch(AgentIntent.RestoreSession("g1"))
            runCurrent()

            val goal = viewModel.state.value.goal
            assertEquals("Make tests pass", goal?.objective)
            assertEquals(AgentGoalStatus.PAUSED, goal?.status)
            assertEquals(4, goal?.iteration)
            assertEquals(90_000, goal?.elapsedMillis)
            assertNull(goal?.runningSinceEpochMillis)
            assertEquals(GOAL_RESTORE_NOTICE, viewModel.state.value.goalNotice)
        }

    private fun model(agent: GoalTransport): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = "/proj",
            fileSystem = fileSystem,
            transportFactory = { agent },
        ).also { viewModels += it }

    private fun kotlinx.coroutines.test.TestScope.connect(viewModel: AgentSessionViewModel) {
        viewModel.dispatch(AgentIntent.EnsureConnected)
        runCurrent()
    }

    private fun userTexts(viewModel: AgentSessionViewModel): List<String> =
        viewModel.state.value.turns
            .filterIsInstance<Turn.User>()
            .map { it.text }
}
