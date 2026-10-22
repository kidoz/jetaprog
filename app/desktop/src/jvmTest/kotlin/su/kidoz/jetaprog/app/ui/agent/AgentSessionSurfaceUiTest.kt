package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
