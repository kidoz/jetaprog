package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rendered caret tests for the code editor: a user click must place the caret
 * on the clicked line (the reported regression — a click right after opening a
 * file used to land somewhere else), and programmatic caret sync
 * (`caretSyncVersion`) must still move the selection.
 */
@OptIn(ExperimentalTestApi::class)
class CodeEditorCaretUiTest {
    private val content = (1..20).joinToString(separator = "\n") { "line $it" }

    /** Lines are 21sp tall; density 1 in the headless test makes that 21px. */
    private fun yForLine(line: Int): Float = 4f + (line * 21f) + 10f

    @Test
    fun clickPlacesCaretOnTheClickedLine() =
        runComposeUiTest {
            val moved = mutableListOf<Int>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(
                        state = EditorState(content = content),
                        onContentChange = {},
                        onCursorMove = { moved += it.line },
                    )
                }
            }

            onNode(hasSetTextAction()).performTouchInput {
                // Click near the end of line 7 ("line 8"): far enough from the
                // left edge to be unambiguous, inside the line's vertical band.
                click(Offset(right - 40f, yForLine(line = 7)))
            }

            runOnIdle {
                assertTrue(moved.isNotEmpty(), "click did not move the cursor")
                assertEquals(7, moved.last())
            }
        }

    @Test
    fun caretSyncVersionMovesSelectionProgrammatically() =
        runComposeUiTest {
            val state = mutableStateOf(EditorState(content = content))
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(state = state.value, onContentChange = {})
                }
            }

            val targetOffset = content.indexOf("line 11")
            runOnIdle {
                // What the view model emits for go-to-line / restored sessions.
                state.value =
                    state.value.copy(
                        cursor = Cursor(position = TextPosition(line = 10, column = 0)),
                        caretSyncVersion = 1,
                    )
            }

            val node = onNode(hasSetTextAction()).fetchSemanticsNode()
            val selection = node.config[SemanticsProperties.TextSelectionRange]
            assertEquals(TextRange(targetOffset, targetOffset), TextRange(selection.start, selection.end))
        }
}
