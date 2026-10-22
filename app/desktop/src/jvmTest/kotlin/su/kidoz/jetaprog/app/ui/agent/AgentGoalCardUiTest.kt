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
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The goal card shows the objective, status, elapsed time, and iteration. */
@OptIn(ExperimentalTestApi::class)
class AgentGoalCardUiTest {
    @Test
    fun cardShowsTheObjectiveAndPause() =
        runComposeUiTest {
            val goal =
                AgentGoal(
                    objective = "Make ./gradlew test pass",
                    status = AgentGoalStatus.ACTIVE,
                    elapsedMillis = 0,
                    runningSinceEpochMillis = 0,
                    iteration = 3,
                )
            val now = 5 * 60_000L
            val clicks = mutableListOf<String>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    Box(Modifier.size(420.dp, 220.dp)) {
                        GoalCard(
                            goal = goal,
                            now = now,
                            onPause = { clicks += "pause" },
                            onResume = { clicks += "resume" },
                            onReplace = { clicks += "replace" },
                            onClear = { clicks += "clear" },
                        )
                    }
                }
            }
            onNodeWithText("Goal").assertIsDisplayed()
            onNodeWithText("Make ./gradlew test pass").assertIsDisplayed()
            onNodeWithText(goalMetaLabel(goal, now)).assertIsDisplayed()
            onNodeWithText("Pause").assertIsDisplayed().performClick()
            onNodeWithText("Replace").assertIsDisplayed()
            onNodeWithText("Clear").assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-goal-card")
            assertEquals(listOf("pause"), clicks)
        }
}
