package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Viewport tests for the code editor. Compose's text field reveals the caret it held
 * *before* the click that focused it, which used to drag the viewport back to a restored
 * session caret (or the top of a freshly opened file) on the first click after opening a
 * project. The reveal must stay off for that click, yet still work for typing.
 */
@OptIn(ExperimentalTestApi::class)
class CodeEditorScrollUiTest {
    private val content = (1..300).joinToString(separator = "\n") { "line $it" }

    /** Lines are 21sp tall; density 1 in the headless test makes that 21px, below 4px padding. */
    private fun yForLine(line: Int): Float = 4f + (line * 21f) + 10f

    private fun SemanticsNodeInteraction.scrollOffset(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun firstClickIntoUnfocusedEditorKeepsViewportWhereUserClicked() =
        runComposeUiTest {
            val moved = mutableListOf<Int>()
            val state = mutableStateOf(EditorState(content = content))
            setContent {
                JetaProgTheme(darkTheme = true) {
                    Box(Modifier.size(400.dp, 300.dp)) {
                        CodeEditor(
                            state = state.value,
                            onContentChange = {},
                            onCursorMove = { moved += it.line },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Session restore: caret on line 200, editor scrolled to it.
            runOnIdle {
                state.value =
                    state.value.copy(
                        cursor = Cursor(position = TextPosition(line = 200, column = 0)),
                        caretSyncVersion = 1,
                    )
            }
            waitForIdle()
            val field = onNode(hasSetTextAction())
            val restoredOffset = field.scrollOffset()
            assertTrue(restoredOffset > 21f * 150, "expected the editor to scroll to the restored caret")

            // The user scrolls back to the top of the file...
            field.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -restoredOffset) }
            waitForIdle()
            assertTrue(field.scrollOffset() < 21f, "expected the editor at the top")

            // ...and clicks a visible line in the still unfocused editor.
            field.performMouseInput { click(Offset(right - 40f, yForLine(line = 3))) }
            waitForIdle()

            assertEquals(3, moved.lastOrNull(), "caret should land on the clicked line")
            val afterClick = field.scrollOffset()
            assertTrue(afterClick < 21f, "viewport jumped away from the click to offset $afterClick")
        }

    @Test
    fun typingAtTheBottomEdgeStillRevealsTheCaret() =
        runComposeUiTest {
            setContent {
                JetaProgTheme(darkTheme = true) {
                    Box(Modifier.size(400.dp, 300.dp)) {
                        CodeEditor(
                            state = EditorState(content = content),
                            onContentChange = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            val field = onNode(hasSetTextAction())
            // Line 13 is the last line fully inside the 300px viewport.
            field.performMouseInput { click(Offset(right - 40f, yForLine(line = 13))) }
            waitForIdle()
            assertTrue(field.scrollOffset() < 1f, "focusing click must not scroll")

            // A newline pushes the caret onto line 14, just below the viewport.
            field.performTextInput("\n")
            waitForIdle()

            val afterTyping = field.scrollOffset()
            assertTrue(afterTyping > 0f, "typing past the viewport edge should reveal the caret")
        }
}
