package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.viewmodel.GitState
import su.kidoz.jetaprog.app.viewmodel.GitViewModel
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VcsPerspectiveUiTest {
    @Test
    fun alignedDiffAndIndexLabelsInDarkTheme() = checkDiff(dark = true)

    @Test
    fun alignedDiffAndIndexLabelsInLightTheme() = checkDiff(dark = false)

    private fun checkDiff(dark: Boolean) =
        runComposeUiTest {
            val state =
                MutableStateFlow(
                    GitState(
                        selected = GitChange("src/Main.kt", GitChangeType.MODIFIED, staged = false),
                        diff = "@@ -1,3 +1,4 @@\n before\n-old\n+new\n+extra\n after\n",
                    ),
                )
            val vm = mockk<GitViewModel>()
            every { vm.state } returns state
            setContent { JetaProgTheme(darkTheme = dark) { VcsMainArea(vm) } }
            onNodeWithText("Before — Index").assertIsDisplayed()
            onNodeWithText("After — Working tree").assertIsDisplayed()
            onAllNodesWithText("before").assertCountEquals(2)
            onAllNodesWithText("after").assertCountEquals(2)
            val rows = onAllNodesWithText("after").fetchSemanticsNodes()
            assertEquals(rows[0].boundsInRoot.top, rows[1].boundsInRoot.top)
            onRoot().saveUiArtifacts("git-aligned-${if (dark) "dark" else "light"}")
            runOnIdle { state.value = state.value.copy(selected = state.value.selected?.copy(staged = true)) }
            onNodeWithText("Before — HEAD").assertIsDisplayed()
            onNodeWithText("After — Index").assertIsDisplayed()
        }

    @Test
    fun binaryPreviewExplainsWhyTextIsUnavailable() =
        runComposeUiTest {
            val vm = mockk<GitViewModel>()
            every { vm.state } returns
                MutableStateFlow(
                    GitState(
                        selected = GitChange("image.png", GitChangeType.UNTRACKED, staged = false),
                        diff = "Binary files /dev/null and b/image.png differ\n",
                    ),
                )
            setContent { JetaProgTheme { VcsMainArea(vm) } }
            onNodeWithText("Binary files differ; text preview is unavailable.").assertIsDisplayed()
        }
}
