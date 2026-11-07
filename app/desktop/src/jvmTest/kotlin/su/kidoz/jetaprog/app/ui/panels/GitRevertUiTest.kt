package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.viewmodel.GitChangesViewMode
import su.kidoz.jetaprog.app.viewmodel.GitState
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GitRevertUiTest {
    private val unstaged = GitChange("src/Main.kt", GitChangeType.MODIFIED, staged = false)
    private val staged = unstaged.copy(staged = true)
    private val untracked = GitChange("new.txt", GitChangeType.UNTRACKED, staged = false)

    @Test
    fun selectedRevertRequiresConfirmationInDarkTheme() = checkSelectedRevert(dark = true)

    @Test
    fun selectedRevertRequiresConfirmationInLightTheme() = checkSelectedRevert(dark = false)

    private fun checkSelectedRevert(dark: Boolean) =
        runComposeUiTest {
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns
                MutableStateFlow(
                    GitState(staged = listOf(staged), unstaged = listOf(unstaged, untracked), selected = unstaged),
                )
            setContent { JetaProgTheme(darkTheme = dark) { GitPanel(vm, Modifier.width(320.dp)) } }
            onNodeWithText("Revert").assertIsDisplayed()
            onRoot().saveUiArtifacts("git-revert-${if (dark) "dark" else "light"}")
            onNodeWithText("Revert").performClick()
            onNodeWithText("Revert uncommitted changes?").assertIsDisplayed()
            onNodeWithText("Discard staged and unstaged changes in 1 file(s)?", substring = true).assertIsDisplayed()
            onNodeWithText("src/Main.kt", substring = true).assertIsDisplayed()
            verify(exactly = 0) { vm.discardChanges(any()) }
            onNodeWithText("Cancel").performClick()
            verify(exactly = 0) { vm.discardChanges(any()) }
            onNodeWithText("Revert").performClick()
            onNodeWithText("Revert changes").performClick()
            verify(exactly = 1) { vm.discardChanges(listOf(unstaged)) }
        }

    @Test
    fun modifierSelectionRevertsOnlySelectedPathsInBothLayouts() {
        for (mode in GitChangesViewMode.entries) {
            runComposeUiTest {
                val excluded = GitChange("other.txt", GitChangeType.MODIFIED, staged = false)
                val vm = mockk<GitViewModel>(relaxed = true)
                every { vm.state } returns
                    MutableStateFlow(
                        GitState(
                            staged = listOf(staged),
                            unstaged = listOf(unstaged, untracked, excluded),
                            changesViewMode = mode,
                        ),
                    )
                setContent { JetaProgTheme { GitPanel(vm) } }
                onNodeWithText("Revert").assertIsNotEnabled()
                onNodeWithText("new.txt").performClick()
                onRoot().performKeyInput {
                    withKeyDown(Key.CtrlLeft) {
                        onNodeWithText("other.txt").performMouseInput { click() }
                    }
                }
                onNodeWithText("new.txt").assertIsSelected()
                onNodeWithText("other.txt").assertIsSelected()
                onNodeWithText("Revert").performClick()
                onNodeWithText(
                    "Discard staged and unstaged changes in 2 file(s)?",
                    substring = true,
                ).assertIsDisplayed()
                onNodeWithText("Newly added and untracked files will be deleted.", substring = true).assertIsDisplayed()
                onNodeWithText("Revert changes").performClick()
                verify(exactly = 1) { vm.discardChanges(listOf(untracked, excluded)) }
                verify(exactly = 0) { vm.toggleStage(any()) }
            }
        }
    }

    @Test
    fun shiftClickSelectsRangeAndCommandClickTogglesAFile() =
        runComposeUiTest {
            val middle = GitChange("middle.txt", GitChangeType.MODIFIED, staged = false)
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns MutableStateFlow(GitState(unstaged = listOf(unstaged, middle, untracked)))
            setContent { JetaProgTheme { GitPanel(vm) } }
            onNodeWithText("Main.kt").performClick()
            onRoot().performKeyInput {
                withKeyDown(Key.ShiftLeft) { onNodeWithText("new.txt").performMouseInput { click() } }
                withKeyDown(Key.MetaLeft) { onNodeWithText("middle.txt").performMouseInput { click() } }
            }
            onNodeWithText("Revert").performClick()
            onNodeWithText("Discard staged and unstaged changes in 2 file(s)?", substring = true).assertIsDisplayed()
            onNodeWithText("Revert changes").performClick()
            verify(exactly = 1) { vm.discardChanges(listOf(unstaged, untracked)) }
        }

    @Test
    fun refreshedFilesDoNotResurrectOldSelections() =
        runComposeUiTest {
            val state = MutableStateFlow(GitState(unstaged = listOf(unstaged, untracked), selected = unstaged))
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns state
            setContent { JetaProgTheme { GitPanel(vm) } }
            onRoot().performKeyInput {
                withKeyDown(Key.CtrlLeft) { onNodeWithText("new.txt").performMouseInput { click() } }
            }
            runOnIdle { state.value = state.value.copy(unstaged = listOf(untracked)) }
            waitForIdle()
            runOnIdle { state.value = state.value.copy(unstaged = listOf(unstaged, untracked)) }
            onNodeWithText("Revert").performClick()
            onNodeWithText("Discard staged and unstaged changes in 1 file(s)?", substring = true).assertIsDisplayed()
            onNodeWithText("Revert changes").performClick()
            verify(exactly = 1) { vm.discardChanges(listOf(untracked)) }
        }

    @Test
    fun revertActionsAreDisabledWhileBusyAndWhenClean() =
        runComposeUiTest {
            val state = MutableStateFlow(GitState(unstaged = listOf(unstaged), selected = unstaged, isBusy = true))
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns state
            setContent { JetaProgTheme { GitPanel(vm) } }
            onNodeWithText("Revert").assertIsNotEnabled()
            runOnIdle { state.value = GitState() }
            onNodeWithText("Revert").assertIsNotEnabled()
        }

    @Test
    fun partiallyStagedFileSelectsOnlyTheClickedEntry() =
        runComposeUiTest {
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns MutableStateFlow(GitState(staged = listOf(staged), unstaged = listOf(unstaged)))
            setContent { JetaProgTheme { GitPanel(vm) } }

            val rows = onAllNodesWithText("Main.kt").assertCountEquals(2)
            rows[0].performClick()
            rows[0].assertIsSelected()
            rows[1].assertIsNotSelected()
        }

    @Test
    fun hoveringChangeDoesNotResizePathColumn() =
        runComposeUiTest {
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns MutableStateFlow(GitState(unstaged = listOf(unstaged)))
            setContent { JetaProgTheme { GitPanel(vm, Modifier.width(320.dp)) } }

            val originalBounds = onNodeWithText("src").getUnclippedBoundsInRoot()
            onNodeWithText("Main.kt").performMouseInput { moveTo(center) }
            waitForIdle()

            onNodeWithContentDescription("Revert uncommitted changes in src/Main.kt").assertIsDisplayed()
            kotlin.test.assertEquals(originalBounds, onNodeWithText("src").getUnclippedBoundsInRoot())
        }

    @Test
    fun stageCheckboxesExposeStateAndAccessibleActions() =
        runComposeUiTest {
            val vm = mockk<GitViewModel>(relaxed = true)
            every { vm.state } returns MutableStateFlow(GitState(staged = listOf(staged), unstaged = listOf(unstaged)))
            setContent { JetaProgTheme { GitPanel(vm) } }

            onNodeWithContentDescription("Unstage src/Main.kt").assertIsOn()
            onNodeWithContentDescription("Stage src/Main.kt").assertIsOff().performClick()
            verify(exactly = 1) { vm.toggleStage(unstaged) }
        }
}
