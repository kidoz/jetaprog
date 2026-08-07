package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun switchingTabsRetainsDirtyContentInMemory() =
        runTest {
            coEvery { fileSystem.readText("/project/A.kt", any()) } returns Result.success("original A")
            coEvery { fileSystem.readText("/project/B.kt", any()) } returns Result.success("original B")
            val viewModel = EditorViewModel(fileSystem, settingsService)

            viewModel.dispatch(EditorIntent.OpenFile("/project/A.kt"))
            viewModel.state.first { it.activeTab?.name == "A.kt" }
            viewModel.dispatch(EditorIntent.UpdateContent("edited A"))
            viewModel.state.first { it.content == "edited A" }
            viewModel.dispatch(EditorIntent.OpenFile("/project/B.kt"))
            viewModel.state.first { it.activeTab?.name == "B.kt" }
            viewModel.dispatch(EditorIntent.SwitchTab(0))
            val restored = viewModel.state.first { it.activeTab?.name == "A.kt" }

            assertEquals("edited A", restored.content)
            assertTrue(restored.activeTab?.isDirty == true)
            viewModel.dispose()
        }

    @Test
    fun dirtyTabRequiresConfirmationBeforeClosing() =
        runTest {
            val viewModel = EditorViewModel(fileSystem, settingsService)
            viewModel.dispatch(EditorIntent.OpenFile("/project/A.kt"))
            viewModel.state.first { it.activeTab != null }
            viewModel.dispatch(EditorIntent.UpdateContent("edited"))
            viewModel.state.first { it.activeTab?.isDirty == true }
            val confirmation = async { viewModel.effects.first { it is EditorEffect.ShowConfirmation } }

            viewModel.dispatch(EditorIntent.CloseTab(0))
            val effect = confirmation.await() as EditorEffect.ShowConfirmation

            assertEquals(1, viewModel.state.value.tabs.size)
            effect.onConfirm()
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.tabs
                    .isEmpty(),
            )
            viewModel.dispose()
        }
}
