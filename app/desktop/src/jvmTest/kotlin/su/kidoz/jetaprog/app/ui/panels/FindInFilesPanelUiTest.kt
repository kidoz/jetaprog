package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.viewmodel.TextSearchViewModel
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rendered interaction tests for the Find-in-Files panel's replace mode:
 * the toggle reveals the replacement field and Replace All action, and the
 * confirmation request reaches the view model.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class FindInFilesPanelUiTest {
    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun replaceModeShowsReplaceFieldAndReplaceAllAction() =
        runComposeUiTest {
            val viewModel = TextSearchViewModel("/proj", stubFileSystem())
            setContent {
                JetaProgTheme(darkTheme = true) {
                    FindInFilesPanel(viewModel = viewModel, onOpenMatch = { _, _, _ -> })
                }
            }

            onNodeWithText("Replace All…").assertDoesNotExist()
            runOnIdle { viewModel.setQuery("old") }
            waitUntil("search results") { viewModel.state.value.searched }

            onNodeWithContentDescription("Toggle replace in files", useUnmergedTree = true).performClick()

            onNodeWithText("Replace with").assertIsDisplayed()
            onNodeWithText("Replace All…").assertIsDisplayed()
            runOnIdle { viewModel.dispose() }
        }

    @Test
    fun replaceAllActionOpensConfirmationAndCancelClosesIt() =
        runComposeUiTest {
            val viewModel = TextSearchViewModel("/proj", stubFileSystem())
            setContent {
                JetaProgTheme(darkTheme = true) {
                    FindInFilesPanel(viewModel = viewModel, onOpenMatch = { _, _, _ -> })
                }
            }
            runOnIdle {
                viewModel.setQuery("old")
                viewModel.setReplacement("new")
            }
            waitUntil("search results") { viewModel.state.value.searched }

            onNodeWithContentDescription("Toggle replace in files", useUnmergedTree = true).performClick()
            onNodeWithText("Replace All…").performClick()
            runOnIdle { assertTrue(viewModel.state.value.awaitingReplaceConfirmation) }

            runOnIdle { viewModel.cancelReplaceAll() }
            runOnIdle { assertFalse(viewModel.state.value.awaitingReplaceConfirmation) }
            runOnIdle { viewModel.dispose() }
        }

    private fun stubFileSystem(): FileSystem {
        val fileSystem = mockk<FileSystem>()
        coEvery { fileSystem.listDirectory("/proj") } returns
            Result.success(listOf(fileEntry("src", "/proj/src", isDirectory = true)))
        coEvery { fileSystem.listDirectory("/proj/src") } returns
            Result.success(
                listOf(
                    fileEntry("a.kt", "/proj/src/a.kt", isFile = true),
                    fileEntry("b.kt", "/proj/src/b.kt", isFile = true),
                ),
            )
        coEvery { fileSystem.readText(any(), any()) } answers {
            when (arg<String>(0)) {
                "/proj/src/a.kt" -> Result.success("val old = 1\n")
                else -> Result.success("no match\n")
            }
        }
        coEvery { fileSystem.writeText(any(), any(), any()) } returns Result.success(Unit)
        return fileSystem
    }

    private fun fileEntry(
        name: String,
        path: String,
        isDirectory: Boolean = false,
        isFile: Boolean = false,
    ): FileEntry =
        FileEntry(
            name = name,
            path = path,
            isDirectory = isDirectory,
            isFile = isFile,
            isSymbolicLink = false,
            size = 10L,
            lastModified = 0L,
            isHidden = false,
        )
}
