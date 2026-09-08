package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
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
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Every visit to a document enters navigation history, not only jumps: files opened
 * from the project tree or the tab bar used to be invisible to Recent Files and Back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelHistoryTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val navigationService = mockk<NavigationService>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("fun main() {}\nfun other() {}\n")
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingAFileRecordsTheVisit() =
        runTest {
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.OpenFile("/w/A.kt"))
            viewModel.state.first { it.tabs.size == 1 }

            coVerify(timeout = 2_000, exactly = 1) {
                navigationService.recordNavigation("/w/A.kt", TextPosition(0, 0), any())
            }
            viewModel.dispose()
        }

    @Test
    fun aJumpRecordsItsTargetExactlyOnce() =
        runTest {
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.NavigateTo("/w/B.kt", TextPosition(1, 4)))
            viewModel.state.first { it.tabs.size == 1 && it.cursor.position.line == 1 }

            coVerify(timeout = 2_000, exactly = 1) { navigationService.recordNavigation("/w/B.kt", any(), any()) }
            coVerify(exactly = 1) { navigationService.recordNavigation("/w/B.kt", TextPosition(1, 4), any()) }
            viewModel.dispose()
        }

    @Test
    fun switchingTabsRecordsTheTabShown() =
        runTest {
            val viewModel = editorViewModel()
            viewModel.dispatch(EditorIntent.OpenFile("/w/A.kt"))
            viewModel.state.first { it.tabs.size == 1 }
            viewModel.dispatch(EditorIntent.OpenFile("/w/B.kt"))
            viewModel.state.first { it.tabs.size == 2 }

            viewModel.dispatch(EditorIntent.SwitchTab(0))
            viewModel.state.first { it.activeTabIndex == 0 }

            coVerify(timeout = 2_000, exactly = 2) { navigationService.recordNavigation("/w/A.kt", any(), any()) }
            viewModel.dispose()
        }

    private fun editorViewModel(): EditorViewModel =
        EditorViewModel(
            fileSystem = fileSystem,
            settingsService = settingsService,
            navigationService = navigationService,
        )
}
