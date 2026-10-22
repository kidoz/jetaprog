package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test

/** Verifies restoration notices in both themes and a narrow docked agent surface. */
@OptIn(ExperimentalTestApi::class)
class AgentSessionRestorationUiTest {
    @Test
    fun loadingDisablesSendAndExplainsTheWait() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(420.dp, 600.dp)) {
                            AgentSurfaceContent(
                                state = restoredState(SessionRestoration.Loading),
                                dispatch = {},
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Restoring conversation…").assertIsDisplayed()
                onNodeWithContentDescription("Send").assertIsNotEnabled()
                onRoot().saveUiArtifacts("agent-restoring-$dark")
            }
        }
    }

    @Test
    fun transcriptOnlyExplainsThatTheNextMessageStartsANewChat() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(420.dp, 600.dp)) {
                            AgentSurfaceContent(
                                state =
                                    restoredState(
                                        SessionRestoration.TranscriptOnly(
                                            "This agent does not support resuming saved conversations.",
                                        ),
                                    ),
                                dispatch = {},
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText(
                    "Transcript only. This agent does not support resuming saved conversations. Your next message starts a new chat.",
                ).assertIsDisplayed()
                onNodeWithText("Old answer").assertIsDisplayed()
                onRoot().saveUiArtifacts("agent-transcript-only-$dark")
            }
        }
    }

    @Test
    fun resumedConversationConfirmsThatAgentContextIsAvailable() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(420.dp, 600.dp)) {
                            AgentSurfaceContent(
                                state = restoredState(SessionRestoration.Resumed),
                                dispatch = {},
                                showRail = false,
                            )
                        }
                    }
                }
                onNodeWithText("Conversation resumed. The agent has the previous context.").assertIsDisplayed()
                onRoot().saveUiArtifacts("agent-resumed-$dark")
            }
        }
    }

    private fun restoredState(restoration: SessionRestoration): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            restoration = restoration,
            composerInput = "Continue",
            turns =
                listOf(
                    Turn.User("user", "Old question", ""),
                    Turn.Agent("agent", "", listOf(Block.Text("reply", "Old answer"))),
                ),
        )
}
