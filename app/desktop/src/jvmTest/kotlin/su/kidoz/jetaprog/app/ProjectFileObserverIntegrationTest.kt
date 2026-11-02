package su.kidoz.jetaprog.app

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import su.kidoz.jetaprog.platform.filesystem.FileSystemEventType
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.AllSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the project session's event pipeline with real disk I/O and deterministic watcher delivery. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectFileObserverIntegrationTest {
    @Test
    fun projectEventsReloadCleanTabsAndRetainDirtyTabsWhileRefreshingIndexesAndGit() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val directory = Files.createTempDirectory("jetaprog-project-events").toFile()
            val file = directory.resolve("main.txt").apply { writeText("original\n") }
            val events = MutableSharedFlow<FileSystemEvent>()
            val fileSystem =
                object : FileSystem by JvmFileSystem() {
                    override fun watch(
                        path: String,
                        recursive: Boolean,
                    ) = events
                }
            val settings = mockk<SettingsService>()
            every { settings.getCurrentSettings() } returns AllSettings()
            every { settings.settings } returns MutableStateFlow(AllSettings())
            val editor = EditorViewModel(fileSystem, settings)
            val indexed = mutableListOf<String>()
            var refreshes = 0
            val watcher =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observeProjectFiles(directory.path, fileSystem, editor, { refreshes++ }, 1) { indexed += it.path }
                }
            try {
                editor.perform(EditorIntent.OpenFile(file.path))
                file.writeText("external clean\n")
                events.emit(FileSystemEvent(FileSystemEventType.MODIFIED, file.path, false))
                editor.state.first { it.content == "external clean\n" }
                editor.perform(EditorIntent.UpdateContent("my unsaved work\n"))
                file.writeText("external dirty\n")
                events.emit(FileSystemEvent(FileSystemEventType.MODIFIED, file.path, false))
                advanceUntilIdle()
                assertEquals("my unsaved work\n", editor.state.value.content)
                assertEquals(listOf(file.path, file.path), indexed)
                // The debounce belongs to backgroundScope, so explicitly advance its clock.
                testScheduler.advanceTimeBy(2)
                testScheduler.runCurrent()
                assertTrue(refreshes > 0)
                editor.perform(EditorIntent.Save)
                assertEquals("external dirty\n", file.readText())
            } finally {
                watcher.cancel()
                editor.dispose()
                directory.deleteRecursively()
                Dispatchers.resetMain()
            }
        }
}
