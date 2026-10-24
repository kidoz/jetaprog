package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Task activity, blocking decisions, and recovery share one persistent status region. */
@OptIn(ExperimentalTestApi::class)
class AgentTaskStatusUiTest {
    @Test
    fun darkTaskLayoutKeepsComposerAndConversationUsable() = taskLayout(dark = true)

    @Test
    fun lightTaskLayoutKeepsComposerAndConversationUsable() = taskLayout(dark = false)

    private fun taskLayout(dark: Boolean) =
        runComposeUiTest {
            val state = mutableStateOf(busyState())
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 640.dp)) {
                        AgentSurfaceContent(state.value, { intents += it }, showRail = false)
                    }
                }
            }
            onAllNodesWithText("Running").assertCountEquals(1)
            onNodeWithText("Stop").assertIsDisplayed().performClick()
            assertTrue(AgentIntent.Stop in intents)
            onNodeWithText("Plan").assertDoesNotExist()
            onNodeWithText("Task details").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Task details").performKeyInput { pressKey(Key.Enter) }
            onNodeWithText("Plan step 12").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Conversation").assertIsDisplayed()
            onNodeWithContentDescription("Queue message").assertIsDisplayed()
            onNodeWithText("Task details").performScrollTo().performClick()
            onRoot().saveUiArtifacts("agent-task-running-$dark")

            val approval =
                Block.Approval(
                    "approval",
                    "Run checks",
                    "./gradlew test",
                    choices = listOf(ApprovalChoice("once", "Allow once", ApprovalChoiceRole.ALLOW_ONCE)),
                )
            runOnIdle {
                state.value =
                    state.value.copy(
                        turns = state.value.turns + Turn.Agent("agent", "now", listOf(approval)),
                        presence = Presence("Editing", "Reader.kt", System.currentTimeMillis()),
                    )
            }
            onNodeWithText("Running").assertDoesNotExist()
            onAllNodesWithText("Waiting for approval").assertCountEquals(1)
            onAllNodesWithText("Stop").assertCountEquals(1)
            onNodeWithText("Review request").performClick()
            onNodeWithText("Hide request").assertIsDisplayed()
            onNodeWithText("Checks").performClick()
            onNodeWithText("Hide request").assertDoesNotExist()
            onNodeWithText("No command evidence in this session.").assertIsDisplayed()
            onNodeWithText("Review request").performClick()
            onNodeWithText("No command evidence in this session.").assertDoesNotExist()
            onAllNodesWithText("Allow once")[1].performScrollTo().assertIsDisplayed().performClick()
            assertTrue(AgentIntent.SelectApproval("approval", "once") in intents)
            onNodeWithContentDescription("Conversation").assertIsDisplayed()
            onNodeWithContentDescription("Queue message").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-task-approval-$dark")
        }

    @Test
    fun failureAndInterruptionReplaceActivityAndExposeRecovery() =
        runComposeUiTest {
            val state = mutableStateOf(busyState())
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(420.dp, 640.dp)) {
                        AgentSurfaceContent(state.value, { intents += it }, showRail = false)
                    }
                }
            }
            runOnIdle {
                state.value =
                    state.value.copy(
                        isStreaming = false,
                        interrupted = true,
                        presence = Presence("Editing", null, System.currentTimeMillis()),
                    )
            }
            onNodeWithText("Interrupted").assertIsDisplayed()
            onNodeWithText("Stop").assertDoesNotExist()
            onNodeWithText("The turn stopped. Send a message to continue.").assertIsDisplayed()
            runOnIdle {
                state.value =
                    state.value.copy(
                        connection = AgentConnection.ERROR,
                        interrupted = false,
                        error = "Agent process exited.",
                    )
            }
            onNodeWithText("Connection failed").assertIsDisplayed()
            onAllNodesWithText("Agent process exited.").assertCountEquals(1)
            onNodeWithText("Retry").performClick()
            assertTrue(intents.count { it == AgentIntent.EnsureConnected } == 2)
        }

    private fun busyState(): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            workspacePath = "/projects/jetaprog",
            activeRecordId = "task",
            isStreaming = true,
            goal = AgentGoal("Improve the agent task workspace"),
            plan = (1..12).map { PlanStep("Plan step $it", PlanStepStatus.PENDING) },
            turns = listOf(Turn.User("user", "Keep the conversation available", "now")),
        )
}
