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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.editor.state.FindToggle
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Editor acceptance checks through the same intents used by the UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorFindReplaceRegressionTest {
    private fun testEditor(
        content: String,
        block: suspend TestScope.(EditorViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fs = mockk<FileSystem>()
        val settings = mockk<SettingsService>()
        coEvery { fs.readText(any(), any()) } returns Result.success(content)
        coEvery { fs.writeText(any(), any(), any()) } returns Result.failure(IllegalStateException("Disk is read-only"))
        every { settings.getCurrentSettings() } returns AllSettings()
        every { settings.settings } returns MutableStateFlow(AllSettings())
        val vm = EditorViewModel(fs, settings)
        try {
            vm.dispatch(EditorIntent.OpenFile("/audit/a.txt"))
            vm.state.first { it.activeTab?.name == "a.txt" }
            advanceUntilIdle()
            block(vm)
        } finally {
            vm.dispose()
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestScope.send(
        vm: EditorViewModel,
        intent: EditorIntent,
    ) {
        vm.dispatch(intent)
        if (intent is EditorIntent.OpenFile) {
            vm.state.first {
                it.activeTab?.name ==
                    intent.path.substringAfterLast(
                        '/',
                    )
            }
        }
        if (intent == EditorIntent.Save) vm.state.first { it.error?.startsWith("Failed to save") == true }
        advanceUntilIdle()
    }

    @Test
    fun replaceAllIsOneUndoableEdit() =
        testEditor("cat cat cat") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.UpdateFindQuery("cat"))
            send(vm, EditorIntent.UpdateReplaceText("dog"))
            send(vm, EditorIntent.ReplaceAll)
            assertEquals("dog dog dog", vm.state.value.content)
            send(vm, EditorIntent.Undo)
            assertEquals("cat cat cat", vm.state.value.content)
            send(vm, EditorIntent.Redo)
            assertEquals("dog dog dog", vm.state.value.content)
        }

    @Test
    fun regexReplacementExpandsCaptureGroups() =
        testEditor("item12 item34") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.ToggleFindOption(FindToggle.REGEX))
            send(vm, EditorIntent.UpdateFindQuery("item(\\d+)"))
            send(vm, EditorIntent.UpdateReplaceText("value\$1"))
            send(vm, EditorIntent.ReplaceAll)
            assertEquals("value12 value34", vm.state.value.content)
        }

    @Test
    fun namedGroupsAndLookaroundUseOriginalDocument() =
        testEditor("prefix12 prefix34") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.ToggleFindOption(FindToggle.REGEX))
            send(vm, EditorIntent.UpdateFindQuery("(?<=prefix)(?<number>\\d+)"))
            send(vm, EditorIntent.UpdateReplaceText("[\${number}]"))
            send(vm, EditorIntent.ReplaceAll)
            assertEquals("prefix[12] prefix[34]", vm.state.value.content)
            send(vm, EditorIntent.Undo)
            assertEquals("prefix12 prefix34", vm.state.value.content)
        }

    @Test
    fun invalidReplacementLeavesAllContentUntouched() =
        testEditor("item12 item34") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.ToggleFindOption(FindToggle.REGEX))
            send(vm, EditorIntent.UpdateFindQuery("item(\\d+)"))
            send(vm, EditorIntent.UpdateReplaceText("value\$2"))
            send(vm, EditorIntent.ReplaceAll)
            assertEquals("item12 item34", vm.state.value.content)
        }

    @Test
    fun replaceCurrentUsesExpandedLengthForNavigation() =
        testEditor("item12 item34") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.ToggleFindOption(FindToggle.REGEX))
            send(vm, EditorIntent.UpdateFindQuery("item(\\d+)"))
            send(vm, EditorIntent.UpdateReplaceText("item\$1x"))
            send(vm, EditorIntent.ReplaceCurrent)
            assertEquals("item12x item34", vm.state.value.content)
            assertEquals(
                8,
                vm.state.value.findReplaceState.currentMatch
                    ?.start,
            )
        }

    @Test
    fun invalidRegexDoesNotModifyDocument() =
        testEditor("original content") { vm ->
            send(vm, EditorIntent.OpenFindBar(true))
            send(vm, EditorIntent.ToggleFindOption(FindToggle.REGEX))
            send(vm, EditorIntent.UpdateFindQuery("["))
            send(vm, EditorIntent.UpdateReplaceText("replacement"))
            send(vm, EditorIntent.ReplaceAll)
            assertEquals("original content", vm.state.value.content)
            assertTrue(
                vm.state.value.findReplaceState.matches
                    .isEmpty(),
            )
        }

    @Test
    fun undoIsIsolatedBetweenTabs() =
        testEditor("original") { vm ->
            send(vm, EditorIntent.UpdateContent("edited A"))
            send(vm, EditorIntent.OpenFile("/audit/b.txt"))
            send(vm, EditorIntent.UpdateContent("edited B"))
            send(vm, EditorIntent.SwitchTab(0))
            send(vm, EditorIntent.Undo)
            assertEquals("original", vm.state.value.content)
            send(vm, EditorIntent.SwitchTab(1))
            assertEquals("edited B", vm.state.value.content)
        }

    @Test
    fun failedSaveRetainsDirtyBuffer() =
        testEditor("original") { vm ->
            send(vm, EditorIntent.UpdateContent("unsaved work\n"))
            send(vm, EditorIntent.Save)
            assertEquals("unsaved work\n", vm.state.value.content)
            assertTrue(
                vm.state.value.activeTab
                    ?.isDirty == true,
            )
        }

    @Test
    fun nextAndPreviousSearchWrap() =
        testEditor("cat dog cat") { vm ->
            send(vm, EditorIntent.OpenFindBar(false))
            send(vm, EditorIntent.UpdateFindQuery("cat"))
            assertEquals(0, vm.state.value.findReplaceState.currentMatchIndex)
            send(vm, EditorIntent.FindPrevious)
            assertEquals(1, vm.state.value.findReplaceState.currentMatchIndex)
            send(vm, EditorIntent.FindNext)
            assertEquals(0, vm.state.value.findReplaceState.currentMatchIndex)
        }
}
