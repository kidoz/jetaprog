package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The review card lists each changed file with Accept and Dismiss. */
@OptIn(ExperimentalTestApi::class)
class AgentReviewCardUiTest {
    @Test
    fun cardGroupsAFileAndAcceptsIt() =
        runComposeUiTest {
            val file =
                ReviewFile(
                    key = "/proj/src/Reader.kt",
                    fileName = "Reader.kt",
                    pathLabel = "src",
                    added = 1,
                    removed = 1,
                    pendingBlockIds = listOf("diff"),
                    statusLabel = null,
                )
            val clicks = mutableListOf<String>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    Box(Modifier.size(420.dp, 220.dp)) {
                        ReviewCard(
                            files = listOf(file),
                            onAccept = { clicks += "accept:$it" },
                            onDismiss = { clicks += "dismiss:$it" },
                            onClose = { clicks += "close" },
                        )
                    }
                }
            }
            onNodeWithText("Review").assertIsDisplayed()
            onNodeWithText("Reader.kt").assertIsDisplayed()
            onNodeWithText("src").assertIsDisplayed()
            onNodeWithText(sessionDiffCountLabel(1, 1).orEmpty()).assertIsDisplayed()
            onNodeWithText("Accept").performClick()
            onNodeWithText("Dismiss").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-review-card")
            assertEquals(listOf("accept:/proj/src/Reader.kt"), clicks)
        }

    @Test
    fun checkoutChipSwitchesToTheOtherSession() =
        runComposeUiTest {
            var switches = 0
            setContent {
                JetaProgTheme(darkTheme = true) {
                    Box(Modifier.size(480.dp, 240.dp)) {
                        AgentSurfaceContent(
                            state = AgentUiState(connection = AgentConnection.CONNECTED),
                            dispatch = {},
                            showRail = false,
                            checkoutLabel = "Worktree",
                            onSwitchCheckout = { switches += 1 },
                        )
                    }
                }
            }
            onNodeWithText("Worktree").assertIsDisplayed().performClick()
            onNodeWithContentDescription("Close review").assertDoesNotExist()
            assertEquals(1, switches)
        }
}
