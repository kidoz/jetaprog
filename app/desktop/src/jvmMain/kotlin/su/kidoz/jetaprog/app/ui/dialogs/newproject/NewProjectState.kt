package su.kidoz.jetaprog.app.ui.dialogs.newproject

import su.kidoz.jetaprog.app.project.BuildTool
import su.kidoz.jetaprog.app.project.License
import su.kidoz.jetaprog.app.project.ProjectLanguage
import su.kidoz.jetaprog.app.project.ProjectTemplate
import su.kidoz.jetaprog.app.project.availableBuildTools
import su.kidoz.jetaprog.app.project.getTemplate
import su.kidoz.jetaprog.common.mvi.State

/**
 * State for the New Project wizard dialog.
 */
public data class NewProjectState(
    val isVisible: Boolean = false,
    val currentStep: Int = 0,
    val projectName: String = "",
    val projectLocation: String = System.getProperty("user.home") + "/Projects",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val buildTool: BuildTool = BuildTool.GRADLE,
    val packageName: String = "",
    val initGit: Boolean = true,
    val createReadme: Boolean = true,
    val license: License = License.MIT,
    val sdkVersion: String = "",
    val isCreating: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
) : State {
    /**
     * Available build tools for the selected language.
     */
    val availableBuildTools: List<BuildTool>
        get() = language.availableBuildTools()

    /**
     * Currently selected template based on language and build tool.
     */
    val selectedTemplate: ProjectTemplate
        get() = getTemplate(language, buildTool)

    /**
     * Available SDK versions based on language.
     */
    val availableSdkVersions: List<String>
        get() =
            when (language) {
                ProjectLanguage.KOTLIN -> listOf("2.1.21", "2.0.21", "1.9.25")
                ProjectLanguage.JAVA -> listOf("21", "17", "11", "8")
                ProjectLanguage.RUST -> listOf("1.83", "1.82", "1.81", "1.80")
                ProjectLanguage.CPP -> listOf("C++23", "C++20", "C++17", "C++14")
                ProjectLanguage.VALA -> listOf("0.56", "0.54", "0.52", "0.48")
            }

    /**
     * Total number of wizard steps.
     */
    val totalSteps: Int = 3

    /**
     * Whether current step is valid.
     */
    val isCurrentStepValid: Boolean
        get() =
            when (currentStep) {
                0 -> projectName.isNotBlank() && projectLocation.isNotBlank()
                1 -> packageName.isNotBlank()
                2 -> true
                else -> false
            }

    /**
     * Whether the wizard can create the project.
     */
    val canCreate: Boolean
        get() = isCurrentStepValid && currentStep == totalSteps - 1 && !isCreating
}
