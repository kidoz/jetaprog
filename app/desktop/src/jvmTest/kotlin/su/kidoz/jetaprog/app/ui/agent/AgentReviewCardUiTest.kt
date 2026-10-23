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

/** Review decisions and conflict notices remain usable in both themes. */
@OptIn(ExperimentalTestApi::class)
class AgentReviewCardUiTest {
    @Test
    fun cardGroupsAFileAndKeepsItInDarkTheme() = reviewCard(darkTheme = true)

    @Test
    fun cardGroupsAFileAndKeepsItInLightTheme() = reviewCard(darkTheme = false)

    private fun reviewCard(darkTheme: Boolean) =
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
                    conflict = "Revert blocked: this file has unsaved editor changes.",
                )
            val clicks = mutableListOf<String>()
            setContent {
                JetaProgTheme(darkTheme = darkTheme) {
                    Box(Modifier.size(420.dp, 280.dp)) {
                        ReviewCard(
                            files = listOf(file),
                            onAccept = { clicks += "accept:$it" },
                            onDismiss = { clicks += "dismiss:$it" },
                            onClose = { clicks += "close" },
                            onOpen = { clicks += "open:$it" },
                        )
                    }
                }
            }
            onNodeWithText("Review").assertIsDisplayed()
            onNodeWithText("Reader.kt").assertIsDisplayed()
            onNodeWithText("src").assertIsDisplayed()
            onNodeWithText(sessionDiffCountLabel(1, 1).orEmpty()).assertIsDisplayed()
            onNodeWithText("Open file").assertIsDisplayed().performClick()
            onNodeWithText("Keep").performClick()
            onNodeWithText("Revert").assertIsDisplayed()
            onNodeWithText(file.conflict.orEmpty()).assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-review-${if (darkTheme) "dark" else "light"}")
            assertEquals(listOf("open:/proj/src/Reader.kt", "accept:/proj/src/Reader.kt"), clicks)
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
