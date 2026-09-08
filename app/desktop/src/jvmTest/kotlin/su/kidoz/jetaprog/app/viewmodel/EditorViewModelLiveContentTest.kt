package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Navigation asks the editor for the text it currently holds, so results line up with
 * unsaved edits rather than with the file on disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelLiveContentTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("on disk")
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unsavedEditsAreVisibleForActiveAndBackgroundTabs() =
        runTest {
            val viewModel = EditorViewModel(fileSystem = fileSystem, settingsService = settingsService)

            viewModel.dispatch(EditorIntent.OpenFile("/w/A.kt"))
            viewModel.state.first { it.tabs.size == 1 }
            viewModel.dispatch(EditorIntent.UpdateContent("edited in A"))
            assertEquals("edited in A", viewModel.openDocumentContent("/w/A.kt"))

            viewModel.dispatch(EditorIntent.OpenFile("/w/B.kt"))
            viewModel.state.first { it.tabs.size == 2 }

            assertEquals("edited in A", viewModel.openDocumentContent("/w/A.kt"))
            assertEquals("on disk", viewModel.openDocumentContent("/w/B.kt"))
            assertNull(viewModel.openDocumentContent("/w/Closed.kt"))
            viewModel.dispose()
        }
}
