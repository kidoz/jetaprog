package su.kidoz.jetaprog.app

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.viewmodel.EditorViewModel
import su.kidoz.jetaprog.editor.state.EditorIntent
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import su.kidoz.jetaprog.platform.filesystem.FileSystemEventType

/** Keeps project editor buffers, indexes and Git in sync with the same filesystem event stream. */
internal suspend fun observeProjectFiles(
    projectPath: String,
    fileSystem: FileSystem,
    editor: EditorViewModel,
    refreshGit: () -> Unit,
    debounceMillis: Long,
    onFileEvent: suspend (FileSystemEvent) -> Unit,
): Unit =
    coroutineScope {
        var gitRefresh: Job? = null
        fileSystem.watch(projectPath, recursive = true).collect { event ->
            if (!event.isDirectory && event.type != FileSystemEventType.DELETED) {
                editor.dispatch(EditorIntent.ReloadFile(event.path))
            }
            onFileEvent(event)
            gitRefresh?.cancel()
            gitRefresh =
                launch {
                    delay(debounceMillis)
                    refreshGit()
                }
        }
    }
