package su.kidoz.jetaprog.app.ui.dialogs.filepicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for the file picker view model against a real temporary directory:
 * listing and filtering per mode, navigation, typed paths, folder creation,
 * and what each mode reports as the picked path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilePickerViewModelTest {
    private lateinit var root: File
    private lateinit var viewModel: FilePickerViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Resolve symlinks (macOS /var -> /private/var) so paths compare equal.
        root = Files.createTempDirectory("jetaprog-picker-test").toRealPath().toFile()
        File(root, "beta").mkdir()
        File(root, "Alpha").mkdir()
        File(root, ".hidden").mkdir()
        File(root, "notes.txt").writeText("hello")
        File(root, "Alpha/inner.kt").writeText("val x = 1")
        viewModel = FilePickerViewModel(JvmFileSystem(), homeDirectory = root.absolutePath, roots = listOf("/"))
    }

    @AfterTest
    fun tearDown() {
        viewModel.dispose()
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    @Test
    fun directoryModeListsOnlyVisibleFoldersSortedCaseInsensitively() {
        show(FilePickerRequest.openProject(root.absolutePath))

        assertEquals(listOf("Alpha", "beta"), entryNames())
        assertEquals(root.absolutePath, viewModel.state.value.pathInput)
    }

    @Test
    fun fileModeListsFoldersBeforeFilesAndHiddenOnlyOnRequest() {
        show(FilePickerRequest.openFile(root.absolutePath))
        assertEquals(listOf("Alpha", "beta", "notes.txt"), entryNames())

        viewModel.dispatch(FilePickerIntent.SetShowHidden(true))
        awaitState("hidden entries") { it.showHidden }
        assertEquals(listOf(".hidden", "Alpha", "beta", "notes.txt"), entryNames())
    }

    @Test
    fun missingInitialDirectoryFallsBackToNearestExistingAncestor() {
        show(FilePickerRequest.openProject(File(root, "Alpha/not/there").absolutePath), File(root, "Alpha"))

        assertEquals(listOf<String>(), entryNames())
    }

    @Test
    fun activatingAFolderEntersItAndUpReturns() {
        show(FilePickerRequest.openFile(root.absolutePath))

        viewModel.dispatch(FilePickerIntent.ActivateEntry(File(root, "Alpha").absolutePath))
        awaitListing(File(root, "Alpha"))
        assertEquals(listOf("inner.kt"), entryNames())

        viewModel.dispatch(FilePickerIntent.NavigateUp)
        awaitListing(root)
        assertEquals(listOf("Alpha", "beta", "notes.txt"), entryNames())
    }

    @Test
    fun confirmInDirectoryModePicksSelectedFolderOrElseCurrentDirectory() {
        show(FilePickerRequest.openProject(root.absolutePath))
        viewModel.dispatch(FilePickerIntent.Confirm)
        assertEquals(FilePickerEffect.Picked(FilePickerPurpose.OPEN_PROJECT, root.absolutePath), nextEffect())
        assertFalse(viewModel.state.value.isVisible)

        show(FilePickerRequest.cloneDestination(root.absolutePath))
        viewModel.dispatch(FilePickerIntent.SelectEntry(File(root, "beta").absolutePath))
        viewModel.dispatch(FilePickerIntent.Confirm)
        assertEquals(
            FilePickerEffect.Picked(FilePickerPurpose.CLONE_DESTINATION, File(root, "beta").absolutePath),
            nextEffect(),
        )
    }

    @Test
    fun fileModeNeedsASelectedFileAndActivatingItPicks() {
        show(FilePickerRequest.openFile(root.absolutePath))
        assertFalse(viewModel.state.value.canConfirm)

        viewModel.dispatch(FilePickerIntent.ActivateEntry(File(root, "notes.txt").absolutePath))
        assertEquals(
            FilePickerEffect.Picked(FilePickerPurpose.OPEN_FILE, File(root, "notes.txt").absolutePath),
            nextEffect(),
        )
    }

    @Test
    fun typedPathNavigatesWithTildeExpansionAndReportsMissingPaths() {
        show(FilePickerRequest.openProject(File(root, "beta").absolutePath), File(root, "beta"))

        viewModel.dispatch(FilePickerIntent.PathInputChanged("~/Alpha"))
        assertTrue(awaitState("typed path") { it.pathInput == "~/Alpha" }.isPathEdited)
        viewModel.dispatch(FilePickerIntent.Confirm)
        // Navigating to a typed path lists it; it must not pick it.
        awaitListing(File(root, "Alpha"))

        viewModel.dispatch(FilePickerIntent.PathInputChanged(File(root, "nope").absolutePath))
        viewModel.dispatch(FilePickerIntent.Confirm)
        awaitState("missing path error") { it.error == "Path does not exist: ${File(root, "nope").absolutePath}" }
    }

    @Test
    fun newFolderIsCreatedSelectedAndRejectsDuplicatesAndSeparators() {
        show(FilePickerRequest.openProject(root.absolutePath))

        viewModel.dispatch(FilePickerIntent.StartNewFolder)
        viewModel.dispatch(FilePickerIntent.NewFolderNameChanged("a/b"))
        viewModel.dispatch(FilePickerIntent.Confirm)
        awaitState("separator error") { it.error == "“a/b” is not a valid folder name." }

        viewModel.dispatch(FilePickerIntent.NewFolderNameChanged("beta"))
        viewModel.dispatch(FilePickerIntent.Confirm)
        awaitState("duplicate error") { it.error == "“beta” already exists in this folder." }

        viewModel.dispatch(FilePickerIntent.NewFolderNameChanged("gamma"))
        viewModel.dispatch(FilePickerIntent.Confirm)
        val created = awaitState("new folder listed") { it.selectedPath == File(root, "gamma").absolutePath }
        assertTrue(File(root, "gamma").isDirectory)
        assertNull(created.newFolderName)
        assertEquals(listOf("Alpha", "beta", "gamma"), entryNames())
    }

    @Test
    fun saveModePicksNewFileAndAsksBeforeOverwriting() {
        show(FilePickerRequest.saveFileAs(root.absolutePath, suggestedFileName = "notes.txt"))
        assertEquals("notes.txt", viewModel.state.value.fileName)

        viewModel.dispatch(FilePickerIntent.Confirm)
        val asking = awaitState("overwrite prompt") { it.overwriteTarget == File(root, "notes.txt").absolutePath }
        assertFalse(asking.canConfirm)

        viewModel.dispatch(FilePickerIntent.CancelOverwrite)
        assertTrue(awaitState("prompt closed") { it.overwriteTarget == null }.isVisible)

        viewModel.dispatch(FilePickerIntent.FileNameChanged("fresh.txt"))
        viewModel.dispatch(FilePickerIntent.Confirm)
        assertEquals(
            FilePickerEffect.Picked(FilePickerPurpose.SAVE_FILE_AS, File(root, "fresh.txt").absolutePath),
            nextEffect(),
        )
    }

    @Test
    fun confirmingOverwritePicksTheExistingFile() {
        show(FilePickerRequest.saveFileAs(root.absolutePath, suggestedFileName = "notes.txt"))
        viewModel.dispatch(FilePickerIntent.Confirm)
        awaitState("overwrite prompt") { it.overwriteTarget != null }
        viewModel.dispatch(FilePickerIntent.ConfirmOverwrite)

        assertEquals(
            FilePickerEffect.Picked(FilePickerPurpose.SAVE_FILE_AS, File(root, "notes.txt").absolutePath),
            nextEffect(),
        )
    }

    @Test
    fun dismissHidesWithoutAnEffectAndRemembersHiddenFilesChoice() {
        show(FilePickerRequest.openFile(root.absolutePath))
        viewModel.dispatch(FilePickerIntent.SetShowHidden(true))
        viewModel.dispatch(FilePickerIntent.Dismiss)
        assertNull(awaitState("hidden picker") { !it.isVisible }.request)

        show(FilePickerRequest.openFile(root.absolutePath))
        assertTrue(viewModel.state.value.showHidden)
    }

    @Test
    fun placesStartWithHomeAndEndWithTheFileSystemRoot() {
        show(FilePickerRequest.openProject(root.absolutePath))

        val places = viewModel.state.value.places
        assertEquals(FilePickerPlace("Home", root.absolutePath), places.first())
        assertEquals(FilePickerPlace("Computer", "/"), places.last())
    }

    private fun entryNames(): List<String> =
        viewModel.state.value.entries
            .map { it.name }

    /** Shows the picker and waits for the first listing of [expectedDirectory]. */
    private fun show(
        request: FilePickerRequest,
        expectedDirectory: File = root,
    ) {
        viewModel.dispatch(FilePickerIntent.Show(request))
        awaitListing(expectedDirectory)
    }

    private fun awaitListing(directory: File) =
        awaitState("listing of $directory") {
            it.isVisible && !it.isLoading && it.currentDirectory == directory.absolutePath
        }

    /**
     * The view model does real IO on another thread, so an intent may still be
     * running when dispatch returns: wait for the outcome instead of asserting at once.
     */
    private fun awaitState(
        what: String,
        predicate: (FilePickerState) -> Boolean,
    ): FilePickerState =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.state.first(predicate) }
                ?: fail("Timed out waiting for $what; state was ${viewModel.state.value}")
        }

    private fun nextEffect(): FilePickerEffect =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { assertNotNull(viewModel.effects.first()) } }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
