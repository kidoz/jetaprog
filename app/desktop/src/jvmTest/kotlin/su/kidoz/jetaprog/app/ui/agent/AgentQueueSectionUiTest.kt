package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AgentQueueSectionUiTest {
    private val queue =
        listOf(
            QueuedFollowUp(
                "Add regression tests",
                listOf(ComposerAttachment("Reader.kt", "src/Reader.kt")),
                id = "first",
            ),
            QueuedFollowUp("Document the behavior", emptyList(), id = "second"),
            QueuedFollowUp("Check error handling", emptyList(), id = "third"),
        )

    @Test
    fun editPausesDeliveryAndKeepsResumeDisabledUntilSave() =
        runComposeUiTest {
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 440.dp)) { AgentQueueSection(queue, true, { intents += it }) }
                }
            }
            onNodeWithText("Queue paused (3) · Show").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Queue paused (3) · Show").performKeyInput { pressKey(Key.Enter) }
            onNodeWithContentDescription("Edit queued message 1").performClick()
            onNodeWithContentDescription("Resume queued delivery").assertIsNotEnabled()
            onNodeWithContentDescription("Edit queued message 1").performTextReplacement("Test cancellation")
            onNodeWithText("Reader.kt").assertIsDisplayed()
            onNodeWithContentDescription("Save queued message 1").performClick()
            onNodeWithContentDescription("Resume queued delivery").performClick()
            assertEquals(
                listOf<AgentIntent>(
                    AgentIntent.PauseQueue,
                    AgentIntent.UpdateQueuedPrompt("first", "Test cancellation"),
                    AgentIntent.ResumeQueue,
                ),
                intents,
            )
        }

    @Test
    fun rowActionsUseIdentityInsteadOfPromptText() =
        runComposeUiTest {
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 440.dp)) { AgentQueueSection(queue.take(2), false, { intents += it }) }
                }
            }
            onNodeWithText("Queued (2) · Show").performClick()
            onNodeWithContentDescription("Move queued message 1 earlier").assertIsNotEnabled()
            onNodeWithContentDescription("Move queued message 1 later").performClick()
            onNodeWithContentDescription("Remove queued message 2").performClick()
            assertEquals(
                listOf<AgentIntent>(
                    AgentIntent.MoveQueuedPrompt("first", false),
                    AgentIntent.RemoveQueuedPrompt("second"),
                ),
                intents,
            )
        }

    @Test
    fun dockedQueueDark() = docked(true)

    @Test
    fun dockedQueueLight() = docked(false)

    private fun docked(dark: Boolean) =
        runComposeUiTest {
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(480.dp, 720.dp)) {
                        AgentSurfaceContent(
                            AgentUiState(
                                connection = AgentConnection.CONNECTED,
                                isStreaming = true,
                                presence = Presence("Working", null, System.currentTimeMillis()),
                                turns = listOf(Turn.User("user", "Improve file review", "")),
                                queuedPrompts = queue,
                            ),
                            dispatch = {},
                            showRail = false,
                        )
                    }
                }
            }
            onNodeWithText("Queued (3) · Show").assertIsDisplayed().performClick()
            onNodeWithText("Add regression tests").assertIsDisplayed()
            onNodeWithContentDescription("Move queued message 1 later").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-queue-${if (dark) "dark" else "light"}")
        }
}
