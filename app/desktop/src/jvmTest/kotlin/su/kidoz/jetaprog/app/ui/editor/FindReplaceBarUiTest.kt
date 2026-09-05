package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.FindReplaceState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rendered interaction tests for the editor Find/Replace bar: query wiring,
 * Enter/Shift+Enter/Escape semantics, and the replace row (Shift+Enter there
 * moves to the previous match — the fixed regression).
 */
@OptIn(ExperimentalTestApi::class)
class FindReplaceBarUiTest {
    private val intents = mutableListOf<EditorIntent>()

    @Test
    fun typingInFindFieldEmitsQueryUpdates() =
        runComposeUiTest {
            show()
            findField().performTextInput("needle")
            assertEquals(listOf<EditorIntent>(EditorIntent.UpdateFindQuery("needle")), intents)
        }

    @Test
    fun findFieldEnterAndEscapeAreWired() =
        runComposeUiTest {
            show()
            findField().performKeyInput { pressKey(Key.Enter) }
            findField().performKeyInput { pressKey(Key.Escape) }
            assertEquals(
                listOf<EditorIntent>(EditorIntent.FindNext, EditorIntent.CloseFindBar),
                intents,
            )
        }

    @Test
    fun replaceFieldWiresTextShiftEnterAndEnter() =
        runComposeUiTest {
            show(showReplace = true)
            replaceField().performTextInput("replacement")
            replaceField().performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Enter)
                keyUp(Key.ShiftLeft)
            }
            replaceField().performKeyInput { pressKey(Key.Enter) }
            assertEquals(
                listOf<EditorIntent>(
                    EditorIntent.UpdateReplaceText("replacement"),
                    // Shift+Enter in the replace row moves to the previous match.
                    EditorIntent.FindPrevious,
                    EditorIntent.ReplaceCurrent,
                ),
                intents,
            )
        }

    @Test
    fun replaceRowRendersWhenEnabled() =
        runComposeUiTest {
            show(showReplace = true)
            replaceField().assertExists()
        }

    private fun androidx.compose.ui.test.ComposeUiTest.show(showReplace: Boolean = false) {
        setContent {
            JetaProgTheme(darkTheme = true) {
                FindReplaceBar(
                    state = FindReplaceState(isVisible = true, showReplace = showReplace),
                    onIntent = intents::add,
                )
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.findField() =
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]

    private fun androidx.compose.ui.test.ComposeUiTest.replaceField() =
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
}
