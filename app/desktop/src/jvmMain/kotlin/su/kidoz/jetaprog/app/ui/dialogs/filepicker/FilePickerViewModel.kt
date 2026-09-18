package su.kidoz.jetaprog.app.ui.dialogs.filepicker

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.io.File

/**
 * Drives the IDE's own file picker: lists directories through [FileSystem],
 * tracks selection and the typed path, creates folders, guards overwrites in
 * save mode, and reports the chosen path back to the host as an effect.
 *
 * One app-wide instance serves every "browse" action; the request's
 * [FilePickerPurpose] tells the host what to do with the result.
 */
public class FilePickerViewModel(
    private val fileSystem: FileSystem,
    private val homeDirectory: String = System.getProperty("user.home").orEmpty(),
    private val roots: List<String> = File.listRoots().map { it.absolutePath },
) : MviViewModel<FilePickerIntent, FilePickerState, FilePickerEffect>(FilePickerState()) {
    private var loadJob: Job? = null

    override suspend fun handleIntent(intent: FilePickerIntent) {
        when (intent) {
            is FilePickerIntent.Show -> show(intent.request)
            FilePickerIntent.Dismiss -> reset()
            is FilePickerIntent.NavigateTo -> load(intent.path)
            FilePickerIntent.NavigateUp -> fileSystem.parent(currentState.currentDirectory)?.let { load(it) }
            is FilePickerIntent.PathInputChanged -> updateState { copy(pathInput = intent.text, error = null) }
            is FilePickerIntent.SelectEntry -> select(intent.path)
            is FilePickerIntent.ActivateEntry -> activate(intent.path)
            is FilePickerIntent.FileNameChanged -> updateState { copy(fileName = intent.name, error = null) }
            is FilePickerIntent.SetShowHidden -> updateState { copy(showHidden = intent.show) }
            FilePickerIntent.StartNewFolder -> updateState { copy(newFolderName = "", error = null) }
            is FilePickerIntent.NewFolderNameChanged -> updateState { copy(newFolderName = intent.name, error = null) }
            FilePickerIntent.CancelNewFolder -> updateState { copy(newFolderName = null, error = null) }
            FilePickerIntent.Confirm -> confirm()
            FilePickerIntent.ConfirmOverwrite -> currentState.overwriteTarget?.let { pick(it) }
            FilePickerIntent.CancelOverwrite -> updateState { copy(overwriteTarget = null) }
        }
    }

    private suspend fun show(request: FilePickerRequest) {
        val shortcuts = buildPlaces()
        updateState {
            FilePickerState(
                isVisible = true,
                request = request,
                fileName = request.suggestedFileName,
                showHidden = showHidden,
                places = shortcuts,
            )
        }
        load(nearestExistingDirectory(request.initialDirectory))
    }

    /** Hides the picker; the hidden-files preference survives until the app exits. */
    private fun reset() {
        loadJob?.cancel()
        updateState { FilePickerState(showHidden = showHidden) }
    }

    private suspend fun pick(path: String) {
        val purpose = currentState.request?.purpose ?: return
        reset()
        emitEffect(FilePickerEffect.Picked(purpose, path))
    }

    private fun select(path: String) {
        val entry = currentState.entries.firstOrNull { it.path == path } ?: return
        updateState {
            copy(
                selectedPath = path,
                fileName = if (mode == FilePickerMode.SAVE_FILE && entry.isFile) entry.name else fileName,
                error = null,
            )
        }
    }

    private suspend fun activate(path: String) {
        val entry = currentState.entries.firstOrNull { it.path == path } ?: return
        if (entry.isDirectory) {
            load(entry.path)
            return
        }
        select(path)
        chooseCurrentTarget()
    }

    private suspend fun confirm() {
        val state = currentState
        when {
            !state.canConfirm -> Unit
            state.newFolderName != null -> createFolder(state.newFolderName)
            state.isPathEdited -> goToTypedPath(state.pathInput)
            else -> chooseCurrentTarget()
        }
    }

    /** Chooses what the listing currently points at, per mode. */
    private suspend fun chooseCurrentTarget() {
        val state = currentState
        when (state.mode) {
            FilePickerMode.OPEN_DIRECTORY -> {
                pick(state.selectedEntry?.path ?: state.currentDirectory)
            }

            FilePickerMode.OPEN_FILE -> {
                val entry = state.selectedEntry ?: return
                if (entry.isDirectory) load(entry.path) else pick(entry.path)
            }

            FilePickerMode.SAVE_FILE -> {
                chooseSaveTarget(state)
            }
        }
    }

    private suspend fun chooseSaveTarget(state: FilePickerState) {
        val target = resolveTyped(state.fileName, base = state.currentDirectory)
        val parent = target?.let(fileSystem::parent)
        when {
            target == null -> showError("“${state.fileName.trim()}” is not a valid file name.")
            fileSystem.isDirectory(target) -> load(target)
            fileSystem.exists(target) -> updateState { copy(overwriteTarget = target, error = null) }
            parent == null || !fileSystem.isDirectory(parent) -> showError("Folder does not exist: $parent")
            else -> pick(target)
        }
    }

    /** Enter in the path field: go where the typed path points. */
    private suspend fun goToTypedPath(text: String) {
        val state = currentState
        val target = resolveTyped(text, base = state.currentDirectory)
        val parent = target?.let(fileSystem::parent)
        when {
            target == null -> {
                showError("“${text.trim()}” is not a valid path.")
            }

            fileSystem.isDirectory(target) -> {
                load(target)
            }

            fileSystem.isFile(target) && state.mode == FilePickerMode.OPEN_FILE -> {
                pick(target)
            }

            fileSystem.isFile(target) && parent != null -> {
                load(parent, select = target, fileName = fileSystem.fileName(target))
            }

            state.mode == FilePickerMode.SAVE_FILE && parent != null && fileSystem.isDirectory(parent) -> {
                load(parent, fileName = fileSystem.fileName(target))
            }

            else -> {
                showError("Path does not exist: $target")
            }
        }
    }

    private suspend fun createFolder(rawName: String) {
        val name = rawName.trim()
        val directory = currentState.currentDirectory
        val isPlainName = name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' }
        val target = if (isPlainName) resolveTyped(name, base = directory) else null
        when {
            target == null -> {
                showError("“$name” is not a valid folder name.")
            }

            fileSystem.exists(target) -> {
                showError("“$name” already exists in this folder.")
            }

            else -> {
                fileSystem.createDirectory(target).fold(
                    onSuccess = {
                        updateState { copy(newFolderName = null) }
                        load(directory, select = target)
                    },
                    onFailure = { showError("Could not create folder “$name”.") },
                )
            }
        }
    }

    /**
     * Lists [directory] off the intent queue so a slow mount cannot block Cancel.
     * A newer navigation cancels the one still in flight.
     */
    private fun load(
        directory: String,
        select: String? = null,
        fileName: String? = null,
    ) {
        loadJob?.cancel()
        updateState { copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val listing = fileSystem.listDirectory(directory)
                updateState {
                    if (!isVisible) return@updateState this
                    listing.fold(
                        onSuccess = { entries ->
                            copy(
                                currentDirectory = directory,
                                pathInput = directory,
                                allEntries = entries.sortedWith(ENTRY_ORDER),
                                selectedPath = select,
                                fileName = fileName ?: this.fileName,
                                isLoading = false,
                            )
                        },
                        onFailure = {
                            copy(
                                // Nothing listed yet: still anchor the picker somewhere.
                                currentDirectory = currentDirectory.ifEmpty { directory },
                                pathInput = if (currentDirectory.isEmpty()) directory else pathInput,
                                isLoading = false,
                                error = "Cannot read folder: $directory",
                            )
                        },
                    )
                }
            }
    }

    private fun showError(message: String) {
        updateState { copy(error = message) }
    }

    /** Resolves typed text against [base], expanding a leading `~`; null when malformed. */
    private fun resolveTyped(
        text: String,
        base: String,
    ): String? {
        val trimmed = text.trim()
        val expanded =
            when {
                trimmed.isEmpty() -> return null
                trimmed == "~" -> homeDirectory
                trimmed.startsWith("~/") || trimmed.startsWith("~\\") -> homeDirectory + trimmed.drop(1)
                else -> trimmed
            }
        // Paths.get rejects malformed input (for example NUL) with an unchecked exception.
        return runCatching { fileSystem.resolve(base.ifEmpty { homeDirectory }, expanded) }.getOrNull()
    }

    private suspend fun nearestExistingDirectory(path: String): String {
        var candidate = resolveTyped(path, base = homeDirectory)
        while (candidate != null && !fileSystem.isDirectory(candidate)) {
            candidate = fileSystem.parent(candidate)
        }
        return candidate ?: homeDirectory.ifEmpty { roots.firstOrNull().orEmpty() }
    }

    private suspend fun buildPlaces(): List<FilePickerPlace> {
        val home = listOf(FilePickerPlace("Home", homeDirectory)).filter { homeDirectory.isNotEmpty() }
        val folders =
            HOME_FOLDERS
                .map { FilePickerPlace(it, fileSystem.resolve(homeDirectory, it)) }
                .filter { homeDirectory.isNotEmpty() && fileSystem.isDirectory(it.path) }
        val drives = roots.map { FilePickerPlace(if (it == "/") "Computer" else it, it) }
        return home + folders + drives
    }

    private companion object {
        val HOME_FOLDERS = listOf("Desktop", "Documents", "Downloads", "Projects")

        val ENTRY_ORDER: Comparator<FileEntry> =
            compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
