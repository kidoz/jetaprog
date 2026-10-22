package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
                onNodeWithText("Plan").assertIsDisplayed()
                onNodeWithText("Read the project").assertIsDisplayed()
                onNodeWithText("Done").assertIsDisplayed()
                onNodeWithText("Edit the composer").assertIsDisplayed()
                onNodeWithText("In progress").assertIsDisplayed()
                onNodeWithText("Wait").assertIsDisplayed()
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
                onNodeWithText("Steering").assertIsDisplayed()
                onNodeWithText("Add tests").assertIsDisplayed()
                onNodeWithContentDescription("Remove Reader.kt").assertIsDisplayed()
                onNodeWithText("Current file").assertIsDisplayed()
                onNodeWithText("Current selection").assertIsDisplayed()
                onNodeWithText("src/Reader.kt").assertIsDisplayed()
                onNodeWithText("/review").assertDoesNotExist()
                onNodeWithText("Current file").performClick()
                onNodeWithContentDescription("Remove Reader.kt").performClick()
                onNodeWithContentDescription("Remove queued prompt").performClick()
                if (dark) onRoot().saveUiArtifacts("agent-composer-context")
            }
            assertEquals(
                listOf(
                    AgentIntent.EnsureConnected,
                    AgentIntent.AttachCurrentFile,
                    AgentIntent.RemoveAttachment(path = "src/Reader.kt", selection = false),
                    AgentIntent.RemoveQueuedPrompt,
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
            onNodeWithText("resolved").assertIsDisplayed()
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

    private fun composerKeys(keys: KeyInjectionScope.() -> Unit): List<AgentIntent> {
        val intents = mutableListOf<AgentIntent>()
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 320.dp)) {
                        AgentSurfaceContent(
                            state =
                                AgentUiState(
                                    connection = AgentConnection.CONNECTED,
                                    composerInput = "hello",
                                    isStreaming = true,
                                ),
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
            queuedPrompt = QueuedFollowUp(prompt = "Add tests", attachments = emptyList(), steer = true),
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
