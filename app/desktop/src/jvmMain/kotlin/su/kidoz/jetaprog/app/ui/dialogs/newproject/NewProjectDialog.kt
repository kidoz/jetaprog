package su.kidoz.jetaprog.app.ui.dialogs.newproject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.project.BuildTool
import su.kidoz.jetaprog.app.project.License
import su.kidoz.jetaprog.app.project.ProjectLanguage
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.HorizontalSplitter
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJDropdown
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.viewmodel.NewProjectViewModel

/**
 * New Project wizard dialog.
 */
@Composable
public fun NewProjectDialog(
    viewModel: NewProjectViewModel,
    onBrowseLocation: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    DialogOverlay(
        isVisible = state.isVisible,
        onDismiss = { viewModel.dispatch(NewProjectIntent.Hide) },
    ) {
        DialogContainer(
            modifier = Modifier.width(600.dp),
        ) {
            // Title
            Text(
                text = "New Project",
                color = IntelliJColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step indicator
            StepIndicator(
                currentStep = state.currentStep,
                totalSteps = state.totalSteps,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalSplitter()
            Spacer(modifier = Modifier.height(16.dp))

            // Step content
            when (state.currentStep) {
                0 -> {
                    Step1Content(
                        state = state,
                        onProjectNameChange = { viewModel.dispatch(NewProjectIntent.SetProjectName(it)) },
                        onProjectLocationChange = { viewModel.dispatch(NewProjectIntent.SetProjectLocation(it)) },
                        onLanguageChange = { viewModel.dispatch(NewProjectIntent.SetLanguage(it)) },
                        onBuildToolChange = { viewModel.dispatch(NewProjectIntent.SetBuildTool(it)) },
                        onBrowseLocation = onBrowseLocation,
                    )
                }

                1 -> {
                    Step2Content(
                        state = state,
                        onPackageNameChange = { viewModel.dispatch(NewProjectIntent.SetPackageName(it)) },
                    )
                }

                2 -> {
                    Step3Content(
                        state = state,
                        onGitInitChange = { viewModel.dispatch(NewProjectIntent.SetGitInit(it)) },
                        onCreateReadmeChange = { viewModel.dispatch(NewProjectIntent.SetCreateReadme(it)) },
                        onLicenseChange = { viewModel.dispatch(NewProjectIntent.SetLicense(it)) },
                        onSdkVersionChange = { viewModel.dispatch(NewProjectIntent.SetSdkVersion(it)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalSplitter()
            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IntelliJButton(
                    text = "Cancel",
                    onClick = { viewModel.dispatch(NewProjectIntent.Hide) },
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (state.currentStep > 0) {
                    IntelliJButton(
                        text = "Back",
                        onClick = { viewModel.dispatch(NewProjectIntent.PreviousStep) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (state.currentStep < state.totalSteps - 1) {
                    IntelliJButton(
                        text = "Next",
                        onClick = { viewModel.dispatch(NewProjectIntent.NextStep) },
                        style = ButtonStyle.PRIMARY,
                        enabled = state.isCurrentStepValid,
                    )
                } else {
                    IntelliJButton(
                        text = if (state.isCreating) "Creating..." else "Create",
                        onClick = { viewModel.dispatch(NewProjectIntent.Create) },
                        style = ButtonStyle.PRIMARY,
                        enabled = state.canCreate,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
) {
    val stepLabels = listOf("Project", "Package", "Options")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stepLabels.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isActive -> IntelliJColors.accent
                                    isCompleted -> IntelliJColors.success
                                    else -> IntelliJColors.buttonBackground
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = IntelliJColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = label,
                    color =
                        if (isActive) {
                            IntelliJColors.textPrimary
                        } else {
                            IntelliJColors.textSecondary
                        },
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun Step1Content(
    state: NewProjectState,
    onProjectNameChange: (String) -> Unit,
    onProjectLocationChange: (String) -> Unit,
    onLanguageChange: (ProjectLanguage) -> Unit,
    onBuildToolChange: (BuildTool) -> Unit,
    onBrowseLocation: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IntelliJTextField(
            value = state.projectName,
            onValueChange = onProjectNameChange,
            label = "Project Name",
            placeholder = "my-project",
            error = state.validationErrors["projectName"],
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            IntelliJTextField(
                value = state.projectLocation,
                onValueChange = onProjectLocationChange,
                label = "Location",
                placeholder = "/path/to/projects",
                error = state.validationErrors["projectLocation"],
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier =
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(IntelliJColors.buttonBackground)
                        .clickable(onClick = onBrowseLocation)
                        .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Browse",
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntelliJDropdown(
                selectedItem = state.language,
                items = ProjectLanguage.entries.toList(),
                onItemSelected = onLanguageChange,
                label = "Language",
                itemToString = { it.displayName },
                modifier = Modifier.weight(1f),
            )

            IntelliJDropdown(
                selectedItem = state.buildTool,
                items = state.availableBuildTools,
                onItemSelected = onBuildToolChange,
                label = "Build Tool",
                itemToString = { it.displayName },
                modifier = Modifier.weight(1f),
            )
        }

        // Show template info
        Text(
            text = "Template: ${state.selectedTemplate.name}",
            color = IntelliJColors.textSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = state.selectedTemplate.description,
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Step2Content(
    state: NewProjectState,
    onPackageNameChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val packageLabel =
            when (state.language) {
                ProjectLanguage.KOTLIN, ProjectLanguage.JAVA -> "Package Name"
                ProjectLanguage.RUST -> "Crate Name"
                ProjectLanguage.CPP -> "Project Name"
                ProjectLanguage.VALA -> "Namespace"
            }

        val packagePlaceholder =
            when (state.language) {
                ProjectLanguage.KOTLIN, ProjectLanguage.JAVA -> "com.example.myproject"
                ProjectLanguage.RUST -> "my_crate"
                ProjectLanguage.CPP -> "myproject"
                ProjectLanguage.VALA -> "MyApp"
            }

        IntelliJTextField(
            value = state.packageName,
            onValueChange = onPackageNameChange,
            label = packageLabel,
            placeholder = packagePlaceholder,
            error = state.validationErrors["packageName"],
        )

        // Show full project path preview
        Text(
            text = "Project will be created at:",
            color = IntelliJColors.textSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = "${state.projectLocation}/${state.projectName}",
            color = IntelliJColors.textLink,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Step3Content(
    state: NewProjectState,
    onGitInitChange: (Boolean) -> Unit,
    onCreateReadmeChange: (Boolean) -> Unit,
    onLicenseChange: (License) -> Unit,
    onSdkVersionChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sdkLabel =
            when (state.language) {
                ProjectLanguage.KOTLIN -> "Kotlin Version"
                ProjectLanguage.JAVA -> "Java Version"
                ProjectLanguage.RUST -> "Rust Version"
                ProjectLanguage.CPP -> "C++ Standard"
                ProjectLanguage.VALA -> "Vala Version"
            }

        IntelliJDropdown(
            selectedItem = state.sdkVersion.ifEmpty { state.availableSdkVersions.firstOrNull() ?: "" },
            items = state.availableSdkVersions,
            onItemSelected = onSdkVersionChange,
            label = sdkLabel,
        )

        IntelliJDropdown(
            selectedItem = state.license,
            items = License.entries.toList(),
            onItemSelected = onLicenseChange,
            label = "License",
            itemToString = { it.displayName },
        )

        Spacer(modifier = Modifier.height(4.dp))

        IntelliJCheckbox(
            checked = state.initGit,
            onCheckedChange = onGitInitChange,
            label = "Initialize Git repository",
        )

        IntelliJCheckbox(
            checked = state.createReadme,
            onCheckedChange = onCreateReadmeChange,
            label = "Create README.md",
        )
    }
}
