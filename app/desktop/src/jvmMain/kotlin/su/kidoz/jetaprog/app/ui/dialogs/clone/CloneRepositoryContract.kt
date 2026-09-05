package su.kidoz.jetaprog.app.ui.dialogs.clone

import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State

/**
 * State of the Clone Repository dialog.
 *
 * @property isVisible Whether the dialog is shown.
 * @property repositoryUrl The remote repository URL to clone from.
 * @property destinationDirectory Directory the project folder is created in.
 * @property projectName Target folder name; derived from the URL, editable.
 * @property isCloning Whether a clone is currently running.
 * @property progressText Latest progress line from `git clone --progress`.
 * @property error Validation or process error shown in the dialog.
 */
public data class CloneRepositoryState(
    val isVisible: Boolean = false,
    val repositoryUrl: String = "",
    val destinationDirectory: String = "",
    val projectName: String = "",
    val isCloning: Boolean = false,
    val progressText: String = "",
    val error: String? = null,
) : State {
    /** Whether the Clone action can start. */
    val canClone: Boolean
        get() =
            repositoryUrl.isNotBlank() && destinationDirectory.isNotBlank() &&
                projectName.isNotBlank() && !isCloning
}

/** User intents for the Clone Repository dialog. */
public sealed interface CloneRepositoryIntent : Intent {
    /** Show the dialog with defaults. */
    public data object Show : CloneRepositoryIntent

    /** Hide the dialog (no-op while a clone is running). */
    public data object Hide : CloneRepositoryIntent

    /** Update the repository URL; re-derives the project name from it. */
    public data class SetRepositoryUrl(
        val url: String,
    ) : CloneRepositoryIntent

    /** Update the destination directory. */
    public data class SetDestinationDirectory(
        val path: String,
    ) : CloneRepositoryIntent

    /** Update the target project folder name. */
    public data class SetProjectName(
        val name: String,
    ) : CloneRepositoryIntent

    /** Start cloning. */
    public data object Clone : CloneRepositoryIntent
}

/** One-time side effects emitted by the Clone Repository dialog. */
public sealed interface CloneRepositoryEffect : Effect {
    /** The repository was cloned into [projectPath]; the host opens it. */
    public data class Cloned(
        val projectPath: String,
    ) : CloneRepositoryEffect
}
