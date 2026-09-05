package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rendered interaction tests for the agent session History popup: empty state,
 * restore-on-click, and the delete request routed to the host (the confirm
 * overlay is owned by the surface, outside this popup).
 */
@OptIn(ExperimentalTestApi::class)
class AgentHistoryPopupUiTest {
    private val intents = mutableListOf<AgentIntent>()
    private var restoreId: String? = null
    private var deleteRequest: AgentSessionRecord? = null

    private val records =
        listOf(
            AgentSessionRecord(
                id = "rec-2",
                title = "Fix the parser",
                projectPath = "/project",
                savedAtEpochMillis = 2_000,
                messages =
                    listOf(
                        AgentHistoryMessage(AgentHistoryRole.USER, "Fix the parser"),
                        AgentHistoryMessage(AgentHistoryRole.AGENT, "Done."),
                    ),
            ),
            AgentSessionRecord(id = "rec-1", title = "Old chat", savedAtEpochMillis = 1_000),
        )

    @Test
    fun emptyHistoryExplainsItself() =
        runComposeUiTest {
            show(records = emptyList())
            onNodeWithText("No saved sessions yet.").assertIsDisplayed()
        }

    @Test
    fun clickingASessionRestoresIt() =
        runComposeUiTest {
            show(records)
            onNodeWithText("Fix the parser").assertIsDisplayed().performClick()
            runOnIdle {
                assertEquals("rec-2", restoreId)
                assertEquals(listOf<AgentIntent>(AgentIntent.HideHistory), intents)
            }
        }

    @Test
    fun deleteIconRequestsDeletionForTheRightRecord() =
        runComposeUiTest {
            show(records)
            onAllNodesWithContentDescription("Delete session", useUnmergedTree = true)[0].performClick()
            runOnIdle { assertEquals("rec-2", deleteRequest?.id) }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.show(records: List<AgentSessionRecord>) {
        setContent {
            JetaProgTheme(darkTheme = true) {
                AgentHistoryPopup(
                    records = records,
                    offsetY = 0,
                    onRestore = {
                        restoreId = it
                        intents += AgentIntent.HideHistory
                    },
                    onDeleteRequest = { deleteRequest = it },
                    onDismiss = { intents += AgentIntent.HideHistory },
                )
            }
        }
    }
}
