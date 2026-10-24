package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.agent.AgentConversationView
import su.kidoz.jetaprog.app.agent.AgentNavigationState
import su.kidoz.jetaprog.app.agent.AgentTaskNavigation
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AgentTaskContinuityUiTest {
    @Test
    fun readingPositionSurvivesSwitchAndRecompositionAndJumpResumesFollowing() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val active = mutableStateOf("a")
                val navigation = mutableStateOf(AgentNavigationState())
                val shown = mutableStateOf(true)
                val count = mutableStateOf(30)
                setContent {
                    JetaProgTheme(darkTheme = dark) {
                        Box(Modifier.size(480.dp, 520.dp)) {
                            if (shown.value) {
                                AgentSurfaceContent(
                                    state =
                                        conversation(
                                            active.value,
                                            count.value,
                                        ).copy(taskNavigation = navigation.value),
                                    dispatch = { intent ->
                                        if (intent is AgentIntent.SaveConversationView) {
                                            val old = navigation.value.tasks[intent.recordId] ?: AgentTaskNavigation()
                                            navigation.value =
                                                navigation.value.copy(
                                                    tasks =
                                                        navigation.value.tasks +
                                                            (intent.recordId to old.copy(view = intent.view)),
                                                )
                                        }
                                    },
                                    showRail = false,
                                )
                            }
                        }
                    }
                }
                onNodeWithText("a message 29").assertIsDisplayed()
                onNodeWithContentDescription("Conversation").performTouchInput { swipeDown() }
                onNodeWithContentDescription("Jump to latest").assertIsDisplayed()
                onNodeWithContentDescription("Conversation").performScrollToIndex(4)
                onNodeWithText("a message 4").assertIsDisplayed()
                var position = AgentConversationView()
                runOnIdle {
                    position =
                        navigation.value.tasks
                            .getValue("a")
                            .view
                    assertFalse(position.followOutput)
                    active.value = "b"
                }
                onNodeWithText("b message 29").assertIsDisplayed()
                runOnIdle { active.value = "a" }
                onNodeWithText("a message 4").assertIsDisplayed()
                runOnIdle {
                    assertEquals(
                        position,
                        navigation.value.tasks
                            .getValue("a")
                            .view,
                    )
                }
                runOnIdle { shown.value = false }
                waitForIdle()
                runOnIdle { shown.value = true }
                onNodeWithText("a message 4").assertIsDisplayed()
                runOnIdle { count.value = 31 }
                onNodeWithText("a message 30").assertDoesNotExist()
                onRoot().saveUiArtifacts("agent-reading-position-$dark")
                onNodeWithContentDescription("Jump to latest").performClick()
                onNodeWithText("a message 30").assertIsDisplayed()
                onNodeWithContentDescription("Jump to latest").assertDoesNotExist()
            }
        }
    }

    @Test
    fun scrollingWithinOneLongTurnPausesAndNewTextDoesNotPullTheViewportDown() =
        runComposeUiTest {
            val longTurn = Turn.User("long", (0..80).joinToString("\n") { "Line $it" }, "now")
            val state = mutableStateOf(conversation("a", 0).copy(turns = listOf(longTurn)))
            var position = AgentConversationView()
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 520.dp)) {
                        AgentSurfaceContent(state.value, {
                            if (it is AgentIntent.SaveConversationView) position = it.view
                        }, showRail = false)
                    }
                }
            }
            waitForIdle()
            runOnIdle { assertTrue(position.offset > 0) }
            onNodeWithContentDescription("Conversation").performTouchInput { swipeDown() }
            onNodeWithContentDescription("Jump to latest").assertIsDisplayed()
            var readingOffset = 0
            runOnIdle {
                assertFalse(position.followOutput)
                readingOffset = position.offset
                state.value = state.value.copy(turns = listOf(longTurn.copy(text = longTurn.text + "\nNew output")))
            }
            waitForIdle()
            runOnIdle { assertEquals(readingOffset, position.offset) }
            onNodeWithContentDescription("Jump to latest").performClick()
            waitForIdle()
            runOnIdle {
                assertTrue(position.followOutput)
                assertTrue(position.offset > readingOffset)
            }
        }

    @Test
    fun messageIdentityWinsOverOldIndexAndMissingAnchorsClampSafely() =
        runComposeUiTest {
            val state =
                mutableStateOf(
                    conversation("a", 30).copy(
                        taskNavigation =
                            AgentNavigationState(
                                tasks =
                                    mapOf(
                                        "a" to AgentTaskNavigation(view = AgentConversationView("a-8", 1, 0, false)),
                                        "b" to
                                            AgentTaskNavigation(
                                                view = AgentConversationView("missing", 999, 999, false),
                                            ),
                                    ),
                            ),
                    ),
                )
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 520.dp)) {
                        AgentSurfaceContent(state.value, {}, showRail = false)
                    }
                }
            }
            onNodeWithText("a message 8").assertIsDisplayed()
            runOnIdle { state.value = conversation("b", 3).copy(taskNavigation = state.value.taskNavigation) }
            onNodeWithText("b message 2").assertIsDisplayed()
        }

    @Test
    fun draftSelectionRestoresForIdenticalTextAcrossTasksAndEditingReplacesTheRange() =
        runComposeUiTest {
            val initial =
                AgentUiState(
                    activeRecordId = "a",
                    composerInput = "alpha beta",
                    composerSelectionText = "alpha beta",
                    composerSelection = TextRange(10, 6),
                )
            val state = mutableStateOf(initial)
            setContent {
                JetaProgTheme {
                    Box(Modifier.size(480.dp, 520.dp)) {
                        AgentSurfaceContent(state.value, { intent ->
                            if (intent is AgentIntent.SetComposerInput) {
                                state.value =
                                    state.value.copy(
                                        composerInput = intent.text,
                                        composerSelectionText = intent.text,
                                        composerSelection = TextRange(intent.selectionStart, intent.selectionEnd),
                                    )
                            }
                        }, showRail = false)
                    }
                }
            }
            val field = onNodeWithContentDescription("Agent prompt")
            assertEquals(TextRange(10, 6), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            runOnIdle { state.value = initial.copy(activeRecordId = "b", composerSelection = TextRange(0)) }
            assertEquals(TextRange(0), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            runOnIdle { state.value = initial }
            assertEquals(TextRange(10, 6), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            field.performTextInput("gamma")
            field.assertTextEquals("alpha gamma")
            field.performTextInputSelection(TextRange(0, 5))
            runOnIdle { assertEquals(TextRange(0, 5), state.value.composerSelection) }
        }

    private fun conversation(
        id: String,
        count: Int,
    ): AgentUiState =
        AgentUiState(
            activeRecordId = id,
            workspacePath = "/project",
            turns = (0 until count).map { Turn.User("$id-$it", "$id message $it", "now") },
        )
}
