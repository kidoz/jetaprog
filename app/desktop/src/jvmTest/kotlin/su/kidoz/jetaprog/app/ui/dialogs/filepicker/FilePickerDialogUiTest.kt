package su.kidoz.jetaprog.app.ui.dialogs.filepicker

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.app.ui.saveUiArtifacts
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rendered interaction tests for the IDE's own file picker: what each mode
 * shows, single versus double click, keyboard handling, the overwrite prompt,
 * and a full browse-and-open run against a real directory.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FilePickerDialogUiTest {
    private val intents = mutableListOf<FilePickerIntent>()
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        root = Files.createTempDirectory("jetaprog-picker-ui").toRealPath().toFile()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    @Test
    fun openFileModeShowsListingAndGatesOpenOnSelection() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openFile("/work")))

            // Static labels sit under the click-swallowing overlay: read them unmerged.
            onNodeWithText("Open File", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Home", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("src", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("build.gradle.kts", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("2.0 KB", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Open").assertIsNotEnabled()
            onNodeWithTag(TAG, useUnmergedTree = true).saveUiArtifacts("file-picker-dark-open-file")
        }

    @Test
    fun directoryModeOffersCurrentFolderAndRendersInLightTheme() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openProject("/work")), darkTheme = false)

            onNodeWithText("Open Project", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("build.gradle.kts", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("Open").assertIsEnabled()
            onNodeWithTag(TAG, useUnmergedTree = true).saveUiArtifacts("file-picker-light-open-project")
        }

    @Test
    fun singleClickSelectsAndSecondClickOnSameRowActivates() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openFile("/work")))

            onNodeWithText("src").performClick()
            runOnIdle { assertEquals(listOf<FilePickerIntent>(FilePickerIntent.SelectEntry("/work/src")), intents) }

            onNodeWithText("src").performClick()
            runOnIdle {
                assertEquals(
                    listOf(FilePickerIntent.SelectEntry("/work/src"), FilePickerIntent.ActivateEntry("/work/src")),
                    intents,
                )
            }
        }

    @Test
    fun clickingAnotherRowSelectsItInsteadOfActivating() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openFile("/work")))

            onNodeWithText("src").performClick()
            onNodeWithText("docs").performClick()
            runOnIdle {
                assertEquals(
                    listOf<FilePickerIntent>(
                        FilePickerIntent.SelectEntry("/work/src"),
                        FilePickerIntent.SelectEntry("/work/docs"),
                    ),
                    intents,
                )
            }
        }

    @Test
    fun toolbarPlacesAndFooterDispatchTheirIntents() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openProject("/work")))

            onNodeWithText("Up").performClick()
            onNodeWithText("New Folder").performClick()
            onNodeWithText("Home").performClick()
            onNodeWithText("Show hidden files").performClick()
            onNodeWithText("Open").performClick()
            onNodeWithText("Cancel").performClick()
            runOnIdle {
                assertEquals(
                    listOf(
                        FilePickerIntent.NavigateUp,
                        FilePickerIntent.StartNewFolder,
                        FilePickerIntent.NavigateTo("/home/dev"),
                        FilePickerIntent.SetShowHidden(true),
                        FilePickerIntent.Confirm,
                        FilePickerIntent.Dismiss,
                    ),
                    intents,
                )
            }
        }

    @Test
    fun enterConfirmsAndEscapeDismissesFromThePathField() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openProject("/work")))

            // The path field takes focus when the dialog opens.
            pathField().performKeyInput { pressKey(Key.Enter) }
            pathField().performKeyInput { pressKey(Key.Escape) }
            runOnIdle { assertEquals(listOf(FilePickerIntent.Confirm, FilePickerIntent.Dismiss), intents) }
        }

    @Test
    fun escapeClosesTheNewFolderRowBeforeTheDialog() =
        runComposeUiTest {
            showDialog(listingState(FilePickerRequest.openProject("/work")).copy(newFolderName = "dra"))

            onNodeWithText("Create").assertIsEnabled()
            // While a folder is being named, the primary button waits for it.
            onNodeWithText("Open").assertIsNotEnabled()
            onNodeWithTag(TAG, useUnmergedTree = true).saveUiArtifacts("file-picker-dark-new-folder")

            newFolderField().performKeyInput { pressKey(Key.Escape) }
            runOnIdle { assertEquals(listOf<FilePickerIntent>(FilePickerIntent.CancelNewFolder), intents) }
        }

    @Test
    fun saveModeShowsFileNameAndOverwritePrompt() =
        runComposeUiTest {
            showDialog(
                listingState(FilePickerRequest.saveFileAs("/work", "README.md"))
                    .copy(fileName = "README.md", overwriteTarget = "/work/README.md"),
            )

            onNodeWithText("File name:", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("“README.md” already exists. Replace it?", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Save").assertIsNotEnabled()
            onNodeWithTag(TAG, useUnmergedTree = true).saveUiArtifacts("file-picker-dark-save-overwrite")

            onNodeWithText("Replace").performClick()
            onNodeWithText("Keep").performClick()
            runOnIdle {
                assertEquals(
                    listOf(FilePickerIntent.ConfirmOverwrite, FilePickerIntent.CancelOverwrite),
                    intents,
                )
            }
        }

    @Test
    fun hiddenDialogRendersNothing() =
        runComposeUiTest {
            showDialog(FilePickerState())
            onNodeWithText("Cancel").assertDoesNotExist()
        }

    @Test
    fun browsingARealDirectoryAndOpeningReportsThePickedFolder() =
        runComposeUiTest {
            File(root, "project/src").mkdirs()
            val viewModel = FilePickerViewModel(JvmFileSystem(), homeDirectory = root.absolutePath, roots = listOf("/"))
            setContent { JetaProgTheme(darkTheme = true) { FilePickerHost(viewModel = viewModel) } }
            runOnIdle { viewModel.dispatch(FilePickerIntent.Show(FilePickerRequest.openProject(root.absolutePath))) }

            // Listings come from a real IO thread: poll for what the user would see.
            awaitText("project")
            onNodeWithText("project").performClick()
            onNodeWithText("project").performClick()
            awaitText("src")

            pathField().performTextReplacement(root.absolutePath)
            pathField().performKeyInput { pressKey(Key.Enter) }
            awaitText("project")

            onNodeWithText("project").performClick()
            onNodeWithText("Open").performClick()
            val effect = runBlocking { withTimeout(TIMEOUT_MILLIS) { viewModel.effects.first() } }
            assertEquals(
                FilePickerEffect.Picked(FilePickerPurpose.OPEN_PROJECT, File(root, "project").absolutePath),
                effect,
            )
            waitUntil(timeoutMillis = TIMEOUT_MILLIS) { !viewModel.state.value.isVisible }
            onNodeWithText("Cancel").assertDoesNotExist()
            viewModel.dispose()
        }

    private fun ComposeUiTest.showDialog(
        state: FilePickerState,
        darkTheme: Boolean = true,
    ) {
        setContent {
            JetaProgTheme(darkTheme = darkTheme) {
                FilePickerDialog(state = state, onIntent = { intents += it }, modifier = Modifier.testTag(TAG))
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    /** The path field is the first editable field unless the New Folder row is open. */
    private fun ComposeUiTest.pathField() = onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]

    private fun ComposeUiTest.newFolderField() = onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]

    private fun listingState(request: FilePickerRequest): FilePickerState =
        FilePickerState(
            isVisible = true,
            request = request,
            currentDirectory = "/work",
            pathInput = "/work",
            allEntries =
                listOf(
                    entry("docs", isDirectory = true),
                    entry("src", isDirectory = true),
                    entry(".git", isDirectory = true, isHidden = true),
                    entry("build.gradle.kts", size = 2_048),
                    entry("README.md", size = 512),
                ),
            places =
                listOf(
                    FilePickerPlace("Home", "/home/dev"),
                    FilePickerPlace("Projects", "/home/dev/Projects"),
                    FilePickerPlace("Computer", "/"),
                ),
        )

    private fun entry(
        name: String,
        isDirectory: Boolean = false,
        isHidden: Boolean = false,
        size: Long = 0,
    ): FileEntry =
        FileEntry(
            name = name,
            path = "/work/$name",
            isDirectory = isDirectory,
            isFile = !isDirectory,
            isSymbolicLink = false,
            size = size,
            lastModified = 0,
            isHidden = isHidden,
        )

    private companion object {
        const val TAG = "file-picker"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
