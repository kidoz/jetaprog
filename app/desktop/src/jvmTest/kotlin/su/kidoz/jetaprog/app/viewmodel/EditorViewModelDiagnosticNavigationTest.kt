package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.api.services.LanguageDiagnostic
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import su.kidoz.jetaprog.plugins.support.SourcedDiagnosticsListener
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** F2 / Shift+F2 walk the document's diagnostics in order and wrap around. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelDiagnosticNavigationTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()
    private val languageRegistry = mockk<LanguageRegistry>()
    private val diagnosticsListener = slot<SourcedDiagnosticsListener>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success((1..10).joinToString("\n") { "line $it" })
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
        every { languageRegistry.onDiagnostics(capture(diagnosticsListener)) } returns Disposable { }
        every { languageRegistry.onWorkspaceEdit(any()) } returns Disposable { }
        coEvery { languageRegistry.notifyDocumentOpened(any(), any(), any()) } returns Unit
        every { languageRegistry.hasLspServer(any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun diagnostic(line: Int) =
        LanguageDiagnostic(range = TextRange(TextPosition(line, 2), TextPosition(line, 6)), message = "problem")

    @Test
    fun nextAndPreviousDiagnosticWalkInOrderAndWrap() =
        runTest {
            val viewModel =
                EditorViewModel(
                    fileSystem = fileSystem,
                    settingsService = settingsService,
                    languageRegistry = languageRegistry,
                )
            viewModel.dispatch(EditorIntent.OpenFile("/w/A.kt"))
            viewModel.state.first { it.tabs.size == 1 }
            diagnosticsListener.captured("server", "file:///w/A.kt", listOf(diagnostic(7), diagnostic(2)))
            viewModel.state.first { it.diagnostics.size == 2 }

            viewModel.dispatch(EditorIntent.GoToNextDiagnostic)
            assertEquals(TextPosition(2, 2), viewModel.state.value.cursor.position)
            viewModel.dispatch(EditorIntent.GoToNextDiagnostic)
            assertEquals(TextPosition(7, 2), viewModel.state.value.cursor.position)
            viewModel.dispatch(EditorIntent.GoToNextDiagnostic)
            assertEquals(TextPosition(2, 2), viewModel.state.value.cursor.position, "wraps to the first")

            viewModel.dispatch(EditorIntent.GoToPreviousDiagnostic)
            assertEquals(TextPosition(7, 2), viewModel.state.value.cursor.position, "wraps to the last")
            viewModel.dispose()
        }
}
