package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSelectionRegressionTest {
    @Test
    fun selectionsSurviveTabSwitchesAndDriveSelectionDependentActions() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fileSystem = mockk<FileSystem>()
            coEvery { fileSystem.readText("/a.kt", any()) } returns Result.success("abcdef")
            coEvery { fileSystem.readText("/b.kt", any()) } returns Result.success("123456")
            val settingsService = mockk<SettingsService>()
            every { settingsService.getCurrentSettings() } returns AllSettings()
            every { settingsService.settings } returns MutableStateFlow(AllSettings())
            val viewModel = EditorViewModel(fileSystem, settingsService)
            try {
                val firstSelection = TextRange(TextPosition(0, 1), TextPosition(0, 4))
                val secondSelection = TextRange(TextPosition(0, 2), TextPosition(0, 5))
                viewModel.perform(EditorIntent.OpenFile("/a.kt"))
                viewModel.perform(EditorIntent.Select(firstSelection, reversed = true))
                viewModel.perform(EditorIntent.OpenFile("/b.kt"))
                viewModel.perform(EditorIntent.Select(secondSelection))

                viewModel.perform(EditorIntent.SwitchTab(0))
                assertEquals(firstSelection.start, viewModel.state.value.cursor.position)
                assertEquals(firstSelection.end, viewModel.state.value.cursor.anchor)
                viewModel.perform(EditorIntent.Copy)
                assertEquals(
                    "bcd",
                    viewModel.effects
                        .filterIsInstance<EditorEffect.CopyToClipboard>()
                        .first()
                        .text,
                )

                viewModel.perform(EditorIntent.SwitchTab(1))
                assertEquals(secondSelection.end, viewModel.state.value.cursor.position)
                assertEquals(secondSelection.start, viewModel.state.value.cursor.anchor)
                viewModel.perform(EditorIntent.Copy)
                assertEquals(
                    "345",
                    viewModel.effects
                        .filterIsInstance<EditorEffect.CopyToClipboard>()
                        .first()
                        .text,
                )

                viewModel.perform(EditorIntent.Select(TextRange(TextPosition(0, 3), TextPosition(0, 3))))
                assertFalse(viewModel.state.value.cursor.hasSelection)
                assertEquals(TextPosition(0, 3), viewModel.state.value.cursor.position)
                viewModel.perform(EditorIntent.SwitchTab(0))
                viewModel.perform(EditorIntent.SwitchTab(1))
                assertFalse(viewModel.state.value.cursor.hasSelection)
                assertEquals(TextPosition(0, 3), viewModel.state.value.cursor.position)
                assertTrue(viewModel.state.value.cursor.anchor == null)
            } finally {
                viewModel.dispose()
                Dispatchers.resetMain()
            }
        }
}
