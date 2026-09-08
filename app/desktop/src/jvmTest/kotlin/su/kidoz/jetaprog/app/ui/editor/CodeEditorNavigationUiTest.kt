package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ctrl+Click (Cmd+Click on macOS) asks for the declaration under the pointer; a plain
 * click does not.
 */
@OptIn(ExperimentalTestApi::class)
class CodeEditorNavigationUiTest {
    private val content = (1..20).joinToString(separator = "\n") { "line $it" }

    /** Lines are 21sp tall; density 1 in the headless test makes that 21px, below 4px padding. */
    private fun yForLine(line: Int): Float = 4f + (line * 21f) + 10f

    @Test
    fun modifierClickRequestsTheDeclarationUnderThePointer() =
        runComposeUiTest {
            val requested = mutableListOf<TextPosition>()
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CodeEditor(
                        state = EditorState(content = content),
                        onContentChange = {},
                        onGoToDefinition = { requested += it },
                    )
                }
            }

            val field = onNode(hasSetTextAction())
            field.performMouseInput { click(Offset(right - 40f, yForLine(line = 2))) }
            waitForIdle()
            assertTrue(requested.isEmpty(), "a plain click must not navigate")

            field.performKeyInput {
                withKeyDown(Key.CtrlLeft) {
                    field.performMouseInput { click(Offset(20f, yForLine(line = 7))) }
                }
            }
            waitForIdle()

            assertEquals(1, requested.size, "one modifier click should request one declaration")
            assertEquals(7, requested.single().line)
        }
}
