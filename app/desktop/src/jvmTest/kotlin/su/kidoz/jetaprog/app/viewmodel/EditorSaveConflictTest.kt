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
import su.kidoz.jetaprog.editor.state.EditorEffect
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSaveConflictTest {
    private val fileSystem = mockk<FileSystem>()
    private val settings = mockk<SettingsService>()
    private var disk = "original\n"
    private lateinit var editor: EditorViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.getCurrentSettings() } returns AllSettings()
        every { settings.settings } returns MutableStateFlow(AllSettings())
        coEvery { fileSystem.readText(any(), any()) } answers { Result.success(disk) }
        coEvery { fileSystem.writeText(any(), any(), any()) } answers {
            disk = secondArg()
            Result.success(Unit)
        }
        editor = EditorViewModel(fileSystem, settings)
    }

    @AfterTest
    fun tearDown() {
        editor.dispose()
        Dispatchers.resetMain()
    }

    @Test
    fun staleCleanBufferCannotOverwriteExternalChange() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            disk = "external\n"
            val state = editor.perform(EditorIntent.Save)
            assertEquals("external\n", disk)
            assertEquals("original\n", state.content)
            assertTrue(state.error.orEmpty().contains("changed on disk"))
            assertTrue(state.activeTab?.isDirty == true)
            coVerify(exactly = 0) { fileSystem.writeText(any(), any(), any()) }
        }

    @Test
    fun externalChangePreservesDirtyBufferAndDisk() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            editor.perform(EditorIntent.UpdateContent("my work\n"))
            disk = "external\n"
            editor.perform(EditorIntent.ReloadFile("/project/a.txt"))
            val state = editor.perform(EditorIntent.Save)
            assertEquals("my work\n", state.content)
            assertEquals("external\n", disk)
            assertTrue(state.activeTab?.isDirty == true)
        }

    @Test
    fun conflictReloadRequiresConfirmationAndClearsDirtyState() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            editor.perform(EditorIntent.UpdateContent("my work\n"))
            disk = "external\n"
            editor.perform(EditorIntent.Save)
            val confirmation =
                assertIs<EditorEffect.ShowConfirmation>(
                    editor.effects.first { it is EditorEffect.ShowConfirmation },
                )
            assertEquals("Reload from disk", confirmation.confirmLabel)
            assertEquals("my work\n", editor.state.value.content)
            confirmation.onCancel()
            assertEquals("my work\n", editor.state.value.content)
            confirmation.onConfirm()
            val state = editor.state.first { it.content == "external\n" }
            assertFalse(state.activeTab?.isDirty == true)
            assertEquals(null, editor.perform(EditorIntent.Save).error)
        }

    @Test
    fun reloadAndRepeatedSavesAdvanceDiskBaseline() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            disk = "external\n"
            editor.perform(EditorIntent.ReloadFile("/project/a.txt"))
            editor.perform(EditorIntent.UpdateContent("first edit\n"))
            assertEquals(null, editor.perform(EditorIntent.Save).error)
            editor.perform(EditorIntent.UpdateContent("second edit\n"))
            val state = editor.perform(EditorIntent.Save)
            assertEquals(null, state.error)
            assertFalse(state.activeTab?.isDirty == true)
            assertEquals("second edit\n", disk)
        }

    @Test
    fun missingFileIsNotSilentlyRecreated() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            coEvery { fileSystem.readText(any(), any()) } returns Result.failure(IllegalStateException("File deleted"))
            val state = editor.perform(EditorIntent.Save)
            assertTrue(state.error.orEmpty().contains("File deleted"))
            coVerify(exactly = 0) { fileSystem.writeText(any(), any(), any()) }
        }

    @Test
    fun backgroundCleanTabReloadDoesNotReplaceActiveTab() =
        runTest {
            editor.perform(EditorIntent.OpenFile("/project/a.txt"))
            editor.perform(EditorIntent.OpenFile("/project/b.txt"))
            editor.perform(EditorIntent.UpdateContent("dirty B\n"))
            disk = "external A\n"
            editor.perform(EditorIntent.ReloadFile("/project/a.txt"))
            assertEquals("dirty B\n", editor.state.value.content)
            assertEquals("external A\n", editor.perform(EditorIntent.SwitchTab(0)).content)
        }
}
