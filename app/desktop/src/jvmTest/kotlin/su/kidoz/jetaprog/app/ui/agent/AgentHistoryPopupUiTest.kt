package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentSessionStatus
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                            hasDraft = true,
                        ),
                    ),
                now = 5 * 60_000,
            )
            onNodeWithText("Fix the parser").assertIsDisplayed()
            onNodeWithText("Running · 5m · Draft").assertIsDisplayed()
            onNodeWithText(sessionDiffCountLabel(4, 1)!!).assertIsDisplayed()
            onNode(isRoot() and hasAnyDescendant(hasText("Fix the parser"))).saveUiArtifacts("agent-session-list")
        }

    @Test
    fun searchesTranscriptTextAndEnterOpensTheMatchingTask() =
        runComposeUiTest {
            show(records)
            val search = onNodeWithContentDescription("Search tasks")
            search.performTextInput("DONE")
            onNodeWithText("Fix the parser").assertIsDisplayed()
            onNodeWithText("Old chat").assertDoesNotExist()
            search.performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals("rec-2", restoreId) }
        }

    @Test
    fun noMatchesAllowsSearchCorrection() =
        runComposeUiTest {
            show(records)
            val search = onNodeWithContentDescription("Search tasks")
            search.performTextInput("nonexistent")
            onNodeWithText("No matching tasks.").assertIsDisplayed()
            search.performTextReplacement("old")
            onNodeWithText("Old chat").assertIsDisplayed()
            onNodeWithText("Fix the parser").assertDoesNotExist()
        }

    @Test
    fun pinArchiveAndUnarchiveDoNotOpenOrDeleteTheTask() =
        runComposeUiTest {
            show(records)
            onAllNodesWithContentDescription("Pin task")[0].performClick()
            onNodeWithContentDescription("Unpin task").assertIsDisplayed()
            onAllNodesWithContentDescription("Archive task")[0].performClick()
            onNodeWithText("Fix the parser").assertDoesNotExist()
            onNodeWithText("Archived").performClick()
            onNodeWithText("Fix the parser").assertIsDisplayed()
            onNodeWithText("Old chat").assertDoesNotExist()
            onNodeWithContentDescription("Unarchive task").performClick()
            onNodeWithText("No archived tasks.").assertIsDisplayed()
            onNodeWithText("Recent").performClick()
            onNodeWithText("Fix the parser").assertIsDisplayed()
            runOnIdle {
                assertNull(restoreId)
                assertNull(deleteRequest)
                assertEquals(
                    listOf(
                        AgentIntent.SetSessionPinned("rec-2", true),
                        AgentIntent.SetSessionArchived("rec-2", true),
                        AgentIntent.SetSessionArchived("rec-2", false),
                    ),
                    intents,
                )
            }
        }

    @Test
    fun taskNavigationRendersInLightTheme() =
        runComposeUiTest {
            show(records, darkTheme = false)
            onAllNodesWithContentDescription("Pin task")[0].performClick()
            onNode(isRoot() and hasAnyDescendant(hasText("Fix the parser"))).saveUiArtifacts("agent-task-history-light")
        }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        records: List<AgentSessionRecord>,
        rows: List<SessionListRow>? = null,
        now: Long = 2_000,
        darkTheme: Boolean = true,
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
            var displayed by remember { mutableStateOf(shown) }
            JetaProgTheme(darkTheme = darkTheme) {
                AgentHistoryPopup(
                    rows = displayed,
                    offsetY = 0,
                    now = now,
                    onRestore = {
                        restoreId = it
                        intents += AgentIntent.HideHistory
                    },
                    onPin = { id, pinned ->
                        intents += AgentIntent.SetSessionPinned(id, pinned)
                        displayed = displayed.map { if (it.id == id) it.copy(pinned = pinned) else it }
                    },
                    onArchive = { id, archived ->
                        intents += AgentIntent.SetSessionArchived(id, archived)
                        displayed = displayed.map { if (it.id == id) it.copy(archived = archived) else it }
                    },
                    onDeleteRequest = { deleteRequest = it },
                    onDismiss = { intents += AgentIntent.HideHistory },
                )
            }
        }
    }
}
