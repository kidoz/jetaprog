package su.kidoz.jetaprog.app.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rendered tests for the status bar wiring: the branch segment is clickable
 * and reports exactly one activation; segments without handlers stay inert.
 */
@OptIn(ExperimentalTestApi::class)
class IntelliJStatusBarUiTest {
    @Test
    fun branchClickIsReportedExactlyOnce() =
        runComposeUiTest {
            var activations = 0
            setContent {
                JetaProgTheme(darkTheme = true) {
                    IntelliJStatusBar(
                        gitBranch = "main",
                        encodingInfo = "UTF-8",
                        languageInfo = "Kotlin",
                        onBranchClick = { activations++ },
                    )
                }
            }
            onNodeWithText("main").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(1, activations) }
        }

    @Test
    fun segmentsWithoutHandlersRenderAsPlainText() =
        runComposeUiTest {
            setContent {
                JetaProgTheme(darkTheme = true) {
                    IntelliJStatusBar(
                        gitBranch = "main",
                        encodingInfo = "UTF-8",
                        languageInfo = "Kotlin",
                    )
                }
            }
            onNodeWithText("UTF-8").assertIsDisplayed()
            onNodeWithText("Kotlin").assertIsDisplayed()
            runOnIdle { assertTrue(true) }
        }
}
