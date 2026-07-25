package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.MarkedString
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.language.Hover
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hover popup timing.
 *
 * The loading indicator used to be shown synchronously, before the debounce, so every
 * pointer movement over code raised a "Loading..." popup — and a moving pointer restarted
 * it before it could ever resolve, leaving the popup stuck on "Loading..." indefinitely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelHoverTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val languageRegistry = mockk<LanguageRegistry>()

    private val position = TextPosition(3, 8)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("int main() {}")
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
        every { languageRegistry.onDiagnostics(any()) } returns Disposable { }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsNoPopupWhileTheDebounceIsStillRunning() =
        runTest {
            coEvery { languageRegistry.provideHover(any(), any()) } returns hover("int")
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestHover(position))
            advanceTimeBy(EditorViewModel.HOVER_DEBOUNCE_MS - 1)

            val state = viewModel.state.value.hoverState
            assertFalse(state.isLoading, "a loading popup appeared before the debounce elapsed")
            assertFalse(state.isVisible)
            viewModel.dispose()
        }

    @Test
    fun aMovingPointerNeverLeavesThePopupStuckOnLoading() =
        runTest {
            coEvery { languageRegistry.provideHover(any(), any()) } returns hover("int")
            val viewModel = editorViewModel()

            // Ten pointer moves in quick succession, each restarting the debounce.
            repeat(10) { step ->
                viewModel.dispatch(EditorIntent.RequestHover(TextPosition(3, step)))
                advanceTimeBy(EditorViewModel.HOVER_DEBOUNCE_MS / 4)
                assertFalse(
                    viewModel.state.value.hoverState.isLoading,
                    "popup showed Loading while the pointer was still moving",
                )
            }

            advanceUntilIdle()
            val settled = viewModel.state.value.hoverState
            assertFalse(settled.isLoading, "popup stayed on Loading after the pointer settled")
            assertTrue(settled.isVisible)
            viewModel.dispose()
        }

    @Test
    fun showsContentOnceThePointerSettles() =
        runTest {
            coEvery { languageRegistry.provideHover(any(), any()) } returns hover("int square(int)")
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestHover(position))
            advanceUntilIdle()

            val state = viewModel.state.value.hoverState
            assertTrue(state.isVisible)
            assertFalse(state.isLoading)
            assertEquals(1, state.contents.size)
            viewModel.dispose()
        }

    @Test
    fun dismissesWhenTheServerHasNothingToSay() =
        runTest {
            // clangd answers `result: null` for whitespace and punctuation, which is common.
            coEvery { languageRegistry.provideHover(any(), any()) } returns null
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestHover(position))
            advanceUntilIdle()

            val state = viewModel.state.value.hoverState
            assertFalse(state.isVisible)
            assertFalse(state.isLoading, "an empty result left the popup on Loading")
            viewModel.dispose()
        }

    @Test
    fun showsLoadingOnlyWhenTheRequestIsGenuinelySlow() =
        runTest {
            coEvery { languageRegistry.provideHover(any(), any()) } coAnswers {
                delay(EditorViewModel.HOVER_LOADING_INDICATOR_MS * 10)
                hover("int")
            }
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestHover(position))
            advanceTimeBy(EditorViewModel.HOVER_DEBOUNCE_MS + EditorViewModel.HOVER_LOADING_INDICATOR_MS + 1)

            assertTrue(
                viewModel.state.value.hoverState.isLoading,
                "a slow request should raise the loading indicator",
            )

            advanceUntilIdle()
            assertFalse(viewModel.state.value.hoverState.isLoading)
            assertTrue(viewModel.state.value.hoverState.isVisible)
            viewModel.dispose()
        }

    @Test
    fun aFailingProviderDoesNotStrandThePopupOnLoading() =
        runTest {
            coEvery { languageRegistry.provideHover(any(), any()) } throws IllegalStateException("boom")
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestHover(position))
            advanceUntilIdle()

            val state = viewModel.state.value.hoverState
            assertFalse(state.isLoading, "a provider failure left the popup on Loading")
            assertFalse(state.isVisible)
            viewModel.dispose()
        }

    private fun hover(text: String): Hover = Hover(contents = listOf(MarkedString.Markdown(text)))

    private fun editorViewModel(): EditorViewModel =
        EditorViewModel(
            fileSystem = fileSystem,
            settingsService = settingsService,
            languageRegistry = languageRegistry,
        )
}
