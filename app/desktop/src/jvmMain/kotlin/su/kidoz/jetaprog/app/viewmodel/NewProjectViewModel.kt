package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.project.BuildTool
import su.kidoz.jetaprog.app.project.License
import su.kidoz.jetaprog.app.project.ProjectConfig
import su.kidoz.jetaprog.app.project.ProjectCreator
import su.kidoz.jetaprog.app.project.ProjectLanguage
import su.kidoz.jetaprog.app.project.availableBuildTools
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectEffect
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectIntent
import su.kidoz.jetaprog.app.ui.dialogs.newproject.NewProjectState
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * ViewModel for the New Project wizard dialog.
 */
public class NewProjectViewModel(
    fileSystem: FileSystem,
) : MviViewModel<NewProjectIntent, NewProjectState, NewProjectEffect>(NewProjectState()) {
    private val projectCreator = ProjectCreator(fileSystem)

    override suspend fun handleIntent(intent: NewProjectIntent) {
        when (intent) {
            is NewProjectIntent.Show -> show()
            is NewProjectIntent.Hide -> hide()
            is NewProjectIntent.NextStep -> nextStep()
            is NewProjectIntent.PreviousStep -> previousStep()
            is NewProjectIntent.SetProjectName -> setProjectName(intent.name)
            is NewProjectIntent.SetProjectLocation -> setProjectLocation(intent.path)
            is NewProjectIntent.SetLanguage -> setLanguage(intent.language)
            is NewProjectIntent.SetBuildTool -> setBuildTool(intent.buildTool)
            is NewProjectIntent.SetPackageName -> setPackageName(intent.packageName)
            is NewProjectIntent.SetGitInit -> setGitInit(intent.enabled)
            is NewProjectIntent.SetCreateReadme -> setCreateReadme(intent.enabled)
            is NewProjectIntent.SetLicense -> setLicense(intent.license)
            is NewProjectIntent.SetSdkVersion -> setSdkVersion(intent.version)
            is NewProjectIntent.BrowseLocation -> browseLocation()
            is NewProjectIntent.LocationSelected -> locationSelected(intent.path)
            is NewProjectIntent.Create -> createProject()
        }
    }

    private fun show() {
        updateState {
            NewProjectState(
                isVisible = true,
                projectLocation = System.getProperty("user.home") + "/Projects",
            )
        }
    }

    private fun hide() {
        updateState { copy(isVisible = false) }
    }

    private fun nextStep() {
        if (currentState.isCurrentStepValid && currentState.currentStep < currentState.totalSteps - 1) {
            updateState {
                val newStep = currentStep + 1
                // Auto-generate package name if moving to step 1 and it's empty
                val newPackageName =
                    if (newStep == 1 && packageName.isBlank()) {
                        generatePackageName(projectName)
                    } else {
                        packageName
                    }
                // Auto-select first SDK version if empty
                val newSdkVersion =
                    if (sdkVersion.isBlank()) {
                        availableSdkVersions.firstOrNull() ?: ""
                    } else {
                        sdkVersion
                    }
                copy(
                    currentStep = newStep,
                    packageName = newPackageName,
                    sdkVersion = newSdkVersion,
                )
            }
        }
    }

    private fun previousStep() {
        if (currentState.currentStep > 0) {
            updateState { copy(currentStep = currentStep - 1) }
        }
    }

    private fun setProjectName(name: String) {
        updateState {
            copy(
                projectName = name,
                validationErrors = validationErrors - "projectName",
            )
        }
    }

    private fun setProjectLocation(path: String) {
        updateState {
            copy(
                projectLocation = path,
                validationErrors = validationErrors - "projectLocation",
            )
        }
    }

    private fun setLanguage(language: ProjectLanguage) {
        updateState {
            copy(
                language = language,
                buildTool = language.availableBuildTools().first(), // Reset to first available build tool
                sdkVersion = "", // Reset SDK version when language changes
            )
        }
    }

    private fun setBuildTool(buildTool: BuildTool) {
        updateState { copy(buildTool = buildTool) }
    }

    private fun setPackageName(packageName: String) {
        updateState {
            copy(
                packageName = packageName,
                validationErrors = validationErrors - "packageName",
            )
        }
    }

    private fun setGitInit(enabled: Boolean) {
        updateState { copy(initGit = enabled) }
    }

    private fun setCreateReadme(enabled: Boolean) {
        updateState { copy(createReadme = enabled) }
    }

    private fun setLicense(license: License) {
        updateState { copy(license = license) }
    }

    private fun setSdkVersion(version: String) {
        updateState { copy(sdkVersion = version) }
    }

    private suspend fun browseLocation() {
        emitEffect(NewProjectEffect.OpenDirectoryPicker(currentState.projectLocation))
    }

    private fun locationSelected(path: String) {
        updateState { copy(projectLocation = path) }
    }

    private suspend fun createProject() {
        // Validate
        val errors = validate()
        if (errors.isNotEmpty()) {
            updateState { copy(validationErrors = errors) }
            return
        }

        updateState { copy(isCreating = true) }

        try {
            val config =
                ProjectConfig(
                    name = currentState.projectName,
                    location = currentState.projectLocation,
                    template = currentState.selectedTemplate,
                    packageName = currentState.packageName,
                    initGit = currentState.initGit,
                    createReadme = currentState.createReadme,
                    license = currentState.license,
                    sdkVersion = currentState.sdkVersion,
                )

            val result =
                withContext(Dispatchers.IO) {
                    projectCreator.createProject(config)
                }

            result.fold(
                onSuccess = { path ->
                    updateState { copy(isCreating = false, isVisible = false) }
                    emitEffect(NewProjectEffect.ProjectCreated(path))
                },
                onFailure = { error ->
                    updateState { copy(isCreating = false) }
                    emitEffect(NewProjectEffect.ShowError(error.message ?: "Failed to create project"))
                },
            )
        } catch (e: Exception) {
            updateState { copy(isCreating = false) }
            emitEffect(NewProjectEffect.ShowError(e.message ?: "Failed to create project"))
        }
    }

    private fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (currentState.projectName.isBlank()) {
            errors["projectName"] = "Project name is required"
        } else if (!isValidProjectName(currentState.projectName)) {
            errors["projectName"] = "Invalid project name"
        }

        if (currentState.projectLocation.isBlank()) {
            errors["projectLocation"] = "Project location is required"
        }

        if (currentState.packageName.isBlank()) {
            errors["packageName"] = "Package name is required"
        } else if (!isValidPackageName(currentState.packageName)) {
            errors["packageName"] = "Invalid package name"
        }

        return errors
    }

    private fun isValidProjectName(name: String): Boolean = name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_-]*$"))

    private fun isValidPackageName(name: String): Boolean = name.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$"))

    private fun generatePackageName(projectName: String): String {
        val sanitized = projectName.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "com.example.$sanitized"
    }
}
