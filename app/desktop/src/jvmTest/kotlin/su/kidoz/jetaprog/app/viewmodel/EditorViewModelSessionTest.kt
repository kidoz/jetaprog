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
class EditorViewModelSessionTest {
    private val fileSystem = mockk<FileSystem>()
    private val settingsService = mockk<SettingsService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { fileSystem.readText(any(), any()) } returns Result.success("fun main() {}")
        every { settingsService.getCurrentSettings() } returns AllSettings()
        every { settingsService.settings } returns MutableStateFlow(AllSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoreSessionReopensSurvivingTabsWithActiveCursor() =
        runTest {
            coEvery { fileSystem.exists(any()) } returns true
            coEvery { fileSystem.exists("/project/src/Missing.kt") } returns false
            val viewModel = EditorViewModel(fileSystem, settingsService)

            val cursor = TextPosition(0, 5)
            viewModel.dispatch(
                EditorIntent.RestoreSession(
                    filePaths =
                        listOf(
                            "/project/src/A.kt",
                            "/project/src/Missing.kt",
                            "/project/src/B.kt",
                        ),
                    activeTabIndex = 2,
                    cursor = cursor,
                ),
            )
            val state =
                viewModel.state.first {
                    it.tabs.size == 2 && it.cursor.position == cursor
                }

            assertEquals(listOf("A.kt", "B.kt"), state.tabs.map { it.name })
            assertEquals(1, state.activeTabIndex)
            assertEquals("file:///project/src/B.kt", state.activeDocumentUri?.value)
            viewModel.dispose()
        }
}
