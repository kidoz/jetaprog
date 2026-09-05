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
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelReloadTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reloadFileReplacesCleanBufferWithDiskContent() =
        runTest {
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns Result.success("original")
            val viewModel = EditorViewModel(fileSystem, settingsService)
            viewModel.dispatch(EditorIntent.OpenFile("/proj/A.kt"))
            viewModel.state.first { it.activeTab?.name == "A.kt" }
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns Result.success("from disk")

            viewModel.dispatch(EditorIntent.ReloadFile("/proj/A.kt"))

            val state = viewModel.state.first { it.content == "from disk" }
            assertEquals(false, state.activeTab?.isDirty)
            viewModel.dispose()
        }

    @Test
    fun reloadFileKeepsDirtyBufferUntouched() =
        runTest {
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns Result.success("original")
            val viewModel = EditorViewModel(fileSystem, settingsService)
            viewModel.dispatch(EditorIntent.OpenFile("/proj/A.kt"))
            viewModel.state.first { it.activeTab?.name == "A.kt" }
            viewModel.dispatch(EditorIntent.UpdateContent("user edits"))
            viewModel.state.first { it.content == "user edits" }
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns Result.success("from disk")

            viewModel.dispatch(EditorIntent.ReloadFile("/proj/A.kt"))

            assertEquals("user edits", viewModel.state.value.content)
            viewModel.dispose()
        }

    @Test
    fun reloadFileClampsCaretIntoShorterContent() =
        runTest {
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns
                Result.success("line one\nline two\nline three\n")
            val viewModel = EditorViewModel(fileSystem, settingsService)
            viewModel.dispatch(EditorIntent.OpenFile("/proj/A.kt"))
            viewModel.state.first { it.activeTab?.name == "A.kt" }
            viewModel.dispatch(EditorIntent.MoveToLineEnd)
            viewModel.state.first { it.cursor.position == TextPosition(0, 8) }
            coEvery { fileSystem.readText("/proj/A.kt", any()) } returns Result.success("short")

            viewModel.dispatch(EditorIntent.ReloadFile("/proj/A.kt"))

            val state = viewModel.state.first { it.content == "short" }
            assertEquals(TextPosition(0, 5), state.cursor.position)
            viewModel.dispose()
        }
}
