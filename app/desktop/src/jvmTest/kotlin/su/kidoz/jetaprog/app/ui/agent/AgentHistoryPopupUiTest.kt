package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
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
    private var deleteRequest: SessionListRow? = null

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

    @Test
    fun rowShowsStatusTimeAndDiffCounts() =
        runComposeUiTest {
            show(
                records = emptyList(),
                rows =
                    listOf(
                        SessionListRow(
                            id = "rec-2",
                            title = "Fix the parser",
                            savedAtEpochMillis = 0,
                            status = AgentSessionStatus.RUNNING,
                            addedLines = 4,
                            removedLines = 1,
                            current = true,
                        ),
                    ),
                now = 5 * 60_000,
            )
            onNodeWithText("Fix the parser").assertIsDisplayed()
            onNodeWithText("Running · 5m").assertIsDisplayed()
            onNodeWithText(sessionDiffCountLabel(4, 1)!!).assertIsDisplayed()
            onRoot().saveUiArtifacts("agent-session-list")
        }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        records: List<AgentSessionRecord>,
        rows: List<SessionListRow>? = null,
        now: Long = 2_000,
    ) {
        val shown =
            rows
                ?: sessionListRows(
                    history = records,
                    activeRecordId = null,
                    turns = emptyList(),
                    isStreaming = false,
                    error = null,
                    now = now,
                )
        setContent {
            JetaProgTheme(darkTheme = true) {
                AgentHistoryPopup(
                    rows = shown,
                    offsetY = 0,
                    now = now,
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
