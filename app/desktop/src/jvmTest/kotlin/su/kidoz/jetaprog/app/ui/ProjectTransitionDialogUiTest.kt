package su.kidoz.jetaprog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import su.kidoz.jetaprog.app.PendingProjectTransition
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProjectTransitionDialogUiTest {
    private val actions = mutableListOf<String>()

    @Test
    fun cancelLeavesAllTransitionActionsUntouched() =
        runComposeUiTest {
            showDialog(PendingProjectTransition.OpenProject("/work/next"))

            onNodeWithText("Open project?", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Save All").assertIsDisplayed()
            onNodeWithText("Discard").assertIsDisplayed()
            onNodeWithText("Cancel").performClick()

            runOnIdle { assertEquals(listOf("cancel"), actions) }
        }

    @Test
    fun discardDispatchesExactlyOnce() =
        runComposeUiTest {
            showDialog(PendingProjectTransition.CloseProject)

            onNodeWithText("Close project?", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Discard").performClick()

            runOnIdle { assertEquals(listOf("discard"), actions) }
        }

    @Test
    fun saveAllDispatchesExactlyOnce() =
        runComposeUiTest {
            showDialog(PendingProjectTransition.Exit)

            onNodeWithText("Exit JetaProg?", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Save All").performClick()

            runOnIdle { assertEquals(listOf("saveAll"), actions) }
        }

    @Test
    fun openProjectDialogCapturesSharedGuardActions() =
        runComposeUiTest {
            showDialog(PendingProjectTransition.OpenProject("/work/next"))

            onNodeWithText(
                "The current project has unsaved files. Save them before opening another project, or discard those changes.",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            onNode(
                isRoot() and hasAnyDescendant(hasText("Open project?")),
                useUnmergedTree = true,
            ).saveUiArtifacts("project-transition-open-dark")
        }

    private fun androidx.compose.ui.test.ComposeUiTest.showDialog(transition: PendingProjectTransition) {
        actions.clear()
        setContent {
            JetaProgTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
                    ProjectTransitionDialog(
                        transition = transition,
                        onDiscard = { actions += "discard" },
                        onCancel = { actions += "cancel" },
                        onSaveAll = { actions += "saveAll" },
                    )
                }
            }
        }
    }
}
