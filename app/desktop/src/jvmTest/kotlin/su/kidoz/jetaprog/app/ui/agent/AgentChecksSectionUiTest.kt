package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
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
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import kotlin.test.Test

/** Evidence remains inspectable in both palettes without expanding the transcript. */
@OptIn(ExperimentalTestApi::class)
class AgentChecksSectionUiTest {
    @Test
    fun darkEvidence() = evidence(dark = true)

    @Test
    fun lightEvidence() = evidence(dark = false)

    private fun evidence(dark: Boolean) =
        runComposeUiTest {
            val tool =
                Block.Tool(
                    "run",
                    "Gradle tests",
                    "execute",
                    "./gradlew test",
                    ToolStatus.OK,
                    "BUILD SUCCESSFUL",
                    false,
                    exitCode = 0,
                    checkRevision = 0,
                    startedAtEpochMillis = 1792756800000,
                )
            setContent {
                JetaProgTheme(darkTheme = dark) {
                    Box(Modifier.size(420.dp, 340.dp).background(LocalIntelliJColors.current.background)) {
                        AgentChecksSection(listOf(AgentCheck(tool, CheckStatus.PASSED)), "/project")
                    }
                }
            }
            onNodeWithText("Checks").assertIsDisplayed().performClick()
            onNodeWithText("Passed · Gradle tests").assertIsDisplayed()
            onNodeWithText("./gradlew test").assertIsDisplayed()
            onNodeWithText("BUILD SUCCESSFUL").assertIsDisplayed()
            onRoot().saveUiArtifacts(if (dark) "agent-checks-dark" else "agent-checks-light")
        }
}
