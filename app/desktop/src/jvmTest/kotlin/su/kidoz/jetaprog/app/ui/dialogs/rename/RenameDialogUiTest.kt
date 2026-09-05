package su.kidoz.jetaprog.app.ui.dialogs.rename

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.refactoring.RenamePlan
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RenameDialogUiTest {
    private val renamed = mutableListOf<String>()
    private var dismissals = 0

    @Test
    fun unchangedAndBlankNamesCannotBeSubmittedByButtonOrEnter() =
        runComposeUiTest {
            showRename()
            onNodeWithText("Rename", substring = false).assertIsNotEnabled()
            onNode(hasSetTextAction()).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            onNode(hasSetTextAction()).performTextReplacement("   ")
            onNodeWithText("Rename", substring = false).assertIsNotEnabled()
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(emptyList(), renamed) }
        }

    @Test
    fun validNameCanBeSubmittedByButton() =
        runComposeUiTest {
            showRename()
            onNode(hasSetTextAction()).performTextReplacement("NewName")
            onNodeWithText("Rename", substring = false).assertIsEnabled().performClick()
            runOnIdle {
                assertEquals(listOf("NewName"), renamed)
                assertEquals(0, dismissals)
            }
        }

    @Test
    fun validNameCanBeSubmittedByEnterExactlyOnce() =
        runComposeUiTest {
            showRename()
            onNode(hasSetTextAction()).performTextReplacement("NewName")
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(listOf("NewName"), renamed) }
        }

    @Test
    fun escapeCancelsWithoutRenaming() =
        runComposeUiTest {
            showRename()
            onNode(hasSetTextAction()).performTextReplacement("NewName")
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
            runOnIdle {
                assertEquals(emptyList(), renamed)
                assertEquals(1, dismissals)
            }
        }

    private fun ComposeUiTest.showRename() {
        val plan = RenamePlan("OldName", "Example.kt", "class OldName", emptyMap())
        setContent {
            JetaProgTheme(darkTheme = true) {
                RenameDialog(plan, renamed::add, { dismissals++ })
            }
        }
    }
}
