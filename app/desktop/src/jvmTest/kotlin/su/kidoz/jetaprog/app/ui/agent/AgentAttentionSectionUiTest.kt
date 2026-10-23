package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AgentAttentionSectionUiTest {
    private val approval =
        Block.Approval(
            "permission-2",
            "Run project tests",
            "./gradlew test",
            choices = listOf(ApprovalChoice("allow-once", "Allow once", ApprovalChoiceRole.ALLOW_ONCE)),
        )

    @Test
    fun darkApprovalOpensFromKeyboardAndRoutesExactChoice() = approval(dark = true)

    @Test
    fun lightApprovalOpensFromKeyboardAndRoutesExactChoice() = approval(dark = false)

    private fun approval(dark: Boolean) =
        runComposeUiTest {
            val attention = mutableStateOf(AgentAttention(AgentExecution.RUNNING, approval, 0, null))
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 320.dp)) {
                        AgentAttentionSection(attention.value, { intents += it })
                    }
                }
            }
            onNodeWithText("Waiting for approval").assertIsDisplayed()
            onNodeWithText("Review request").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Review request").performKeyInput { pressKey(Key.Enter) }
            onNodeWithText("Run project tests").assertIsDisplayed()
            onNodeWithText("Allow once").assertIsDisplayed().performClick()
            assertEquals(listOf<AgentIntent>(AgentIntent.SelectApproval("permission-2", "allow-once")), intents)
            onRoot().saveUiArtifacts("agent-attention-${if (dark) "dark" else "light"}")
            runOnIdle { attention.value = attention.value.copy(approval = null) }
            onNodeWithText("Waiting for approval").assertDoesNotExist()
            onNodeWithText("Allow once").assertDoesNotExist()
        }

    @Test
    fun failedAndInterruptedSessionsKeepReviewReachable() =
        runComposeUiTest {
            val attention = mutableStateOf(AgentAttention(AgentExecution.FAILED, null, 2, "Agent process exited."))
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(380.dp, 240.dp)) { AgentAttentionSection(attention.value, { intents += it }) }
                }
            }
            onNodeWithText("Failed").assertIsDisplayed()
            onNodeWithText("Agent process exited.").assertIsDisplayed()
            onNodeWithText("Review changes (2)").performClick()
            assertEquals(listOf<AgentIntent>(AgentIntent.ShowReview), intents)
            runOnIdle { attention.value = attention.value.copy(execution = AgentExecution.INTERRUPTED, failure = null) }
            onNodeWithText("Interrupted").assertIsDisplayed()
            onNodeWithText("Review changes (2)").assertIsDisplayed()
        }

    @Test
    fun dockedApprovalIsReachableInALongTranscriptDark() = docked(dark = true)

    @Test
    fun dockedApprovalIsReachableInALongTranscriptLight() = docked(dark = false)

    private fun docked(dark: Boolean) =
        runComposeUiTest {
            val turns =
                (1..25).map { Turn.Agent("turn-$it", "", listOf(Block.Text("text-$it", "Earlier agent output $it"))) } +
                    Turn.Agent("pending", "", listOf(approval))
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(480.dp, 720.dp)) {
                        AgentSurfaceContent(
                            AgentUiState(
                                connection = AgentConnection.CONNECTED,
                                isStreaming = true,
                                presence = Presence("Running", null, System.currentTimeMillis()),
                                turns = turns,
                            ),
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("Review request").assertIsDisplayed().performClick()
            onNodeWithText("Allow once").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-attention-docked-${if (dark) "dark" else "light"}")
        }
}
