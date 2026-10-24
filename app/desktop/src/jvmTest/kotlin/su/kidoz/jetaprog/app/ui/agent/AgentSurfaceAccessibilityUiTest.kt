package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers docked layout, permission accessibility, and conversation scroll ownership. */
@OptIn(ExperimentalTestApi::class)
class AgentSurfaceAccessibilityUiTest {
    @Test
    fun permissionPickerOffersEveryPolicy() {
        PermissionPolicy.entries.forEach { policy ->
            val intents = mutableListOf<AgentIntent>()
            runComposeUiTest {
                setContent {
                    JetaProgTheme {
                        Box(Modifier.size(600.dp, 500.dp)) {
                            AgentSurfaceContent(
                                state = AgentUiState(connection = AgentConnection.CONNECTED),
                                dispatch = { intents += it },
                                showRail = true,
                            )
                        }
                    }
                }
                onNodeWithContentDescription("Edit files permission")
                    .assertIsDisplayed()
                    .assert(hasStateDescription("Ask policy"))
                    .assertWidthIsAtLeast(24.dp)
                    .assertHeightIsAtLeast(24.dp)
                    .performClick()
                onNodeWithContentDescription("Edit files permission: ${policy.testLabel()}")
                    .assertIsDisplayed()
                    .performClick()
            }
            assertEquals(
                listOf(AgentIntent.EnsureConnected, AgentIntent.SetPermission(PermissionKind.EDIT, policy)),
                intents,
            )
        }
    }

    @Test
    fun narrowShortDockedSurfaceKeepsEssentialControlsVisibleInBothThemes() {
        for (dark in listOf(true, false)) {
            for (width in listOf(180, 264)) {
                runComposeUiTest {
                    setContent {
                        JetaProgTheme(darkTheme = dark) {
                            Box(Modifier.size(width.dp, 360.dp)) {
                                AgentSurfaceContent(
                                    state = narrowState(),
                                    dispatch = {},
                                    showRail = false,
                                    checkoutLabel = "Worktree",
                                    onSwitchCheckout = {},
                                    layoutDocked = true,
                                )
                            }
                        }
                    }
                    onNodeWithContentDescription("History").assertIsDisplayed()
                    onNodeWithContentDescription("New chat").assertIsDisplayed()
                    onNodeWithContentDescription("Expand").assertIsDisplayed()
                    onNodeWithContentDescription("Send")
                        .assertIsDisplayed()
                        .assertWidthIsAtLeast(24.dp)
                        .assertHeightIsAtLeast(24.dp)
                    onNodeWithContentDescription("Agent: Claude").assertExists()
                    onNodeWithContentDescription("Session mode: Ask").assertExists()
                    onNodeWithContentDescription("Model: GPT").assertExists()
                    onNodeWithContentDescription("Conversation").assertIsDisplayed()
                    onNodeWithText("Visible transcript").assertIsDisplayed()
                    onRoot().saveUiArtifacts("agent-narrow-$width-$dark")
                }
            }
        }
    }

    @Test
    fun conversationFollowsLatestUntilTheUserScrollsAwayAndResetsWithTheSession() {
        runComposeUiTest {
            val state = mutableStateOf(conversationState())
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 360.dp)) {
                        AgentSurfaceContent(
                            state = state.value,
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            val conversation = onNodeWithContentDescription("Conversation")
            onNodeWithText("Message 19").assertIsDisplayed()
            conversation.assert(hasStateDescription("Following output"))

            conversation.performTouchInput { swipeDown() }
            conversation.assert(hasStateDescription("Scroll paused"))
            state.value =
                state.value.copy(
                    isStreaming = true,
                    turns = state.value.turns + Turn.User(id = "message-20", text = "Message 20", timeLabel = "now"),
                )
            onNodeWithText("Message 20").assertDoesNotExist()

            conversation.performScrollToNode(hasText("Message 20"))
            conversation.performTouchInput { swipeUp() }
            conversation.assert(hasStateDescription("Following output"))
            state.value =
                state.value.copy(
                    turns = state.value.turns + Turn.User(id = "message-21", text = "Message 21", timeLabel = "now"),
                )
            onNodeWithText("Message 21").assertIsDisplayed()

            state.value = conversationState(recordId = "other", prefix = "Other", count = 5)
            onNodeWithText("Other 4").assertIsDisplayed()
            onNodeWithContentDescription("Conversation").assert(hasStateDescription("Following output"))
        }
    }

    @Test
    fun toolHeaderExposesExpandedStateAndInterruptedStatus() {
        val expanded = mutableStateOf(false)
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    ToolCallCard(
                        block =
                            Block.Tool(
                                id = "tool",
                                name = "Read",
                                kind = "read",
                                args = "Reader.kt",
                                status = ToolStatus.INTERRUPTED,
                                result = null,
                                expanded = expanded.value,
                            ),
                        onToggle = { expanded.value = !expanded.value },
                    )
                }
            }
            onNodeWithContentDescription("Read tool call")
                .assert(hasStateDescription("Collapsed"))
                .assertHeightIsAtLeast(24.dp)
                .performClick()
            onNodeWithContentDescription("Read tool call").assert(hasStateDescription("Expanded"))
            onNodeWithContentDescription("Interrupted").assertIsDisplayed()
        }
    }

    private fun narrowState(): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            docked = true,
            composerInput = "Continue",
            agent = AgentOption(id = "claude", name = "Claude"),
            mode = ModeOption(id = "ask", name = "Ask"),
            availableModes = listOf(ModeOption(id = "ask", name = "Ask"), ModeOption(id = "code", name = "Code")),
            model = ModelOption(id = "gpt", displayName = "GPT", shortName = "GPT"),
            availableModels = listOf(ModelOption(id = "gpt", displayName = "GPT", shortName = "GPT")),
            goal =
                AgentGoal(
                    objective = "Keep the transcript visible while this supporting region scrolls",
                    status = AgentGoalStatus.PAUSED,
                    iteration = 1,
                ),
            goalNotice = "A goal notice",
            reviewNotice = "A review notice",
            worktreeNotice = "A worktree notice",
            agentNotice = "An agent notice",
            plan = (1..12).map { PlanStep("Plan step $it", PlanStepStatus.PENDING) },
            turns = listOf(Turn.User(id = "user", text = "Visible transcript", timeLabel = "now")),
        )

    private fun conversationState(
        recordId: String = "record",
        prefix: String = "Message",
        count: Int = 20,
    ): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            activeRecordId = recordId,
            turns =
                (0 until count).map { index ->
                    Turn.User(id = "$recordId-$index", text = "$prefix $index", timeLabel = "now")
                },
        )
}

private fun PermissionPolicy.testLabel(): String =
    when (this) {
        PermissionPolicy.ASK -> "Ask"
        PermissionPolicy.AUTO -> "Auto"
        PermissionPolicy.NEVER -> "Never"
    }
