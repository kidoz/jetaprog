package su.kidoz.jetaprog.app.ui.dialogs.newproject

import su.kidoz.jetaprog.common.mvi.Effect

/**
 * Side effects for the New Project wizard.
 */
public sealed interface NewProjectEffect : Effect {
    /**
     * Project was created successfully.
     */
    public data class ProjectCreated(
        val path: String,
    ) : NewProjectEffect

    /**
     * An error occurred during project creation.
     */
    public data class ShowError(
        val message: String,
    ) : NewProjectEffect

    /**
     * Request to open directory picker.
     */
    public data class OpenDirectoryPicker(
        val currentPath: String,
    ) : NewProjectEffect
}
