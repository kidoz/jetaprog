package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises editable drafts and keyboard submission through a state round trip. */
@OptIn(ExperimentalTestApi::class)
class AgentComposerInputUiTest {
    @Test
    fun namedFieldEditsMultilineUnicodeAndSendsInBothThemes() {
        for (dark in listOf(true, false)) {
            runComposeUiTest {
                val intents = composer(dark = dark)
                val field = onNodeWithContentDescription("Agent prompt")
                field.assertIsDisplayed().performClick().assertIsFocused()
                field.performTextInput("Explain café and 解析")
                field.assertTextEquals("Explain café and 解析")
                field.performTextReplacement("Review the parser")
                field.performKeyInput {
                    keyDown(Key.ShiftLeft)
                    pressKey(Key.Enter)
                    keyUp(Key.ShiftLeft)
                }
                field.performTextInput("Add regression tests")
                field.assertTextEquals("Review the parser\nAdd regression tests")
                assertTrue(intents.none { it is AgentIntent.Send || it is AgentIntent.Steer })
                onNodeWithContentDescription("Send").assertIsEnabled()
                field.performKeyInput { pressKey(Key.Enter) }
                assertEquals(
                    listOf(AgentIntent.Send("Review the parser\nAdd regression tests")),
                    intents.filterIsInstance<AgentIntent.Send>(),
                )
            }
        }
    }

    @Test
    fun editedRunningDraftCanQueueOrSteerWithEitherPlatformModifier() {
        for (modifier in listOf(Key.CtrlLeft, Key.MetaLeft)) {
            runComposeUiTest {
                val intents = composer(AgentUiState(connection = AgentConnection.CONNECTED, isStreaming = true))
                val field = onNodeWithContentDescription("Agent prompt")
                field.performClick().performTextInput("  Check the failing test  ")
                onNodeWithContentDescription("Queue message").assertIsEnabled().performClick()
                field.performClick().performTextReplacement("Focus on cancellation")
                field.performKeyInput {
                    keyDown(modifier)
                    pressKey(Key.Enter)
                    keyUp(modifier)
                }
                assertEquals(
                    listOf(AgentIntent.Send("Check the failing test")),
                    intents.filterIsInstance<AgentIntent.Send>(),
                )
                assertEquals(
                    listOf(AgentIntent.Steer("Focus on cancellation")),
                    intents.filterIsInstance<AgentIntent.Steer>(),
                )
            }
        }
    }

    @Test
    fun emptyDraftDoesNotSubmitAndRestoringSessionKeepsAnEditableDraft() {
        for (restoration in listOf(null, SessionRestoration.Loading)) {
            runComposeUiTest {
                val intents = composer(AgentUiState(connection = AgentConnection.CONNECTED, restoration = restoration))
                val field = onNodeWithContentDescription("Agent prompt")
                onNodeWithContentDescription("Send").assertIsNotEnabled()
                field.performClick().performKeyInput { pressKey(Key.Enter) }
                if (restoration != null) {
                    field.performTextInput("Continue once restored")
                    field.assertTextEquals("Continue once restored")
                    onNodeWithContentDescription("Send").assertIsNotEnabled()
                    field.performKeyInput {
                        pressKey(Key.Enter)
                        keyDown(Key.MetaLeft)
                        pressKey(Key.Enter)
                        keyUp(Key.MetaLeft)
                    }
                }
                assertTrue(intents.none { it is AgentIntent.Send || it is AgentIntent.Steer })
            }
        }
    }

    private fun ComposeUiTest.composer(
        initial: AgentUiState = AgentUiState(connection = AgentConnection.CONNECTED),
        dark: Boolean = true,
    ): List<AgentIntent> {
        val state = mutableStateOf(initial)
        val intents = mutableListOf<AgentIntent>()
        setContent {
            JetaProgTheme(darkTheme = dark) {
                Box(Modifier.size(480.dp, 640.dp)) {
                    AgentSurfaceContent(
                        state = state.value,
                        dispatch = { intent ->
                            intents += intent
                            if (intent is AgentIntent.SetComposerInput) {
                                state.value = state.value.copy(composerInput = intent.text)
                            }
                        },
                        showRail = false,
                    )
                }
            }
        }
        return intents
    }
}
