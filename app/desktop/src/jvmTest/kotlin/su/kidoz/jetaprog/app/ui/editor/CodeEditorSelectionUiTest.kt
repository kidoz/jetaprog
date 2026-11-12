package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import su.kidoz.jetaprog.common.text.TextRange as EditorTextRange

@OptIn(ExperimentalTestApi::class)
class CodeEditorSelectionUiTest {
    private fun SemanticsNodeInteraction.selection(): TextRange {
        val range = fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)
        return requireNotNull(range).let { TextRange(it.start, it.end) }
    }

    @Test
    fun programmaticSelectionBackwardSelectionAndClearingDispatchRealRanges() =
        runComposeUiTest {
            val content = "abcdefgh"
            val intents = mutableListOf<EditorIntent>()
            val caretMoves = mutableListOf<TextPosition>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(
                        state = EditorState(content = content),
                        onContentChange = {},
                        onCursorMove = caretMoves::add,
                        onIntent = intents::add,
                    )
                }
            }
            val field = onNode(hasSetTextAction())

            field.performSemanticsAction(SemanticsActions.SetSelection) { it(1, 5, true) }
            runOnIdle {
                assertEquals(TextRange(1, 5), field.selection())
                assertEquals(
                    EditorIntent.Select(EditorTextRange(TextPosition(0, 1), TextPosition(0, 5))),
                    intents.last(),
                )
            }

            field.performSemanticsAction(SemanticsActions.SetSelection) { it(7, 2, true) }
            runOnIdle {
                assertEquals(TextRange(7, 2), field.selection())
                assertEquals(
                    EditorIntent.Select(
                        EditorTextRange(TextPosition(0, 2), TextPosition(0, 7)),
                        reversed = true,
                    ),
                    intents.last(),
                )
            }

            field.performSemanticsAction(SemanticsActions.SetSelection) { it(4, 4, true) }
            runOnIdle {
                assertEquals(TextRange(4, 4), field.selection())
                assertEquals(
                    EditorIntent.Select(EditorTextRange(TextPosition(0, 4), TextPosition(0, 4))),
                    intents.last(),
                )
                assertTrue(caretMoves.isEmpty(), "selection changes must not be degraded to caret moves")
            }

            field.performSemanticsAction(SemanticsActions.SetSelection) { it(6, 6, true) }
            runOnIdle {
                assertEquals(TextRange(6, 6), field.selection())
                assertEquals(listOf(TextPosition(0, 6)), caretMoves)
            }
        }

    @Test
    fun mouseSelectionDispatchesTheRangeShownByCompose() =
        runComposeUiTest {
            val content = (1..20).joinToString(separator = "\n") { "line $it" }
            val intents = mutableListOf<EditorIntent>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(
                        state = EditorState(content = content),
                        onContentChange = {},
                        onIntent = intents::add,
                    )
                }
            }
            val field = onNode(hasSetTextAction())
            val y = 4f + 2 * 21f + 10f

            field.performMouseInput {
                updatePointerTo(Offset(15f, y))
                press()
                moveTo(Offset(55f, y))
                release()
            }

            runOnIdle {
                val selection = field.selection()
                assertFalse(selection.collapsed, "dragging should select text")
                val intent = intents.filterIsInstance<EditorIntent.Select>().last()
                assertEquals(offsetToPosition(content, selection.start), intent.range.start)
                assertEquals(offsetToPosition(content, selection.end), intent.range.end)
            }
            onRoot().saveUiArtifacts("code-editor-selection-dark")
        }

    @Test
    fun switchingDocumentsRestoresEachSelectionAndItsDirection() =
        runComposeUiTest {
            val first =
                EditorState(
                    activeDocumentUri = DocumentUri.file("/a.kt"),
                    content = "abcdef",
                    cursor = Cursor(position = TextPosition(0, 4), anchor = TextPosition(0, 1)),
                )
            val second =
                EditorState(
                    activeDocumentUri = DocumentUri.file("/b.kt"),
                    content = "123456",
                    cursor = Cursor(position = TextPosition(0, 5), anchor = TextPosition(0, 2)),
                )
            val state = mutableStateOf(first)
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(state = state.value, onContentChange = {})
                }
            }
            val field = onNode(hasSetTextAction())

            runOnIdle { assertEquals(TextRange(1, 4), field.selection()) }
            runOnIdle { state.value = second }
            waitForIdle()
            assertEquals(TextRange(2, 5), field.selection())
            runOnIdle { state.value = first }
            waitForIdle()
            assertEquals(TextRange(1, 4), field.selection())
        }
}
