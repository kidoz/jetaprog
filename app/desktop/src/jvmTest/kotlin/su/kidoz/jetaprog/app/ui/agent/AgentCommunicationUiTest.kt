package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AgentCommunicationUiTest {
    @Test
    fun darkQuestionsKeepComposerAndStopReachable() = question(dark = true)

    @Test
    fun lightQuestionsKeepComposerAndStopReachable() = question(dark = false)

    private fun question(dark: Boolean) =
        runComposeUiTest {
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 640.dp)) {
                        AgentSurfaceContent(
                            AgentUiState(
                                connection = AgentConnection.CONNECTED,
                                agentName = "Test agent",
                                isStreaming = true,
                                turns = listOf(Turn.User("user", "Refactor the reader", "now")),
                                questions =
                                    listOf(
                                        AgentQuestion(
                                            "q",
                                            "Choose the approach",
                                            listOf(
                                                AgentQuestionField(
                                                    "strategy",
                                                    "Strategy",
                                                    null,
                                                    "string",
                                                    true,
                                                    mapOf("safe" to "Conservative", "fast" to "Fast"),
                                                ),
                                            ),
                                        ),
                                    ),
                            ),
                            { intents += it },
                            false,
                        )
                    }
                }
            }
            onNodeWithText("Test agent needs your input").assertIsDisplayed()
            onNodeWithText("Conservative").performScrollTo().performClick()
            assertTrue(AgentIntent.AnswerQuestion("q", "strategy", listOf("safe")) in intents)
            onAllNodesWithText("Send answer").assertCountEquals(1)[0].assertIsDisplayed().performClick()
            onNodeWithText("Decline").performClick()
            onNodeWithText("Cancel question").performClick()
            assertTrue(AgentIntent.ResolveQuestion("q", "accept") in intents)
            assertTrue(AgentIntent.ResolveQuestion("q", "decline") in intents)
            assertTrue(AgentIntent.ResolveQuestion("q", "cancel") in intents)
            onNodeWithText("Stop").assertIsDisplayed()
            onNodeWithText("Waiting for input").assertIsDisplayed()
            onNodeWithContentDescription("Queue message").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-question-$dark")
        }

    @Test
    fun darkReviewCollectsRangeFeedbackBeforeSending() = reviewFeedback(dark = true)

    @Test
    fun lightReviewCollectsRangeFeedbackBeforeSending() = reviewFeedback(dark = false)

    private fun reviewFeedback(dark: Boolean) =
        runComposeUiTest {
            val intents = mutableListOf<AgentIntent>()
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 640.dp).background(LocalIntelliJColors.current.background)) {
                        AgentReviewFeedback(AgentUiState(), listOf("src/Reader.kt"), { intents += it })
                    }
                }
            }
            onNodeWithText("Review feedback (0)").performClick()
            onNodeWithContentDescription("Comment on src/Reader.kt").performClick()
            onNodeWithContentDescription("Line or range (for example 12-18)").performTextInput("12-18")
            onNodeWithContentDescription("Review comment").performTextInput("Handle EOF")
            onNodeWithText("Add comment").performClick()
            assertTrue(AgentIntent.AddReviewComment("src/Reader.kt", "12-18", "Handle EOF") in intents)
            assertTrue(intents.none { it is AgentIntent.Send })
            onRoot().saveUiArtifacts("agent-review-feedback-$dark")
        }
}
