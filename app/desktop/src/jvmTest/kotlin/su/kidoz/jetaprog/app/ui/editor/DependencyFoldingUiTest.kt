package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.editor.search.FindMatch
import su.kidoz.jetaprog.editor.state.EditorState
import su.kidoz.jetaprog.editor.state.FindReplaceState
import su.kidoz.jetaprog.settings.model.EditorSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DependencyFoldingUiTest {
    private val content = "package demo\n\nimport a.B\nimport c.D\nimport e.F\n\nfun main() {}\n"

    private fun state() =
        EditorState(
            content = content,
            languageId = LanguageId.KOTLIN,
            activeDocumentUri = DocumentUri.file("/demo/A.kt"),
        )

    @Test
    fun collapsedHeaderExpandsAndCollapsesWithoutEditingInDarkTheme() = checkToggle(true)

    @Test
    fun collapsedHeaderExpandsAndCollapsesWithoutEditingInLightTheme() = checkToggle(false)

    private fun checkToggle(dark: Boolean) =
        runComposeUiTest {
            val changes = mutableListOf<String>()
            setContent { JetaProgTheme(darkTheme = dark) { CodeEditor(state(), onContentChange = changes::add) } }
            onNodeWithText("imports … (3)", substring = true).assertIsDisplayed()
            onNodeWithContentDescription("Expand imports (3)").performClick()
            onNodeWithContentDescription("Collapse imports (3)").assertIsDisplayed()
            onNodeWithContentDescription("Collapse imports (3)").performClick()
            onNodeWithContentDescription("Expand imports (3)").assertIsDisplayed()
            onRoot().saveUiArtifacts("dependency-folding-${if (dark) "dark" else "light"}")
            assertEquals(emptyList(), changes)
        }

    @Test
    fun importsOnFirstLineAreCollapsedByDefault() =
        runComposeUiTest {
            val python = "import os\nimport sys\n\nprint('hello')\n"
            setContent {
                JetaProgTheme {
                    CodeEditor(
                        EditorState(content = python, languageId = LanguageId.PYTHON),
                        onContentChange = {},
                    )
                }
            }
            onNodeWithContentDescription("Expand imports (2)").assertIsDisplayed()
            onNodeWithText("imports … (2)", substring = true).assertIsDisplayed()
        }

    @Test
    fun editingBelowFoldPropagatesCompleteOriginalSource() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            setContent {
                JetaProgTheme {
                    CodeEditor(
                        editor.value,
                        onContentChange = { editor.value = editor.value.copy(content = it) },
                    )
                }
            }
            val field = onNode(hasSetTextAction())
            val offset = content.indexOf("main")
            field.performSemanticsAction(SemanticsActions.SetSelection) { it(offset, offset, true) }
            field.performTextInput("new_")
            runOnIdle { assertEquals(content.replace("main", "new_main"), editor.value.content) }
            onNodeWithContentDescription("Expand imports (3)").assertIsDisplayed()
        }

    @Test
    fun pointerAndDefinitionNavigationUseOriginalLinesBelowFold() =
        runComposeUiTest {
            val moved = mutableListOf<TextPosition>()
            val definitions = mutableListOf<TextPosition>()
            setContent {
                JetaProgTheme {
                    CodeEditor(
                        state(),
                        onContentChange = {},
                        onCursorMove = moved::add,
                        onGoToDefinition = definitions::add,
                    )
                }
            }
            val field = onNode(hasSetTextAction())
            field.performMouseInput { click(Offset(80f, 98f)) }
            runOnIdle { assertEquals(6, moved.last().line) }
            field.performKeyInput {
                withKeyDown(Key.CtrlLeft) { field.performMouseInput { click(Offset(80f, 98f)) } }
            }
            runOnIdle { assertEquals(6, definitions.single().line) }
        }

    @Test
    fun editsBeforeFoldRetainCompleteImportsAndAdjustTheirOffsets() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            setContent {
                JetaProgTheme {
                    CodeEditor(editor.value, onContentChange = {
                        editor.value =
                            editor.value.copy(content = it)
                    })
                }
            }
            val field = onNode(hasSetTextAction())
            field.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 0, true) }
            field.performTextInput("// preface\n")
            runOnIdle { assertEquals("// preface\n" + content, editor.value.content) }
            onNodeWithContentDescription("Expand imports (3)").assertIsDisplayed()
        }

    @Test
    fun goToHiddenLineExpandsAndPreservesSourceCaret() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            setContent { JetaProgTheme { CodeEditor(editor.value, onContentChange = {}) } }
            runOnIdle { editor.value = editor.value.copy(cursor = Cursor(TextPosition(3, 7)), caretSyncVersion = 1) }
            onNodeWithContentDescription("Collapse imports (3)").assertIsDisplayed()
            onNodeWithContentDescription("Expand imports (3)").assertDoesNotExist()
        }

    @Test
    fun searchInsideFoldRevealsMatch() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            setContent { JetaProgTheme { CodeEditor(editor.value, onContentChange = {}) } }
            val start = content.indexOf("c.D")
            runOnIdle {
                editor.value =
                    editor.value.copy(
                        findReplaceState =
                            FindReplaceState(
                                matches = listOf(FindMatch(start, start + 3)),
                                currentMatchIndex = 0,
                            ),
                    )
            }
            onNodeWithContentDescription("Collapse imports (3)").assertIsDisplayed()
        }

    @Test
    fun backspaceAtFoldBoundaryRevealsBeforeDeletingHiddenText() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            setContent {
                JetaProgTheme {
                    CodeEditor(editor.value, onContentChange = {
                        editor.value =
                            editor.value.copy(content = it)
                    })
                }
            }
            val field = onNode(hasSetTextAction())
            field.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val end = content.indexOf("import e.F") + "import e.F".length
            field.performSemanticsAction(SemanticsActions.SetSelection) { it(end, end, true) }
            field.performKeyInput { pressKey(Key.Backspace) }
            onNodeWithContentDescription("Collapse imports (3)").assertIsDisplayed()
            runOnIdle { assertEquals(content, editor.value.content) }
            field.performKeyInput { pressKey(Key.Backspace) }
            runOnIdle { assertEquals(content.replace("import e.F", "import e."), editor.value.content) }
        }

    @Test
    fun manualExpansionSurvivesTabSwitchAndSettingDisablesDefaultFolding() =
        runComposeUiTest {
            val editor = mutableStateOf(state())
            val settings = mutableStateOf(EditorSettings.DEFAULT)
            setContent { JetaProgTheme { CodeEditor(editor.value, onContentChange = {}, settings = settings.value) } }
            onNodeWithContentDescription("Expand imports (3)").performClick()
            runOnIdle { editor.value = editor.value.copy(activeDocumentUri = DocumentUri.file("/demo/B.kt")) }
            onNodeWithContentDescription("Expand imports (3)").assertIsDisplayed()
            runOnIdle { editor.value = state() }
            onNodeWithContentDescription("Collapse imports (3)").assertIsDisplayed()
            runOnIdle { settings.value = settings.value.copy(collapseImportsByDefault = false) }
            onNodeWithContentDescription("Collapse imports (3)").assertDoesNotExist()
            onNodeWithText("imports … (3)", substring = true).assertDoesNotExist()
        }
}
