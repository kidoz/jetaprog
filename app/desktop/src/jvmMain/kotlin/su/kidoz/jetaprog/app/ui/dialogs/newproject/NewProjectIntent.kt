package su.kidoz.jetaprog.app.ui.dialogs.newproject

import su.kidoz.jetaprog.app.project.BuildTool
import su.kidoz.jetaprog.app.project.License
import su.kidoz.jetaprog.app.project.ProjectLanguage
import su.kidoz.jetaprog.common.mvi.Intent

/**
 * Intents for the New Project wizard.
 */
public sealed interface NewProjectIntent : Intent {
    /**
     * Show the new project dialog.
     */
    public data object Show : NewProjectIntent

    /**
     * Hide the new project dialog.
     */
    public data object Hide : NewProjectIntent

    /**
     * Go to the next wizard step.
     */
    public data object NextStep : NewProjectIntent

    /**
     * Go to the previous wizard step.
     */
    public data object PreviousStep : NewProjectIntent

    /**
     * Set the project name.
     */
    public data class SetProjectName(
        val name: String,
    ) : NewProjectIntent

    /**
     * Set the project location.
     */
    public data class SetProjectLocation(
        val path: String,
    ) : NewProjectIntent

    /**
     * Set the project language.
     */
    public data class SetLanguage(
        val language: ProjectLanguage,
    ) : NewProjectIntent

    /**
     * Set the build tool.
     */
    public data class SetBuildTool(
        val buildTool: BuildTool,
    ) : NewProjectIntent

    /**
     * Set the package/namespace name.
     */
    public data class SetPackageName(
        val packageName: String,
    ) : NewProjectIntent

    /**
     * Set whether to initialize a Git repository.
     */
    public data class SetGitInit(
        val enabled: Boolean,
    ) : NewProjectIntent

    /**
     * Set whether to create a README file.
     */
    public data class SetCreateReadme(
        val enabled: Boolean,
    ) : NewProjectIntent

    /**
     * Set the license type.
     */
    public data class SetLicense(
        val license: License,
    ) : NewProjectIntent

    /**
     * Set the SDK/language version.
     */
    public data class SetSdkVersion(
        val version: String,
    ) : NewProjectIntent

    /**
     * Open directory picker for project location.
     */
    public data object BrowseLocation : NewProjectIntent

    /**
     * Handle selected directory from picker.
     */
    public data class LocationSelected(
        val path: String,
    ) : NewProjectIntent

    /**
     * Create the project.
     */
    public data object Create : NewProjectIntent
}
