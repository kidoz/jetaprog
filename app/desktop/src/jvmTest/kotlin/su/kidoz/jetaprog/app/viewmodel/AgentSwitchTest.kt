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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AGENT_BUSY_NOTICE
import su.kidoz.jetaprog.app.agent.AGENT_OTHER_BUSY_NOTICE
import su.kidoz.jetaprog.app.agent.AgentCatalogEntry
import su.kidoz.jetaprog.app.agent.AgentChoice
import su.kidoz.jetaprog.app.agent.AgentLaunch
import su.kidoz.jetaprog.app.agent.CLAUDE_FALLBACK_DETAIL
import su.kidoz.jetaprog.app.agent.CODEX_MISSING_DETAIL
import su.kidoz.jetaprog.app.agent.KIMI_MISSING_DETAIL
import su.kidoz.jetaprog.app.agent.ZCODE_BRIDGE_DETAIL
import su.kidoz.jetaprog.app.agent.ZCODE_INSTALL_COMMAND
import su.kidoz.jetaprog.app.agent.ZCODE_INSTALL_COPIED_NOTICE
import su.kidoz.jetaprog.app.agent.ZCODE_INSTALL_COPY_FAILED_NOTICE
import su.kidoz.jetaprog.app.agent.legacyClaudeLaunch
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
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

private class SwitchTransport(
    private val holdPrompt: CompletableDeferred<Unit>? = null,
    private val newSessionResult: String = """{"sessionId":"s1"}""",
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val methods = mutableListOf<String>()
    val selectedModels = mutableListOf<String>()
    var loadedSession: String? = null
    override val incoming: Flow<String> = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        val method = request.getValue("method").jsonPrimitive.content
        methods += method
        val id = request["id"] ?: return
        when (method) {
            "initialize" -> {
                reply(id, """{"protocolVersion":1,"agentCapabilities":{"loadSession":true}}""")
            }

            "session/new" -> {
                reply(id, newSessionResult)
            }

            "session/set_model" -> {
                selectedModels +=
                    request
                        .getValue("params")
                        .jsonObject
                        .getValue("modelId")
                        .jsonPrimitive.content
                reply(id, "null")
            }

            "session/prompt" -> {
                holdPrompt?.await()
                reply(id, """{"stopReason":"end_turn"}""")
            }

            "session/load" -> {
                loadedSession =
                    request
                        .getValue("params")
                        .jsonObject
                        .getValue("sessionId")
                        .jsonPrimitive.content
                reply(id, "{}")
            }

            else -> {
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

    override suspend fun close() {
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSwitchTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val viewModels = mutableListOf<AgentSessionViewModel>()

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
    fun anIdleChatArchivesAndReconnectsOnTheChosenAgent() =
        runTest {
            val configs = mutableListOf<StdioAgentConfig>()
            val viewModel =
                viewModel(configs = configs, installed = listOf(claudeLaunch(), grokLaunch()))
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetPermission(PermissionKind.EDIT, PermissionPolicy.AUTO))
            viewModel.dispatch(AgentIntent.SetComposerInput("keep"))
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()

            viewModel.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()

            val state = viewModel.state.value
            assertEquals("grok", state.agent?.id)
            assertEquals("keep", state.composerInput)
            assertEquals(PermissionPolicy.AUTO, state.permissions[PermissionKind.EDIT])
            assertTrue(state.turns.isEmpty())
            assertEquals(null, state.agentNotice)
            assertEquals(listOf("/bin/grok", "agent", "stdio"), configs.last().command)
            assertEquals("claude", jsonAgentId(files.entries.first { it.key.contains("agent-history") }.value))
            assertEquals("grok", jsonAgentId(files.getValue("/proj/.jetaprog/agent-prefs.json")))
            assertTrue(files.values.any { it.contains("Build it") })
        }

    @Test
    fun aRunningTurnKeepsTheCurrentAgent() =
        runTest {
            val hold = CompletableDeferred<Unit>()
            val configs = mutableListOf<StdioAgentConfig>()
            val viewModel =
                viewModel(
                    configs = configs,
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    transport = SwitchTransport(holdPrompt = hold),
                )
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.Send("Build it"))
            runCurrent()

            viewModel.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()

            assertEquals(AGENT_BUSY_NOTICE, viewModel.state.value.agentNotice)
            assertEquals(
                "claude",
                viewModel.state.value.agent
                    ?.id,
            )
            assertEquals(
                "Build it",
                (
                    viewModel.state.value.turns
                        .first() as Turn.User
                ).text,
            )
            assertEquals(1, configs.size)
            hold.complete(Unit)
            runCurrent()
        }

    @Test
    fun anOlderChatWithoutAnAgentIdStaysOnClaude() =
        runTest {
            files["/proj/.jetaprog/agent-history/old.json"] =
                """{"id":"old","title":"Old","savedAtEpochMillis":1,"agentSessionId":"saved","messages":[{"role":"USER","text":"Build it"}]}"""
            val configs = mutableListOf<StdioAgentConfig>()
            val transports = mutableListOf<SwitchTransport>()
            val viewModel =
                viewModel(configs = configs, installed = listOf(claudeLaunch(), grokLaunch()), transports = transports)
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession("old"))
            runCurrent()

            assertEquals(
                "claude",
                viewModel.state.value.agent
                    ?.id,
            )
            assertEquals(
                "Build it",
                (
                    viewModel.state.value.turns
                        .single() as Turn.User
                ).text,
            )
            assertTrue(transports.last().methods.contains("session/load"))
            assertEquals("/bin/npx", configs.last().command.first())
        }

    @Test
    fun aSavedChatReconnectsToItsOwnAgent() =
        runTest {
            files["/proj/.jetaprog/agent-history/kimi.json"] =
                """{"id":"kimi","title":"Kimi","savedAtEpochMillis":2,"agentSessionId":"kimi-session","agentId":"kimi","messages":[{"role":"USER","text":"Explain"}]}"""
            val configs = mutableListOf<StdioAgentConfig>()
            val transports = mutableListOf<SwitchTransport>()
            val viewModel =
                viewModel(
                    configs = configs,
                    installed = listOf(claudeLaunch(), kimiLaunch()),
                    transports = transports,
                )
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.RestoreSession("kimi"))
            runCurrent()

            assertEquals(
                "kimi",
                viewModel.state.value.agent
                    ?.id,
            )
            assertEquals(listOf("/bin/kimi", "acp"), configs.last().command)
            assertEquals("kimi-session", transports.last().loadedSession)
        }

    @Test
    fun aMissingSavedAgentStaysClosed() =
        runTest {
            files["/proj/.jetaprog/agent-history/kimi.json"] =
                """{"id":"kimi","title":"Kimi","savedAtEpochMillis":2,"agentSessionId":"kimi-session","agentId":"kimi","messages":[{"role":"USER","text":"Explain"}]}"""
            val configs = mutableListOf<StdioAgentConfig>()
            val transports = mutableListOf<SwitchTransport>()
            val viewModel =
                viewModel(configs = configs, installed = listOf(claudeLaunch()), transports = transports)
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            val connects = configs.size
            viewModel.dispatch(AgentIntent.RestoreSession("kimi"))
            runCurrent()

            assertEquals("Kimi is not installed.", viewModel.state.value.agentNotice)
            assertEquals(AgentConnection.DISCONNECTED, viewModel.state.value.connection)
            assertIs<SessionRestoration.TranscriptOnly>(viewModel.state.value.restoration)
            assertEquals(connects, configs.size)
            assertFalse(transports.last().methods.contains("session/load"))
        }

    @Test
    fun theWorktreeCheckoutUsesTheSameAgent() =
        runTest {
            val choice = AgentChoice(legacyClaudeLaunch())
            val mainConfigs = mutableListOf<StdioAgentConfig>()
            val worktreeConfigs = mutableListOf<StdioAgentConfig>()
            lateinit var worktree: AgentSessionViewModel
            val main =
                viewModel(
                    projectPath = "/proj",
                    configs = mainConfigs,
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    choice = choice,
                    onSwitch = { launch ->
                        if (worktree.state.value.isStreaming) {
                            AGENT_OTHER_BUSY_NOTICE
                        } else {
                            worktree.applySharedAgent(launch)
                            null
                        }
                    },
                )
            worktree =
                viewModel(
                    projectPath = "/wt",
                    configs = worktreeConfigs,
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    choice = choice,
                    remembers = false,
                    onSwitch = { launch ->
                        if (main.state.value.isStreaming) {
                            AGENT_OTHER_BUSY_NOTICE
                        } else {
                            main.acceptSharedAgent(launch)
                            null
                        }
                    },
                )
            main.dispatch(AgentIntent.EnsureConnected)
            worktree.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            main.dispatch(AgentIntent.Send("Build it"))
            runCurrent()

            main.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()

            assertEquals(
                "grok",
                main.state.value.agent
                    ?.id,
            )
            assertEquals(
                "grok",
                worktree.state.value.agent
                    ?.id,
            )
            assertEquals(listOf("/bin/grok", "agent", "stdio"), mainConfigs.last().command)
            assertEquals(listOf("/bin/grok", "agent", "stdio"), worktreeConfigs.last().command)
        }

    @Test
    fun aRunningWorktreeRefusesTheChange() =
        runTest {
            val choice = AgentChoice(legacyClaudeLaunch())
            val hold = CompletableDeferred<Unit>()
            lateinit var worktree: AgentSessionViewModel
            val main =
                viewModel(
                    projectPath = "/proj",
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    choice = choice,
                    onSwitch = { launch ->
                        if (worktree.state.value.isStreaming) {
                            AGENT_OTHER_BUSY_NOTICE
                        } else {
                            worktree.applySharedAgent(launch)
                            null
                        }
                    },
                )
            worktree =
                viewModel(
                    projectPath = "/wt",
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    choice = choice,
                    remembers = false,
                    transport = SwitchTransport(holdPrompt = hold),
                    onSwitch = { launch ->
                        if (main.state.value.isStreaming) {
                            AGENT_OTHER_BUSY_NOTICE
                        } else {
                            main.acceptSharedAgent(launch)
                            null
                        }
                    },
                )
            main.dispatch(AgentIntent.EnsureConnected)
            worktree.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            worktree.dispatch(AgentIntent.Send("Keep going"))
            runCurrent()

            main.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()

            assertEquals(AGENT_OTHER_BUSY_NOTICE, main.state.value.agentNotice)
            assertEquals(
                "claude",
                main.state.value.agent
                    ?.id,
            )
            assertEquals(
                "claude",
                worktree.state.value.agent
                    ?.id,
            )
            hold.complete(Unit)
            runCurrent()
        }

    @Test
    fun theMenuShowsTheScanAndAMissingClaudeUsesTheAdapter() =
        runTest {
            val configs = mutableListOf<StdioAgentConfig>()
            val viewModel =
                viewModel(
                    configs = configs,
                    installed = listOf(grokLaunch()),
                    catalog = scanCatalog(),
                )
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            viewModel.dispatch(AgentIntent.SetComposerInput("keep"))
            runCurrent()

            val listed = viewModel.state.value.agentMenu
            assertEquals(listOf("claude", "codex", "grok", "kimi", "zcode"), listed.map { it.id })
            assertEquals(CLAUDE_FALLBACK_DETAIL, listed[0].detail)
            assertTrue(listed[0].selectable)
            assertTrue(listed[0].warning)
            assertEquals(CODEX_MISSING_DETAIL, listed[1].detail)
            assertFalse(listed[1].selectable)
            assertEquals("/bin/grok agent stdio", listed[2].detail)
            assertEquals(KIMI_MISSING_DETAIL, listed[3].detail)
            assertEquals(ZCODE_BRIDGE_DETAIL, listed[4].detail)
            assertEquals(ZCODE_INSTALL_COMMAND, listed[4].installCommand)
            assertEquals(
                listOf("grok"),
                viewModel.state.value.availableAgents
                    .map { it.id },
            )

            viewModel.dispatch(AgentIntent.ReportAgentInstallCopy(copied = false))
            runCurrent()
            assertEquals(ZCODE_INSTALL_COPY_FAILED_NOTICE, viewModel.state.value.agentNotice)
            assertNull(viewModel.state.value.error)

            viewModel.dispatch(AgentIntent.SelectAgent("codex"))
            runCurrent()
            assertEquals(
                "grok",
                viewModel.state.value.agent
                    ?.id,
            )
            assertEquals(ZCODE_INSTALL_COPY_FAILED_NOTICE, viewModel.state.value.agentNotice)
            assertEquals(1, configs.size)

            viewModel.dispatch(AgentIntent.SelectAgent("claude"))
            runCurrent()
            assertEquals(
                "claude",
                viewModel.state.value.agent
                    ?.id,
            )
            assertEquals("keep", viewModel.state.value.composerInput)
            assertEquals(listOf("npx", "-y", "@agentclientprotocol/claude-agent-acp"), configs.last().command)
            assertTrue(configs.last().environment.isEmpty())
            assertNull(viewModel.state.value.agentNotice)
            assertNull(viewModel.state.value.error)

            viewModel.dispatch(AgentIntent.ReportAgentInstallCopy(copied = true))
            runCurrent()
            assertEquals(ZCODE_INSTALL_COPIED_NOTICE, viewModel.state.value.agentNotice)
            assertNull(viewModel.state.value.error)
            assertEquals(
                "claude",
                viewModel.state.value.agent
                    ?.id,
            )
        }

    private fun scanCatalog(): List<AgentCatalogEntry> =
        listOf(
            AgentCatalogEntry(
                id = "claude",
                label = "Claude",
                detail = CLAUDE_FALLBACK_DETAIL,
                launch = legacyClaudeLaunch(),
                warning = true,
            ),
            AgentCatalogEntry(id = "codex", label = "Codex", detail = CODEX_MISSING_DETAIL),
            AgentCatalogEntry(
                id = "grok",
                label = "Grok",
                detail = "/bin/grok agent stdio",
                launch = grokLaunch(),
                countsAsInstalled = true,
            ),
            AgentCatalogEntry(id = "kimi", label = "Kimi", detail = KIMI_MISSING_DETAIL),
            AgentCatalogEntry(
                id = "zcode",
                label = "ZCode",
                detail = ZCODE_BRIDGE_DETAIL,
                installCommand = ZCODE_INSTALL_COMMAND,
            ),
        )

    @Test
    fun eachAgentKeepsTheModelChosenForIt() =
        runTest {
            files["/proj/.jetaprog/agent-prefs.json"] = """{"modelId":"claude-model","docked":false}"""
            val transports = mutableListOf<SwitchTransport>()
            val viewModel =
                viewModel(
                    installed = listOf(claudeLaunch(), grokLaunch()),
                    transports = transports,
                    newSessionResult =
                        """{"sessionId":"s1","models":{"currentModelId":"agent-default",""" +
                            """"availableModels":[{"modelId":"claude-model","name":"Claude model"},""" +
                            """{"modelId":"grok-model","name":"Grok model"}]}}""",
                )
            viewModel.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            assertEquals(listOf("claude-model"), transports.single().selectedModels)
            assertEquals(
                "claude-model",
                viewModel.state.value.model
                    ?.id,
            )

            viewModel.dispatch(AgentIntent.SetModel("missing"))
            runCurrent()
            assertEquals(listOf("claude-model"), transports.single().selectedModels)
            assertNull(viewModel.state.value.error)

            viewModel.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()
            assertEquals(emptyList(), transports.last().selectedModels)
            assertEquals(
                "agent-default",
                viewModel.state.value.model
                    ?.id,
            )

            viewModel.dispatch(AgentIntent.SetModel("grok-model"))
            runCurrent()
            assertEquals(listOf("grok-model"), transports.last().selectedModels)

            viewModel.dispatch(AgentIntent.SelectAgent("claude"))
            runCurrent()
            assertEquals(listOf("claude-model"), transports.last().selectedModels)
            assertEquals(
                "claude-model",
                viewModel.state.value.model
                    ?.id,
            )

            viewModel.dispatch(AgentIntent.SelectAgent("grok"))
            runCurrent()
            assertEquals(listOf("grok-model"), transports.last().selectedModels)
            assertEquals(
                "grok-model",
                viewModel.state.value.model
                    ?.id,
            )
            assertNull(viewModel.state.value.error)

            val saved = files.getValue("/proj/.jetaprog/agent-prefs.json")
            assertTrue(saved.contains("claude-model"))
            assertTrue(saved.contains("grok-model"))
        }

    private fun jsonAgentId(json: String): String? =
        Regex(""""agentId"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    private fun viewModel(
        projectPath: String = "/proj",
        configs: MutableList<StdioAgentConfig> = mutableListOf(),
        installed: List<AgentLaunch>,
        catalog: List<AgentCatalogEntry> = emptyList(),
        choice: AgentChoice = AgentChoice(legacyClaudeLaunch()),
        remembers: Boolean = true,
        transport: SwitchTransport? = null,
        transports: MutableList<SwitchTransport>? = null,
        newSessionResult: String = """{"sessionId":"s1"}""",
        onSwitch: (suspend (AgentLaunch) -> String?)? = null,
    ): AgentSessionViewModel =
        AgentSessionViewModel(
            projectPath = projectPath,
            fileSystem = fileSystem,
            transportFactory = { config ->
                configs += config
                (transport ?: SwitchTransport(newSessionResult = newSessionResult)).also { created ->
                    transports?.add(created)
                }
            },
            discoverAgents = { installed },
            discoverCatalog = { catalog },
            agentChoice = choice,
            onAgentSwitch = onSwitch,
            remembersAgentChoice = remembers,
        ).also { viewModels += it }

    private fun claudeLaunch(): AgentLaunch =
        AgentLaunch(
            id = "claude",
            label = "Claude",
            command = listOf("/bin/npx", "-y", "@agentclientprotocol/claude-agent-acp"),
            environment = mapOf("CLAUDE_CODE_EXECUTABLE" to "/bin/claude"),
        )

    private fun grokLaunch(): AgentLaunch =
        AgentLaunch(id = "grok", label = "Grok", command = listOf("/bin/grok", "agent", "stdio"))

    private fun kimiLaunch(): AgentLaunch =
        AgentLaunch(id = "kimi", label = "Kimi", command = listOf("/bin/kimi", "acp"))
}
