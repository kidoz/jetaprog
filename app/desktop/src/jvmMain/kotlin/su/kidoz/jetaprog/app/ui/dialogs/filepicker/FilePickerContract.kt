package su.kidoz.jetaprog.app.ui.dialogs.filepicker

import androidx.compose.runtime.Immutable
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State
import su.kidoz.jetaprog.platform.filesystem.FileEntry

/** What the picker lets the user choose. */
public enum class FilePickerMode {
    /** Choose an existing directory. Only directories are listed. */
    OPEN_DIRECTORY,

    /** Choose an existing file. */
    OPEN_FILE,

    /** Choose a directory and a file name to write to; the file may not exist yet. */
    SAVE_FILE,
}

/** Why the picker was opened; the host routes the picked path by this. */
public enum class FilePickerPurpose {
    OPEN_PROJECT,
    NEW_PROJECT_LOCATION,
    CLONE_DESTINATION,
    OPEN_FILE,
    SAVE_FILE_AS,
}

/**
 * One request to show the picker.
 *
 * @property purpose Routing key echoed back in [FilePickerEffect.Picked].
 * @property mode What can be chosen.
 * @property title Dialog title.
 * @property confirmLabel Label of the primary button.
 * @property initialDirectory Directory to start in. When it does not exist the
 * picker starts in its nearest existing ancestor, or the home directory.
 * @property suggestedFileName Initial file name in [FilePickerMode.SAVE_FILE] mode.
 */
public data class FilePickerRequest(
    val purpose: FilePickerPurpose,
    val mode: FilePickerMode,
    val title: String,
    val confirmLabel: String,
    val initialDirectory: String = "",
    val suggestedFileName: String = "",
) {
    public companion object {
        /** Pick a project directory to open. */
        public fun openProject(initialDirectory: String): FilePickerRequest =
            FilePickerRequest(
                purpose = FilePickerPurpose.OPEN_PROJECT,
                mode = FilePickerMode.OPEN_DIRECTORY,
                title = "Open Project",
                confirmLabel = "Open",
                initialDirectory = initialDirectory,
            )

        /** Pick the parent directory for a new project. */
        public fun newProjectLocation(initialDirectory: String): FilePickerRequest =
            FilePickerRequest(
                purpose = FilePickerPurpose.NEW_PROJECT_LOCATION,
                mode = FilePickerMode.OPEN_DIRECTORY,
                title = "Select Project Location",
                confirmLabel = "Select",
                initialDirectory = initialDirectory,
            )

        /** Pick the directory a repository is cloned into. */
        public fun cloneDestination(initialDirectory: String): FilePickerRequest =
            FilePickerRequest(
                purpose = FilePickerPurpose.CLONE_DESTINATION,
                mode = FilePickerMode.OPEN_DIRECTORY,
                title = "Select Destination Directory",
                confirmLabel = "Select",
                initialDirectory = initialDirectory,
            )

        /** Pick a single file to open in the editor. */
        public fun openFile(initialDirectory: String): FilePickerRequest =
            FilePickerRequest(
                purpose = FilePickerPurpose.OPEN_FILE,
                mode = FilePickerMode.OPEN_FILE,
                title = "Open File",
                confirmLabel = "Open",
                initialDirectory = initialDirectory,
            )

        /** Pick where to save the active document. */
        public fun saveFileAs(
            initialDirectory: String,
            suggestedFileName: String,
        ): FilePickerRequest =
            FilePickerRequest(
                purpose = FilePickerPurpose.SAVE_FILE_AS,
                mode = FilePickerMode.SAVE_FILE,
                title = "Save File As",
                confirmLabel = "Save",
                initialDirectory = initialDirectory,
                suggestedFileName = suggestedFileName,
            )
    }
}

/** A shortcut in the picker's places rail (home, common folders, file system roots). */
public data class FilePickerPlace(
    val label: String,
    val path: String,
)

/**
 * State of the file picker dialog.
 *
 * @property isVisible Whether the dialog is shown.
 * @property request The request being served; null while hidden.
 * @property currentDirectory Directory whose contents are listed.
 * @property pathInput Text of the editable path field; differs from
 * [currentDirectory] while the user is typing a path.
 * @property allEntries Every entry of [currentDirectory], directories first.
 * @property selectedPath Path of the highlighted entry.
 * @property fileName File name field, used in [FilePickerMode.SAVE_FILE] mode.
 * @property showHidden Whether hidden entries are listed.
 * @property places Shortcuts shown in the places rail.
 * @property newFolderName Name typed into the inline New Folder row; null when
 * no folder is being created.
 * @property overwriteTarget Existing file awaiting overwrite confirmation.
 * @property isLoading Whether a directory listing is in flight.
 * @property error Problem shown at the bottom of the dialog.
 */
@Immutable
public data class FilePickerState(
    val isVisible: Boolean = false,
    val request: FilePickerRequest? = null,
    val currentDirectory: String = "",
    val pathInput: String = "",
    val allEntries: List<FileEntry> = emptyList(),
    val selectedPath: String? = null,
    val fileName: String = "",
    val showHidden: Boolean = false,
    val places: List<FilePickerPlace> = emptyList(),
    val newFolderName: String? = null,
    val overwriteTarget: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) : State {
    /** Mode of the active request. */
    val mode: FilePickerMode
        get() = request?.mode ?: FilePickerMode.OPEN_DIRECTORY

    /** Entries listed for the current mode and hidden-files setting. */
    val entries: List<FileEntry>
        get() =
            allEntries.filter { entry ->
                (showHidden || !entry.isHidden) &&
                    (mode != FilePickerMode.OPEN_DIRECTORY || entry.isDirectory)
            }

    /** The highlighted entry, when it is currently listed. */
    val selectedEntry: FileEntry?
        get() = entries.firstOrNull { it.path == selectedPath }

    /** Whether the user typed a path that has not been navigated to yet. */
    val isPathEdited: Boolean
        get() = pathInput.trim() != currentDirectory

    /** Whether the primary action can run. */
    val canConfirm: Boolean
        get() =
            when {
                !isVisible || overwriteTarget != null -> false
                newFolderName != null -> newFolderName.isNotBlank()
                isPathEdited -> pathInput.isNotBlank()
                mode == FilePickerMode.OPEN_DIRECTORY -> currentDirectory.isNotEmpty()
                mode == FilePickerMode.OPEN_FILE -> selectedEntry != null
                else -> fileName.isNotBlank()
            }
}

/** User intents for the file picker dialog. */
public sealed interface FilePickerIntent : Intent {
    /** Show the picker for [request]. */
    public data class Show(
        val request: FilePickerRequest,
    ) : FilePickerIntent

    /** Close the picker without choosing anything. */
    public data object Dismiss : FilePickerIntent

    /** List [path], from the places rail or programmatically. */
    public data class NavigateTo(
        val path: String,
    ) : FilePickerIntent

    /** Go to the parent of the current directory. */
    public data object NavigateUp : FilePickerIntent

    /** The path field text changed. */
    public data class PathInputChanged(
        val text: String,
    ) : FilePickerIntent

    /** An entry was clicked once. */
    public data class SelectEntry(
        val path: String,
    ) : FilePickerIntent

    /** An entry was double-clicked: enter a directory or choose a file. */
    public data class ActivateEntry(
        val path: String,
    ) : FilePickerIntent

    /** The file name field changed (save mode). */
    public data class FileNameChanged(
        val name: String,
    ) : FilePickerIntent

    /** Show or hide hidden entries. */
    public data class SetShowHidden(
        val show: Boolean,
    ) : FilePickerIntent

    /** Open the inline New Folder row. */
    public data object StartNewFolder : FilePickerIntent

    /** The New Folder name changed. */
    public data class NewFolderNameChanged(
        val name: String,
    ) : FilePickerIntent

    /** Close the inline New Folder row without creating anything. */
    public data object CancelNewFolder : FilePickerIntent

    /**
     * The primary action (button or Enter). In order: creates the pending new
     * folder, navigates to an edited path, or chooses the current target.
     */
    public data object Confirm : FilePickerIntent

    /** Overwrite the existing file awaiting confirmation. */
    public data object ConfirmOverwrite : FilePickerIntent

    /** Keep the existing file and return to the picker. */
    public data object CancelOverwrite : FilePickerIntent
}

/** One-time side effects emitted by the file picker dialog. */
public sealed interface FilePickerEffect : Effect {
    /** The user chose [path] for the request with [purpose]. */
    public data class Picked(
        val purpose: FilePickerPurpose,
        val path: String,
    ) : FilePickerEffect
}
