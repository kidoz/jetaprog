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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.common.completion.CompletionList
import su.kidoz.jetaprog.common.completion.TextEditData
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
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
 * Completion request pacing. Typing used to send a provider round trip per keystroke
 * and empty the popup each time, so the list flickered between a spinner and results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelCompletionTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val languageRegistry = mockk<LanguageRegistry>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
        every { languageRegistry.onDiagnostics(any()) } returns Disposable { }
        every { languageRegistry.onWorkspaceEdit(any()) } returns Disposable { }
        coEvery { languageRegistry.provideCompletions(any(), any(), any()) } returns
            CompletionList(listOf(item("alpha"), item("alphabet"), item("beta")), isIncomplete = false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typedRequestsCoalesceIntoOneProviderRoundTrip() =
        runTest {
            val viewModel = editorViewModel()

            listOf("al", "alp", "alph", "alpha").forEach { prefix ->
                viewModel.dispatch(EditorIntent.RequestCompletion(filterText = prefix, automatic = true))
                advanceTimeBy(EditorViewModel.COMPLETION_DEBOUNCE_MS / 2)
            }
            advanceUntilIdle()
            val items = viewModel.awaitCompletionItems()

            coVerify(exactly = 1) { languageRegistry.provideCompletions(any(), any(), any()) }
            assertEquals(listOf("alpha", "alphabet"), items.map { it.label })
            viewModel.dispose()
        }

    @Test
    fun anExplicitRequestIsNotDelayed() =
        runTest {
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestCompletion(filterText = "al"))
            viewModel.awaitCompletionItems()

            coVerify(exactly = 1) { languageRegistry.provideCompletions(any(), any(), any()) }
            assertTrue(
                currentTime < EditorViewModel.COMPLETION_DEBOUNCE_MS,
                "the explicit request waited for the debounce",
            )
            viewModel.dispose()
        }

    @Test
    fun aDebouncedRequestShowsNoPopupBeforeTheDelayElapses() =
        runTest {
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestCompletion(filterText = "al", automatic = true))
            advanceTimeBy(EditorViewModel.COMPLETION_DEBOUNCE_MS - 1)

            val state = viewModel.state.value.completionState
            assertFalse(state.isVisible, "an empty popup flashed before the debounce elapsed")
            assertFalse(state.isLoading)
            viewModel.dispose()
        }

    @Test
    fun previousItemsStayVisibleAndNarrowWhileTheNextRequestIsPending() =
        runTest {
            val viewModel = editorViewModel()
            viewModel.dispatch(EditorIntent.RequestCompletion(filterText = "a"))
            assertEquals(3, viewModel.awaitCompletionItems().size)

            viewModel.dispatch(EditorIntent.RequestCompletion(filterText = "alph", automatic = true))
            advanceTimeBy(EditorViewModel.COMPLETION_DEBOUNCE_MS - 1)

            val pending = viewModel.state.value.completionState
            assertTrue(pending.isVisible)
            assertFalse(pending.isLoading, "a spinner replaced items that were still usable")
            assertEquals(listOf("alpha", "alphabet"), pending.items.map { it.label })
            viewModel.dispose()
        }

    @Test
    fun theSelectedItemIsResolvedForItsDocumentation() =
        runTest {
            coEvery { languageRegistry.provideCompletions(any(), any(), any()) } returns
                CompletionList(listOf(lazyItem("alpha")), isIncomplete = false)
            coEvery { languageRegistry.resolveCompletion(any()) } answers {
                firstArg<CompletionItem>().copy(documentation = "Alpha docs", resolveData = null)
            }
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.RequestCompletion(filterText = "al"))
            val documentation =
                viewModel.state
                    .first { it.completionState.selectedItem?.documentation != null }
                    .completionState.selectedItem
                    ?.documentation

            assertEquals("Alpha docs", documentation)
            viewModel.dispose()
        }

    @Test
    fun acceptingAnUnresolvedItemFetchesItsExtraEditsFirst() =
        runTest {
            val importEdit = TextEditData(TextRange(TextPosition(0, 0), TextPosition(0, 0)), "import a\n")
            coEvery { languageRegistry.resolveCompletion(any()) } answers {
                firstArg<CompletionItem>().copy(additionalTextEdits = listOf(importEdit), resolveData = null)
            }
            val viewModel = editorViewModel()

            viewModel.dispatch(EditorIntent.ApplyCompletion(lazyItem("alpha")))
            val content = viewModel.state.first { it.content.contains("alpha") }.content

            assertEquals("import a\nalpha", content)
            viewModel.dispose()
        }

    private fun item(label: String) = CompletionItem(label = label)

    /** An item a language server offers to complete on request. */
    private fun lazyItem(label: String) = CompletionItem(label = label, providerId = "server", resolveData = "{}")

    /** Providers run on a real background dispatcher, so results are awaited, not advanced to. */
    private suspend fun EditorViewModel.awaitCompletionItems(): List<CompletionItem> =
        state.first { !it.completionState.isLoading && it.completionState.items.isNotEmpty() }.completionState.items

    private fun editorViewModel(): EditorViewModel =
        EditorViewModel(
            fileSystem = fileSystem,
            settingsService = settingsService,
            languageRegistry = languageRegistry,
        )
}
