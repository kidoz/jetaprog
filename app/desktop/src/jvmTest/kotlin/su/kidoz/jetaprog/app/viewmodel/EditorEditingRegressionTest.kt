package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for document transitions, delayed saves, and completion edits. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorEditingRegressionTest {
    private fun testEditor(
        autoSave: Boolean = false,
        block: suspend TestScope.(EditorViewModel, MutableMap<String, String>) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val disk = ConcurrentHashMap(mapOf("/a.kt" to "cat cat", "/b.kt" to "dog dog"))
        val fs = mockk<FileSystem>()
        coEvery { fs.readText(any(), any()) } answers { Result.success(disk.getValue(firstArg())) }
        coEvery { fs.writeText(any(), any(), any()) } answers {
            disk[firstArg()] = secondArg()
            Result.success(Unit)
        }
        val settings = mockk<SettingsService>()
        val all = AllSettings().let { it.copy(editor = it.editor.copy(autoSave = autoSave)) }
        every { settings.getCurrentSettings() } returns all
        every { settings.settings } returns MutableStateFlow(all)
        val vm = EditorViewModel(fs, settings)
        try {
            block(vm, disk)
        } finally {
            vm.dispose()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun openingFileRefreshesReplacementMatches() =
        testEditor { vm, _ ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.OpenFindBar(true))
            vm.perform(EditorIntent.UpdateFindQuery("cat"))
            vm.perform(EditorIntent.UpdateReplaceText("fox"))
            vm.perform(EditorIntent.OpenFile("/b.kt"))
            assertTrue(
                vm.state.value.findReplaceState.matches
                    .isEmpty(),
            )
            vm.perform(EditorIntent.ReplaceAll)
            assertEquals("dog dog", vm.state.value.content)
            vm.perform(EditorIntent.SwitchTab(0))
            vm.perform(EditorIntent.ReplaceCurrent)
            assertEquals("fox cat", vm.state.value.content)
        }

    @Test
    fun autoSavePersistsBothTabsWithoutChangingActiveDocument() =
        testEditor(autoSave = true) { vm, disk ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.UpdateContent("edited A  "))
            vm.perform(EditorIntent.OpenFile("/b.kt"))
            vm.perform(EditorIntent.UpdateContent("edited B\n"))
            advanceUntilIdle()
            vm.state.first { it.tabs.none { tab -> tab.isDirty } }
            assertEquals("edited A\n", disk["/a.kt"])
            assertEquals("edited B\n", disk["/b.kt"])
            assertEquals(DocumentUri.file("/b.kt"), vm.state.value.activeDocumentUri)
            assertEquals("edited B\n", vm.state.value.content)
            vm.perform(EditorIntent.SwitchTab(0))
            assertEquals("edited A\n", vm.state.value.content)
            vm.perform(EditorIntent.Undo)
            assertEquals("edited A  ", vm.state.value.content)
        }

    @Test
    fun editingAnotherTabDoesNotPostponeFirstTabsSave() =
        testEditor(autoSave = true) { vm, disk ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.UpdateContent("edited A\n"))
            advanceTimeBy(600)
            vm.perform(EditorIntent.OpenFile("/b.kt"))
            vm.perform(EditorIntent.UpdateContent("edited B\n"))
            advanceTimeBy(401)
            vm.state.first { !it.tabs.first().isDirty }
            assertEquals("edited A\n", disk["/a.kt"])
            assertEquals("dog dog", disk["/b.kt"])
            assertTrue(
                vm.state.value.tabs[1]
                    .isDirty,
            )
        }

    @Test
    fun closedTabsAreNotAutoSaved() =
        testEditor(autoSave = true) { vm, disk ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.UpdateContent("discarded\n"))
            vm.perform(EditorIntent.CloseTab(0, discardChanges = true))
            advanceUntilIdle()
            assertEquals("cat cat", disk["/a.kt"])
            assertTrue(
                vm.state.value.tabs
                    .isEmpty(),
            )
        }

    @Test
    fun staleAutoSaveDoesNotSaveNewerEditsEarly() =
        testEditor(autoSave = true) { vm, disk ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.UpdateContent("first\n"))
            val version = vm.state.value.documentVersion
            vm.perform(EditorIntent.UpdateContent("second\n"))
            vm.perform(EditorIntent.AutoSave(DocumentUri.file("/a.kt"), version))
            assertEquals("cat cat", disk["/a.kt"])
            assertTrue(
                vm.state.value.activeTab
                    ?.isDirty == true,
            )
        }

    @Test
    fun backgroundAutoSavePreservesExternalChanges() =
        testEditor(autoSave = true) { vm, disk ->
            vm.perform(EditorIntent.OpenFile("/a.kt"))
            vm.perform(EditorIntent.UpdateContent("my edits\n"))
            vm.perform(EditorIntent.OpenFile("/b.kt"))
            disk["/a.kt"] = "external\n"
            advanceUntilIdle()
            vm.state.first { it.error?.contains("changed on disk") == true }
            assertEquals("external\n", disk["/a.kt"])
            assertTrue(
                vm.state.value.tabs
                    .first()
                    .isDirty,
            )
            assertEquals(DocumentUri.file("/b.kt"), vm.state.value.activeDocumentUri)
            assertFalse(vm.state.value.isSaving)
        }
}
