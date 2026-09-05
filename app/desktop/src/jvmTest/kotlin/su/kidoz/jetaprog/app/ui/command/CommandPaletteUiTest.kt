package su.kidoz.jetaprog.app.ui.command

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.command.CommandPaletteIntent
import su.kidoz.jetaprog.app.command.CommandPaletteState
import su.kidoz.jetaprog.app.command.PaletteCommand
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {
    private val build = PaletteCommand("project.build", "Build project", "Project")
    private val run = PaletteCommand("project.run", "Run project", "Project")
    private val intents = mutableListOf<CommandPaletteIntent>()

    @Test
    fun clickingCommandDispatchesExactlyOneExecution() =
        runComposeUiTest {
            showPalette()
            onNodeWithText("Build project").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(listOf<CommandPaletteIntent>(CommandPaletteIntent.Execute(build)), intents) }
            onNodeWithTag("palette").saveUiArtifacts("command-palette-dark")
        }

    @Test
    fun arrowsAndEnterExecuteFocusedSelectionOnce() =
        runComposeUiTest {
            showPalette()
            onNode(hasSetTextAction()).assertIsFocused().performKeyInput {
                pressKey(Key.DirectionDown)
            }
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(listOf<CommandPaletteIntent>(CommandPaletteIntent.Execute(run)), intents) }
        }

    @Test
    fun escapeDismissesWithoutExecuting() =
        runComposeUiTest {
            showPalette()
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
            runOnIdle { assertEquals(listOf<CommandPaletteIntent>(CommandPaletteIntent.Hide), intents) }
        }

    @Test
    fun typingDispatchesQueryChange() =
        runComposeUiTest {
            showPalette()
            onNode(hasSetTextAction()).performTextInput("build")
            runOnIdle {
                assertEquals(
                    listOf<CommandPaletteIntent>(CommandPaletteIntent.QueryChanged("build")),
                    intents,
                )
            }
        }

    @Test
    fun emptyResultsExplainTheQueryAndEnterDoesNothing() =
        runComposeUiTest {
            showPalette(CommandPaletteState(isVisible = true, query = "missing"), darkTheme = false)
            onNodeWithText("No commands match 'missing'").assertIsDisplayed()
            onNodeWithText("0 commands").assertIsDisplayed()
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(emptyList(), intents) }
            onNodeWithTag("palette").saveUiArtifacts("command-palette-light-empty")
        }

    @Test
    fun changedResultsResetKeyboardSelection() =
        runComposeUiTest {
            val state = mutableStateOf(CommandPaletteState(isVisible = true, results = listOf(build, run)))
            setContent {
                JetaProgTheme(darkTheme = true) {
                    CommandPalette(state.value, intents::add)
                }
            }
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
            runOnIdle { state.value = state.value.copy(query = "build", results = listOf(build)) }
            onNodeWithText("1 command").assertIsDisplayed()
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(listOf<CommandPaletteIntent>(CommandPaletteIntent.Execute(build)), intents) }
        }

    private fun ComposeUiTest.showPalette(
        state: CommandPaletteState = CommandPaletteState(isVisible = true, results = listOf(build, run)),
        darkTheme: Boolean = true,
    ) {
        setContent {
            JetaProgTheme(darkTheme = darkTheme) {
                CommandPalette(state, intents::add, Modifier.testTag("palette"))
            }
        }
    }
}
