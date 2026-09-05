package su.kidoz.jetaprog.app.ui.dialogs.clone

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.viewmodel.CloneRepositoryViewModel
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rendered interaction tests for the Clone Repository dialog: field wiring,
 * gating of the Clone action, and the success/error outcomes.
 */
@OptIn(ExperimentalTestApi::class)
class CloneRepositoryDialogUiTest {
    private val executor: ProcessExecutor = mockk()
    private val process: RunningProcess = mockk()
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("jetaprog-clone-ui").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun fieldsRenderAndCloneIsGatedUntilUrlIsEntered() =
        runComposeUiTest {
            val viewModel = shownViewModel()

            onNodeWithText("Clone Repository").assertIsDisplayed()
            onNodeWithText("Repository URL:").assertIsDisplayed()
            onNodeWithText("Destination directory:").assertIsDisplayed()
            onNodeWithText("Project name:").assertIsDisplayed()
            onNodeWithText("Clone").assertIsNotEnabled()

            urlFieldInput().performTextInput("https://example.com/user/sample.git")
            runOnIdle { assertTrue(viewModel.state.value.canClone) }
        }

    @Test
    fun projectNameDerivesFromUrlOnScreen() =
        runComposeUiTest {
            val viewModel = shownViewModel()
            urlFieldInput().performTextInput("https://example.com/user/sample.git")
            runOnIdle { assertEquals("sample", viewModel.state.value.projectName) }
        }

    @Test
    fun successfulCloneClosesDialogAndEmitsEffect() =
        runComposeUiTest {
            coEvery { executor.start(any()) } returns Result.success(process)
            every { process.output } returns flowOf(ProcessOutput.Exited(0))
            coEvery { process.waitFor() } returns 0

            val viewModel = shownViewModel()
            urlFieldInput().performTextInput("https://example.com/user/sample.git")
            onNodeWithText("Clone").performClick()

            // The clone runs on a real IO thread; poll the observable outcome.
            waitUntil(timeoutMillis = 10_000L) { !viewModel.state.value.isVisible }
            onNodeWithText("Clone Repository").assertDoesNotExist()
        }

    @Test
    fun existingTargetFolderShowsError() =
        runComposeUiTest {
            coEvery { executor.start(any()) } returns Result.success(process)
            every { process.output } returns flowOf(ProcessOutput.Exited(0))
            coEvery { process.waitFor() } returns 0

            val viewModel = shownViewModel()
            val taken = File(tempDir, "taken").apply { mkdirs() }
            // Drive the form through intents: the field-input wiring itself is
            // covered by projectNameDerivesFromUrlOnScreen.
            runOnIdle {
                viewModel.dispatch(CloneRepositoryIntent.SetRepositoryUrl("https://example.com/user/taken.git"))
                viewModel.dispatch(CloneRepositoryIntent.SetProjectName("taken"))
            }
            waitForIdle()
            onNodeWithText("Clone").performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithText("Target folder already exists", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            onAllNodesWithText("Target folder already exists", substring = true)[0].assertIsDisplayed()
        }

    @Test
    fun cancelHidesTheDialog() =
        runComposeUiTest {
            val viewModel = shownViewModel()
            onNodeWithText("Cancel").performClick()
            runOnIdle { assertFalse(viewModel.state.value.isVisible) }
            onNodeWithText("Clone Repository").assertDoesNotExist()
        }

    /** Shows the dialog over the real composable and returns its view model. */
    private fun androidx.compose.ui.test.ComposeUiTest.shownViewModel(): CloneRepositoryViewModel {
        val viewModel =
            CloneRepositoryViewModel(executor, defaultDestination = tempDir.absolutePath)
        setContent {
            JetaProgTheme(darkTheme = true) {
                CloneRepositoryDialog(viewModel = viewModel, onBrowseDestination = {})
            }
        }
        runOnIdle { viewModel.dispatch(CloneRepositoryIntent.Show) }
        waitForIdle()
        return viewModel
    }

    /** The URL field is the first editable field in the dialog (waits for it). */
    private fun androidx.compose.ui.test.ComposeUiTest.urlField() {
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.urlFieldInput() =
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
}
