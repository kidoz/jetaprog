package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Review uses the active checkout and live command evidence in either agent layout. */
@OptIn(ExperimentalTestApi::class)
class AgentReviewEvidenceUiTest {
    @Test
    fun narrowDarkReviewTracksEvidenceFreshness() = reviewEvidence(dark = true, rail = false)

    @Test
    fun expandedLightReviewTracksEvidenceFreshness() = reviewEvidence(dark = false, rail = true)

    private fun reviewEvidence(
        dark: Boolean,
        rail: Boolean,
    ) = runComposeUiTest {
        val state = mutableStateOf(reviewState())
        val intents = mutableListOf<AgentIntent>()
        setContent {
            JetaProgTheme(darkTheme = dark) {
                Box(Modifier.size(if (rail) 1000.dp else 420.dp, 760.dp)) {
                    AgentSurfaceContent(
                        state = state.value,
                        dispatch = {
                            intents += it
                            if (it == AgentIntent.HideReview) state.value = state.value.copy(reviewVisible = false)
                            if (it == AgentIntent.ShowReview) state.value = state.value.copy(reviewVisible = true)
                        },
                        showRail = rail,
                        checkoutLabel = "Main",
                        onSwitchCheckout = {},
                    )
                }
            }
        }
        onNodeWithContentDescription("Active workspace").assertIsDisplayed().assertTextEquals("/project-worktree")
        onNodeWithText("Recorded session changes · 1 file").assertIsDisplayed()
        onAllNodesWithText("Checks").assertCountEquals(1)
        onNodeWithText("Passed").assertIsDisplayed()
        runOnIdle { state.value = state.value.copy(workspaceRevision = 1) }
        onNodeWithText("Stale").assertIsDisplayed()
        onRoot().saveUiArtifacts("agent-review-evidence-${if (dark) "dark-narrow" else "light-wide"}")
        onNodeWithText("Checks").performClick()
        onNodeWithText("Workspace changed or has unsaved edits. Run it again to check the current files.")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Checks").performScrollTo().performClick()
        onNodeWithText("Keep").performScrollTo().performClick()
        assertTrue(AgentIntent.AcceptReviewFile("/project-worktree/Reader.kt") in intents)
        onNodeWithText("Back to conversation").performClick()
        onNodeWithText("Recorded session changes · 1 file").assertDoesNotExist()
        onAllNodesWithText("Checks").assertCountEquals(1)
        onNodeWithText("Stale").assertIsDisplayed()
        onNodeWithText("Review changes (1)").performClick()
        onNodeWithText("Recorded session changes · 1 file").assertIsDisplayed()
        onNodeWithContentDescription("Close review").performScrollTo().performClick()
        onNodeWithText("Recorded session changes · 1 file").assertDoesNotExist()
    }

    @Test
    fun missingWorkspaceNeverUsesTheOtherCheckoutAsItsIdentity() =
        runComposeUiTest {
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(420.dp, 640.dp)) {
                        AgentSurfaceContent(
                            state = reviewState().copy(workspacePath = "", turns = emptyList(), reviewVisible = false),
                            dispatch = {},
                            showRail = false,
                            checkoutLabel = "Worktree",
                            onSwitchCheckout = {},
                        )
                    }
                }
            }
            onNodeWithContentDescription("Active workspace").assertTextEquals("Workspace unavailable")
            onNodeWithText("Not run").assertIsDisplayed()
            onNodeWithText("Checks").performClick()
            onNodeWithText("No command evidence in this session.").assertIsDisplayed()
            onAllNodesWithText("Worktree").assertCountEquals(1)
        }

    private fun reviewState(): AgentUiState =
        AgentUiState(
            connection = AgentConnection.CONNECTED,
            workspacePath = "/project-worktree",
            reviewVisible = true,
            reviewCounts = mapOf("/project-worktree/Reader.kt" to ReviewDelta(1, 0)),
            turns =
                listOf(
                    Turn.Agent(
                        id = "agent",
                        timeLabel = "now",
                        blocks =
                            listOf(
                                Block.Diff(
                                    id = "diff",
                                    fileName = "Reader.kt",
                                    path = ".",
                                    added = 1,
                                    removed = 0,
                                    lines = emptyList(),
                                    decision = DiffDecision.PROPOSED,
                                    absolutePath = "/project-worktree/Reader.kt",
                                    oldText = "",
                                ),
                                Block.Tool(
                                    id = "tests",
                                    name = "Gradle tests",
                                    kind = "execute",
                                    args = "./gradlew test",
                                    status = ToolStatus.OK,
                                    result = "BUILD SUCCESSFUL",
                                    expanded = false,
                                    exitCode = 0,
                                    checkRevision = 0,
                                ),
                            ),
                    ),
                ),
        )
}
