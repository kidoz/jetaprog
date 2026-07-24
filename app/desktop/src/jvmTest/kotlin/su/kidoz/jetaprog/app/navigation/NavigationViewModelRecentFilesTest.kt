package su.kidoz.jetaprog.app.navigation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.navigation.NavigationEffect
import su.kidoz.jetaprog.app.ui.navigation.NavigationIntent
import su.kidoz.jetaprog.app.ui.navigation.NavigationViewModel
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationHistoryEntry
import su.kidoz.jetaprog.editor.navigation.NavigationService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationViewModelRecentFilesTest {
    private val navigationService = mockk<NavigationService>()

    private val entry =
        NavigationHistoryEntry(
            filePath = "/project/src/File.kt",
            position = TextPosition(4, 2),
            timestamp = 0L,
        )

    @Test
    fun `show recent files populates state`() =
        runTest {
            coEvery { navigationService.getRecentFiles(any()) } returns listOf(entry)
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.ShowRecentFiles)

            assertTrue(viewModel.state.value.isRecentFilesVisible)
            assertEquals(listOf(entry), viewModel.state.value.recentFiles)
        }

    @Test
    fun `show recent files with empty history notifies instead of opening`() =
        runTest {
            coEvery { navigationService.getRecentFiles(any()) } returns emptyList()
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.ShowRecentFiles)

            assertFalse(viewModel.state.value.isRecentFilesVisible)
            assertTrue(viewModel.effects.first() is NavigationEffect.ShowNotification)
        }

    @Test
    fun `select recent file hides popup and navigates`() =
        runTest {
            val viewModel = NavigationViewModel(navigationService)

            viewModel.processIntent(NavigationIntent.SelectRecentFile(entry))

            assertFalse(viewModel.state.value.isRecentFilesVisible)
            val effect = viewModel.effects.first()
            assertEquals(NavigationEffect.NavigateTo("/project/src/File.kt", 4, 2), effect)
        }
}
