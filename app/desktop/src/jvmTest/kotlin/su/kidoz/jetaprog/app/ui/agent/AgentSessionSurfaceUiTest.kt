package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies the plan card, session-mode chip, and slash-command menu. */
@OptIn(ExperimentalTestApi::class)
class AgentSessionSurfaceUiTest {
    @Test
    fun slashQueryStaysOpenOnlyWhileTheCommandNameIsBeingTyped() {
        assertEquals("", slashCommandQuery("/"))
        assertEquals("re", slashCommandQuery("  /re"))
        assertNull(slashCommandQuery("Explain /review"))
        assertNull(slashCommandQuery("/review the diff"))
        assertNull(slashCommandQuery("/review "))
    }

    @Test
    fun planModeAndSlashCommandsAreShownAndSelectable() {
        for (dark in listOf(true, false)) {
            val intents = mutableListOf<AgentIntent>()
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(480.dp, 640.dp)) {
                            AgentSurfaceContent(
                                state = surfaceState(composerInput = "/re"),
                                dispatch = { intents += it },
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Task details").assertIsDisplayed().performClick()
                onNodeWithText("Plan").assertIsDisplayed()
                onNodeWithText("Read the project").assertIsDisplayed()
                onNodeWithText("Done").assertIsDisplayed()
                onNodeWithText("Edit the composer").assertIsDisplayed()
                onNodeWithText("In progress").assertIsDisplayed()
                onNodeWithText("Wait").performScrollTo().assertIsDisplayed()
                onNodeWithText("Unknown").assertIsDisplayed()
                onNodeWithText("/review").assertIsDisplayed()
                onNodeWithText("Review the diff").assertIsDisplayed()
                onNodeWithText("/compact").assertDoesNotExist()
                onNodeWithText("/review").performClick()
                onNodeWithText("Ask").performClick()
                onNodeWithText("Code").performClick()
                onRoot().saveUiArtifacts("agent-plan-mode-slash-$dark")
            }
            assertEquals(
                listOf(
                    AgentIntent.EnsureConnected,
                    AgentIntent.SetComposerInput("/review "),
                    AgentIntent.SetMode("code"),
                ),
                intents,
            )
        }
    }

    @Test
    fun slashMenuClosesOnceTheCommandHasAnArgument() {
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 640.dp)) {
                        AgentSurfaceContent(
                            state = surfaceState(composerInput = "/review "),
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("/review").assertDoesNotExist()
            onNodeWithText("Review the diff").assertDoesNotExist()
        }
    }

    @Test
    fun mentionQueryReadsTheTrailingAtToken() {
        assertEquals("", mentionQuery("@"))
        assertEquals("Rea", mentionQuery("see @Rea"))
        assertNull(mentionQuery("see @Rea "))
        assertNull(mentionQuery("/review"))
    }

    @Test
    fun mentionMenuAttachmentsAndQueueAreShownAndRemovable() {
        for (dark in listOf(true, false)) {
            val intents = mutableListOf<AgentIntent>()
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(480.dp, 640.dp)) {
                            AgentSurfaceContent(
                                state = composerState(),
                                dispatch = { intents += it },
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Queued (1) · Show").assertIsDisplayed()
                onNodeWithText("Next: Add tests").assertIsDisplayed()
                onNodeWithContentDescription("Remove Reader.kt").assertIsDisplayed()
                onNodeWithText("Current file").assertIsDisplayed()
                onNodeWithText("Current selection").assertIsDisplayed()
                onNodeWithText("src/Reader.kt").assertIsDisplayed()
                onNodeWithText("/review").assertDoesNotExist()
                onNodeWithText("Current file").performClick()
                onNodeWithContentDescription("Remove Reader.kt").performClick()
                onNodeWithText("Queued (1) · Show").performClick()
                onNodeWithContentDescription("Remove queued message 1").performClick()
                if (dark) onRoot().saveUiArtifacts("agent-composer-context")
            }
            assertEquals(
                listOf(
                    AgentIntent.EnsureConnected,
                    AgentIntent.AttachCurrentFile,
                    AgentIntent.RemoveAttachment(path = "src/Reader.kt", selection = false),
                    AgentIntent.RemoveQueuedPrompt("queued-1"),
                ),
                intents,
            )
        }
    }

    @Test
    fun checkpointRestoreAndOpenDiffAreShown() {
        for (dark in listOf(true, false)) {
            val intents = mutableListOf<AgentIntent>()
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(480.dp, 640.dp)) {
                            AgentSurfaceContent(
                                state = checkpointState(streaming = false),
                                dispatch = { intents += it },
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Restore").assertIsDisplayed()
                onNodeWithText("Open").assertIsDisplayed()
                onNodeWithText("Restore").performClick()
                onNodeWithText("Open").performClick()
                if (dark) onRoot().saveUiArtifacts("agent-checkpoint-diff")
            }
            assertEquals(
                listOf(
                    AgentIntent.EnsureConnected,
                    AgentIntent.RestoreCheckpoint("user"),
                    AgentIntent.OpenDiff("d1"),
                ),
                intents,
            )
        }
    }

    @Test
    fun approvalCardOffersEachAllowAndReject() {
        for (dark in listOf(true, false)) {
            val intents = mutableListOf<AgentIntent>()
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(480.dp, 640.dp)) {
                            AgentSurfaceContent(
                                state = approvalState(resolved = false),
                                dispatch = { intents += it },
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("requires approval").assertIsDisplayed()
                onNodeWithText("\$ gradle test").assertIsDisplayed()
                onNodeWithText("Allow once").assertIsDisplayed()
                onNodeWithText("Allow always").assertIsDisplayed()
                onNodeWithText("Reject once").assertIsDisplayed()
                onNodeWithText("Reject always").assertIsDisplayed()
                onNodeWithText("Approve & run").assertDoesNotExist()
                onNode(hasSetTextAction()).assertIsDisplayed()
                onNodeWithText("Allow once").performClick()
                onNodeWithText("Allow always").performClick()
                onNodeWithText("Reject once").performClick()
                onNodeWithText("Reject always").performClick()
                if (dark) onRoot().saveUiArtifacts("agent-approval-choices")
            }
            assertEquals(
                listOf(
                    AgentIntent.EnsureConnected,
                    AgentIntent.SelectApproval("call-1", "once"),
                    AgentIntent.SelectApproval("call-1", "always"),
                    AgentIntent.SelectApproval("call-1", "reject-once"),
                    AgentIntent.SelectApproval("call-1", "reject-always"),
                ),
                intents,
            )
        }
    }

    @Test
    fun resolvedApprovalHidesItsChoices() {
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 320.dp)) {
                        AgentSurfaceContent(
                            state = approvalState(resolved = true),
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("resolved").performScrollTo().assertIsDisplayed()
            onNodeWithText("Allow once").assertDoesNotExist()
            onNodeWithText("Allow always").assertDoesNotExist()
        }
    }

    @Test
    fun restoreIsHiddenWhileATurnIsStreaming() {
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 320.dp)) {
                        AgentSurfaceContent(
                            state = checkpointState(streaming = true),
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("Restore").assertDoesNotExist()
        }
    }

    @Test
    fun shiftTabCyclesTheModeChipAndWraps() {
        val selected = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                var state by remember { mutableStateOf(surfaceState("hello")) }
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 640.dp)) {
                        AgentSurfaceContent(
                            state = state,
                            dispatch = { intent ->
                                if (intent is AgentIntent.SetMode) {
                                    selected += intent.modeId
                                    val mode = state.availableModes.first { it.id == intent.modeId }
                                    state = state.copy(mode = mode)
                                }
                            },
                            showRail = false,
                        )
                    }
                }
            }
            val field = onNode(hasSetTextAction())
            field.performClick()
            field.performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            onNodeWithText("Code").assertIsDisplayed()
            onNodeWithText("hello").assertIsDisplayed()
            field.performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            onNodeWithText("Ask").assertIsDisplayed()
            onNodeWithText("Code").assertDoesNotExist()
        }
        assertEquals(listOf("code", "ask"), selected)
    }

    @Test
    fun tabAndASingleModeLeaveTheChip() {
        val plainTab = composerKeys(surfaceState("hello")) { pressKey(Key.Tab) }
        assertFalse(plainTab.any { it is AgentIntent.SetMode })

        val onlyAsk =
            AgentUiState(
                connection = AgentConnection.CONNECTED,
                composerInput = "hello",
                mode = ModeOption(id = "ask", name = "Ask"),
                availableModes = listOf(ModeOption(id = "ask", name = "Ask")),
            )
        val shiftTab =
            composerKeys(onlyAsk) {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
        assertFalse(shiftTab.any { it is AgentIntent.SetMode })
    }

    @Test
    fun enterSendsShiftEnterDoesNotAndCtrlEnterSteers() {
        val enter = composerKeys { pressKey(Key.Enter) }
        assertTrue(enter.contains(AgentIntent.Send("hello")), enter.toString())

        val shiftEnter =
            composerKeys {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Enter)
                keyUp(Key.ShiftLeft)
            }
        assertFalse(shiftEnter.any { it is AgentIntent.Send || it is AgentIntent.Steer })

        val steer =
            composerKeys {
                keyDown(Key.CtrlLeft)
                pressKey(Key.Enter)
                keyUp(Key.CtrlLeft)
            }
        assertTrue(steer.contains(AgentIntent.Steer("hello")))
    }

    private fun composerKeys(
        state: AgentUiState =
            AgentUiState(
                connection = AgentConnection.CONNECTED,
                composerInput = "hello",
                isStreaming = true,
            ),
        keys: KeyInjectionScope.() -> Unit,
    ): List<AgentIntent> {
        val intents = mutableListOf<AgentIntent>()
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 640.dp)) {
                        AgentSurfaceContent(
                            state = state,
                            dispatch = { intents += it },
                            showRail = false,
                        )
                    }
                }
            }
            onNode(hasSetTextAction()).performClick().performKeyInput(keys)
        }
        return intents
    }

    private fun approvalState(resolved: Boolean): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            isStreaming = true,
            turns =
                listOf(
                    Turn.User(id = "user", text = "Run the tests", timeLabel = "now"),
                    Turn.Agent(
                        id = "agent",
                        timeLabel = "now",
                        blocks =
                            listOf(
                                Block.Approval(
                                    id = "call-1",
                                    title = "Run command",
                                    command = "gradle test",
                                    resolved = resolved,
                                    choices =
                                        listOf(
                                            ApprovalChoice("once", "Allow once", ApprovalChoiceRole.ALLOW_ONCE),
                                            ApprovalChoice("always", "Allow always", ApprovalChoiceRole.ALLOW_ALWAYS),
                                            ApprovalChoice("reject-once", "Reject once", ApprovalChoiceRole.REJECT),
                                            ApprovalChoice(
                                                "reject-always",
                                                "Reject always",
                                                ApprovalChoiceRole.REJECT,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    @Test
    fun theAgentChipListsInstalledAgentsAndSelectsOne() {
        val intents = mutableListOf<AgentIntent>()
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 640.dp)) {
                        AgentSurfaceContent(
                            state =
                                surfaceState(composerInput = "keep").copy(
                                    agent = AgentOption(id = "claude", name = "Claude"),
                                    agentMenu =
                                        listOf(
                                            AgentMenuRow(
                                                id = "claude",
                                                name = "Claude",
                                                detail = "/bin/claude",
                                                selectable = true,
                                            ),
                                            AgentMenuRow(
                                                id = "grok",
                                                name = "Grok",
                                                detail = "/bin/grok agent stdio",
                                                selectable = true,
                                            ),
                                        ),
                                    agentNotice = "Finish the running task before changing the agent.",
                                ),
                            dispatch = { intents += it },
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("Finish the running task before changing the agent.").assertIsDisplayed()
            onNodeWithText("Claude").performClick()
            onNodeWithText(AGENT_SCAN_FOOTER).assertIsDisplayed()
            onNodeWithText("/bin/grok agent stdio").assertIsDisplayed()
            onNodeWithText("Grok").performClick()
        }
        assertEquals(
            listOf(
                AgentIntent.EnsureConnected,
                AgentIntent.RefreshAgents,
                AgentIntent.SelectAgent("grok"),
            ),
            intents,
        )
    }

    @Test
    fun theAgentMenuCopiesTheZcodeInstallCommandAndLeavesMissingRowsAlone() {
        val intents = mutableListOf<AgentIntent>()
        val copied = mutableListOf<String>()
        val original = copyAgentInstallText
        copyAgentInstallText = { text ->
            copied += text
            true
        }
        try {
            runComposeUiTest {
                setContent {
                    JetaProgTheme {
                        Box(Modifier.size(520.dp, 720.dp)) {
                            AgentSurfaceContent(
                                state =
                                    surfaceState(composerInput = "").copy(
                                        agent = AgentOption(id = "claude", name = "Claude"),
                                        agentMenu =
                                            listOf(
                                                AgentMenuRow(
                                                    id = "claude",
                                                    name = "Claude",
                                                    detail = "/bin/claude",
                                                    selectable = true,
                                                ),
                                                AgentMenuRow(
                                                    id = "codex",
                                                    name = "Codex",
                                                    detail = "codex was not found.",
                                                    selectable = false,
                                                ),
                                                AgentMenuRow(
                                                    id = "zcode",
                                                    name = "ZCode",
                                                    detail = "App found. Needs zcode-acp-server.",
                                                    selectable = false,
                                                    installCommand = "npm install -g zcode-acp-server",
                                                ),
                                            ),
                                    ),
                                dispatch = { intents += it },
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Claude").performClick()
                onNodeWithText("codex was not found.").performClick()
                onNodeWithText("Copy install command").assertIsDisplayed().performClick()
                onNodeWithText("Install").assertDoesNotExist()
            }
        } finally {
            copyAgentInstallText = original
        }
        assertEquals(listOf("npm install -g zcode-acp-server"), copied)
        assertEquals(
            listOf(
                AgentIntent.EnsureConnected,
                AgentIntent.RefreshAgents,
                AgentIntent.ReportAgentInstallCopy(copied = true),
            ),
            intents,
        )
    }

    @Test
    fun readyAgentRowsStayAheadOfRowsThatCannotLaunch() {
        val (ready, blocked) =
            splitAgentMenu(
                listOf(
                    AgentMenuRow(id = "claude", name = "Claude", detail = "adapter", selectable = true, warning = true),
                    AgentMenuRow(id = "codex", name = "Codex", detail = "missing", selectable = false),
                    AgentMenuRow(id = "grok", name = "Grok", detail = "/bin/grok agent stdio", selectable = true),
                ),
            )
        assertEquals(listOf("claude", "grok"), ready.map { it.id })
        assertEquals(listOf("codex"), blocked.map { it.id })
    }

    private fun checkpointState(streaming: Boolean): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            isStreaming = streaming,
            turns =
                listOf(
                    Turn.User(id = "user", text = "Build it", timeLabel = "now", canRestore = true),
                    Turn.Agent(
                        id = "agent",
                        timeLabel = "now",
                        blocks =
                            listOf(
                                Block.Diff(
                                    id = "d1",
                                    fileName = "Reader.kt",
                                    path = "src",
                                    added = 1,
                                    removed = 0,
                                    lines = listOf(DiffLine(1, '+', "val x = 1", DiffLineKind.ADDED)),
                                    decision = DiffDecision.PROPOSED,
                                    absolutePath = "/proj/src/Reader.kt",
                                    oldText = "",
                                ),
                            ),
                    ),
                ),
        )

    private fun composerState(): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            isStreaming = true,
            composerInput = "@Rea",
            composerAttachments = listOf(ComposerAttachment(label = "Reader.kt", path = "src/Reader.kt")),
            queuedPrompts =
                listOf(
                    QueuedFollowUp(prompt = "Add tests", attachments = emptyList(), steer = true, id = "queued-1"),
                ),
            mentionFiles = listOf(MentionMatch(label = "Reader.kt", path = "src/Reader.kt")),
            commands = listOf(AgentCommand(name = "review", description = "Review the diff")),
            turns = listOf(Turn.User(id = "user", text = "Build it", timeLabel = "")),
        )

    private fun surfaceState(composerInput: String): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            composerInput = composerInput,
            mode = ModeOption(id = "ask", name = "Ask"),
            availableModes =
                listOf(
                    ModeOption(id = "ask", name = "Ask"),
                    ModeOption(id = "code", name = "Code"),
                ),
            commands =
                listOf(
                    AgentCommand(name = "review", description = "Review the diff"),
                    AgentCommand(name = "compact", description = "Compact the conversation"),
                ),
            plan =
                listOf(
                    PlanStep("Read the project", PlanStepStatus.COMPLETED),
                    PlanStep("Edit the composer", PlanStepStatus.IN_PROGRESS),
                    PlanStep("Wait", PlanStepStatus.UNKNOWN),
                ),
            turns = listOf(Turn.User(id = "user", text = "Build it", timeLabel = "")),
        )
}
