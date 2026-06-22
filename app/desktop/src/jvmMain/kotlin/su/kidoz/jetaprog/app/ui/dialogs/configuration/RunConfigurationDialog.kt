package su.kidoz.jetaprog.app.ui.dialogs.configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DotNetConfigurationType
import su.kidoz.jetaprog.configuration.RunConfiguration

/**
 * Dialog for editing run configurations.
 *
 * Similar to IntelliJ IDEA's Run/Debug Configurations dialog.
 */
@Composable
public fun RunConfigurationDialog(
    state: ConfigurationState,
    onSave: (RunConfiguration) -> Unit,
    onDelete: (ConfigurationId) -> Unit,
    onDuplicate: (ConfigurationId) -> Unit,
    onSelect: (ConfigurationId) -> Unit,
    onCreateNew: (ConfigurationType) -> Unit,
    onCreateRecommended: () -> Unit,
    onClose: () -> Unit,
) {
    if (!state.isDialogOpen) return

    var editingConfig by remember(state.editingConfiguration) {
        mutableStateOf(state.editingConfiguration)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .size(800.dp, 600.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IntelliJColors.background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog header
                DialogHeader(onClose = onClose)

                HorizontalDivider(color = IntelliJColors.border)

                // Main content
                Row(modifier = Modifier.weight(1f)) {
                    // Left panel - configuration list
                    ConfigurationListPanel(
                        configurations = state.configurations,
                        selectedId = editingConfig?.id,
                        onSelect = { id ->
                            editingConfig = state.configurations.find { it.id == id }
                            onSelect(id)
                        },
                        onDelete = onDelete,
                        onDuplicate = onDuplicate,
                        onCreateNew = onCreateNew,
                        onCreateRecommended = onCreateRecommended,
                        modifier = Modifier.width(250.dp).fillMaxHeight(),
                    )

                    // Vertical divider
                    Box(
                        modifier =
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(IntelliJColors.border),
                    )

                    // Right panel - configuration editor
                    ConfigurationEditorPanel(
                        configuration = editingConfig,
                        onConfigurationChange = { editingConfig = it },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                HorizontalDivider(color = IntelliJColors.border)

                // Dialog footer
                DialogFooter(
                    onCancel = onClose,
                    onApply = {
                        editingConfig?.let { onSave(it) }
                    },
                    onOk = {
                        editingConfig?.let { onSave(it) }
                        onClose()
                    },
                    canApply = editingConfig != null,
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Run/Debug Configurations",
            color = IntelliJColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = IntelliJColors.textSecondary,
            modifier =
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClose)
                    .padding(2.dp),
        )
    }
}

@Composable
private fun ConfigurationListPanel(
    configurations: List<RunConfiguration>,
    selectedId: ConfigurationId?,
    onSelect: (ConfigurationId) -> Unit,
    onDelete: (ConfigurationId) -> Unit,
    onDuplicate: (ConfigurationId) -> Unit,
    onCreateNew: (ConfigurationType) -> Unit,
    onCreateRecommended: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(IntelliJColors.toolWindowBackground)) {
        var addMenuExpanded by remember { mutableStateOf(false) }

        // Toolbar
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = Spacing.xs.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
            ) {
                ListToolbarButton(
                    icon = Icons.Default.Add,
                    tooltip = "Add",
                    onClick = { addMenuExpanded = true },
                )
                ListToolbarButton(
                    icon = Icons.Default.Delete,
                    tooltip = "Delete",
                    enabled = selectedId != null,
                    onClick = { selectedId?.let { onDelete(it) } },
                )
                ListToolbarButton(
                    icon = Icons.Default.ContentCopy,
                    tooltip = "Duplicate",
                    enabled = selectedId != null,
                    onClick = { selectedId?.let { onDuplicate(it) } },
                )
            }

            DropdownMenu(
                expanded = addMenuExpanded,
                onDismissRequest = { addMenuExpanded = false },
                modifier = Modifier.background(IntelliJColors.surface),
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = IntelliJColors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm.dp))
                            Text(
                                text = "Add Recommended",
                                color = IntelliJColors.textPrimary,
                                fontSize = 12.sp,
                            )
                        }
                    },
                    onClick = {
                        addMenuExpanded = false
                        onCreateRecommended()
                    },
                )

                HorizontalDivider(color = IntelliJColors.border)

                configurationCreationTypes.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = type.toIcon(),
                                    contentDescription = null,
                                    tint = IntelliJColors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm.dp))
                                Text(
                                    text = "Add ${type.displayName}",
                                    color = IntelliJColors.textPrimary,
                                    fontSize = 12.sp,
                                )
                            }
                        },
                        onClick = {
                            addMenuExpanded = false
                            onCreateNew(type)
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = IntelliJColors.border)

        // Configuration list
        LazyColumn(modifier = Modifier.weight(1f)) {
            val byType = configurations.groupBy { it.type }

            byType.forEach { (type, configs) ->
                // Type header
                item {
                    ConfigurationTypeHeader(type = type)
                }

                items(configs) { config ->
                    ConfigurationListItem(
                        configuration = config,
                        isSelected = config.id == selectedId,
                        onClick = { onSelect(config.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListToolbarButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isHovered && enabled) {
                        IntelliJColors.buttonBackgroundHover
                    } else {
                        IntelliJColors.toolWindowBackground
                    },
                ).hoverable(interactionSource)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (enabled) IntelliJColors.textSecondary else IntelliJColors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ConfigurationTypeHeader(type: ConfigurationType) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = type.toIcon(),
            contentDescription = null,
            tint = IntelliJColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.xs.dp))
        Text(
            text = type.displayName,
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConfigurationListItem(
    configuration: RunConfiguration,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isSelected -> IntelliJColors.selectionBackground
                        isHovered -> IntelliJColors.buttonBackgroundHover
                        else -> IntelliJColors.toolWindowBackground
                    },
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(start = Spacing.lg.dp, end = Spacing.sm.dp, top = Spacing.xs.dp, bottom = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = configuration.name,
            color =
                if (configuration.isTemporary) {
                    IntelliJColors.textSecondary
                } else {
                    IntelliJColors.textPrimary
                },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConfigurationEditorPanel(
    configuration: RunConfiguration?,
    onConfigurationChange: (RunConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (configuration == null) {
        Box(
            modifier = modifier.background(IntelliJColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select a configuration to edit",
                color = IntelliJColors.textMuted,
                fontSize = 13.sp,
            )
        }
        return
    }

    Column(
        modifier =
            modifier
                .background(IntelliJColors.background)
                .padding(Spacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.dp),
    ) {
        // Name field
        IntelliJTextField(
            value = configuration.name,
            onValueChange = { onConfigurationChange(configuration.copy(name = it)) },
            label = "Name:",
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = IntelliJColors.border)

        // Type-specific settings
        when (val settings = configuration.settings) {
            is ConfigurationSettings.Gradle -> {
                GradleSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.MesonBuild -> {
                MesonBuildSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.MesonRun -> {
                MesonRunSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.Application -> {
                ApplicationSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.ShellScript -> {
                ShellScriptSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.Compound -> {
                CompoundSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.Python -> {
                PythonSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.Poetry -> {
                PoetrySettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.Uv -> {
                UvSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.CargoBuild,
            is ConfigurationSettings.CargoRun,
            is ConfigurationSettings.CargoTest,
            is ConfigurationSettings.CargoClippy,
            -> {
                // TODO: Implement Cargo settings editors
                Text(
                    text = "Cargo settings editor coming soon",
                    color = IntelliJColors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            is ConfigurationSettings.DotNetBuild -> {
                DotNetBuildSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.DotNetRun -> {
                DotNetRunSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is ConfigurationSettings.DotNetTest -> {
                DotNetTestSettingsEditor(
                    settings = settings,
                    onSettingsChange = { onConfigurationChange(configuration.copy(settings = it)) },
                )
            }

            is su.kidoz.jetaprog.configuration.TomcatLocalSettings,
            is su.kidoz.jetaprog.configuration.TomcatRemoteSettings,
            -> {
                Text(
                    text = "Tomcat settings editor coming soon",
                    color = IntelliJColors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            is su.kidoz.jetaprog.configuration.SpringBootSettings,
            is su.kidoz.jetaprog.configuration.SpringBootDevServerSettings,
            -> {
                Text(
                    text = "Spring Boot settings editor coming soon",
                    color = IntelliJColors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            is su.kidoz.jetaprog.configuration.DockerBuildSettings,
            is su.kidoz.jetaprog.configuration.DockerRunSettings,
            is su.kidoz.jetaprog.configuration.DockerComposeSettings,
            -> {
                Text(
                    text = "Docker settings editor coming soon",
                    color = IntelliJColors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = IntelliJColors.border)

        // Common options
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = configuration.storeAsProjectFile,
                onCheckedChange = { onConfigurationChange(configuration.copy(storeAsProjectFile = it)) },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = IntelliJColors.accent,
                        uncheckedColor = IntelliJColors.textSecondary,
                    ),
            )
            Spacer(modifier = Modifier.width(Spacing.xs.dp))
            Text(
                text = "Store as project file",
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.width(Spacing.lg.dp))

            Checkbox(
                checked = configuration.allowParallelRun,
                onCheckedChange = { onConfigurationChange(configuration.copy(allowParallelRun = it)) },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = IntelliJColors.accent,
                        uncheckedColor = IntelliJColors.textSecondary,
                    ),
            )
            Spacer(modifier = Modifier.width(Spacing.xs.dp))
            Text(
                text = "Allow parallel run",
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GradleSettingsEditor(
    settings: ConfigurationSettings.Gradle,
    onSettingsChange: (ConfigurationSettings.Gradle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.taskPath,
            onValueChange = { onSettingsChange(settings.copy(taskPath = it)) },
            label = "Gradle task:",
            placeholder = "e.g., :app:desktop:run",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.arguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        arguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments:",
            placeholder = "--info --stacktrace",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.jvmArguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        jvmArguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "JVM Arguments:",
            placeholder = "-Xmx2g -Dsome.property=value",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MesonBuildSettingsEditor(
    settings: ConfigurationSettings.MesonBuild,
    onSettingsChange: (ConfigurationSettings.MesonBuild) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.buildDirectory,
            onValueChange = { onSettingsChange(settings.copy(buildDirectory = it)) },
            label = "Build directory:",
            placeholder = "builddir",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.target ?: "",
            onValueChange = { onSettingsChange(settings.copy(target = it.ifBlank { null })) },
            label = "Target:",
            placeholder = "All targets if empty",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.arguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        arguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments:",
            placeholder = "Additional meson compile arguments",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MesonRunSettingsEditor(
    settings: ConfigurationSettings.MesonRun,
    onSettingsChange: (ConfigurationSettings.MesonRun) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.buildDirectory,
            onValueChange = { onSettingsChange(settings.copy(buildDirectory = it)) },
            label = "Build directory:",
            placeholder = "builddir",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.executable,
            onValueChange = { onSettingsChange(settings.copy(executable = it)) },
            label = "Executable:",
            placeholder = "Target executable name",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.programArguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        programArguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "Program arguments:",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ApplicationSettingsEditor(
    settings: ConfigurationSettings.Application,
    onSettingsChange: (ConfigurationSettings.Application) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.executablePath,
            onValueChange = { onSettingsChange(settings.copy(executablePath = it)) },
            label = "Executable path:",
            placeholder = "/path/to/executable",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.programArguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        programArguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "Program arguments:",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShellScriptSettingsEditor(
    settings: ConfigurationSettings.ShellScript,
    onSettingsChange: (ConfigurationSettings.ShellScript) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.script,
            onValueChange = { onSettingsChange(settings.copy(script = it)) },
            label = if (settings.isFile) "Script path:" else "Script content:",
            placeholder = if (settings.isFile) "/path/to/script.sh" else "echo 'Hello World'",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.interpreter ?: "",
            onValueChange = { onSettingsChange(settings.copy(interpreter = it.ifBlank { null })) },
            label = "Interpreter:",
            placeholder = "/bin/sh (default)",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.arguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        arguments =
                            it.split(" ").filter { s ->
                                s.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments:",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompoundSettingsEditor(
    settings: ConfigurationSettings.Compound,
    onSettingsChange: (ConfigurationSettings.Compound) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        Text(
            text = "Compound configurations run multiple configurations.",
            color = IntelliJColors.textSecondary,
            fontSize = 12.sp,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.parallel,
                onCheckedChange = { onSettingsChange(settings.copy(parallel = it)) },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = IntelliJColors.accent,
                        uncheckedColor = IntelliJColors.textSecondary,
                    ),
            )
            Spacer(modifier = Modifier.width(Spacing.xs.dp))
            Text(
                text = "Run in parallel",
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
            )
        }

        Text(
            text = "Configurations: ${settings.configurationIds.size}",
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PythonSettingsEditor(
    settings: ConfigurationSettings.Python,
    onSettingsChange: (ConfigurationSettings.Python) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        IntelliJTextField(
            value = settings.scriptPath,
            onValueChange = { onSettingsChange(settings.copy(scriptPath = it)) },
            label = "Script path:",
            placeholder = "path/to/script.py",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.pythonInterpreter,
            onValueChange = { onSettingsChange(settings.copy(pythonInterpreter = it)) },
            label = "Python interpreter:",
            placeholder = "python3",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.scriptArguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        scriptArguments =
                            it.split(" ").filter { arg ->
                                arg.isNotBlank()
                            },
                    ),
                )
            },
            label = "Script arguments:",
            placeholder = "--arg1 value1 --arg2 value2",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.module ?: "",
            onValueChange = { onSettingsChange(settings.copy(module = it.ifBlank { null })) },
            label = "Module (-m):",
            placeholder = "Optional: module name (e.g., pytest)",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PoetrySettingsEditor(
    settings: ConfigurationSettings.Poetry,
    onSettingsChange: (ConfigurationSettings.Poetry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        Text(
            text = "Command: ${settings.command.displayName}",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )

        IntelliJTextField(
            value = settings.arguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        arguments =
                            it.split(" ").filter { arg ->
                                arg.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments:",
            placeholder = "Additional arguments",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UvSettingsEditor(
    settings: ConfigurationSettings.Uv,
    onSettingsChange: (ConfigurationSettings.Uv) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
        Text(
            text = "Command: ${settings.command.displayName}",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )

        IntelliJTextField(
            value = settings.arguments.joinToString(" "),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        arguments =
                            it.split(" ").filter { arg ->
                                arg.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments:",
            placeholder = "Additional arguments",
            modifier = Modifier.fillMaxWidth(),
        )

        IntelliJTextField(
            value = settings.workingDirectory ?: "",
            onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working directory:",
            placeholder = "Project root by default",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DotNetBuildSettingsEditor(
    settings: ConfigurationSettings.DotNetBuild,
    onSettingsChange: (ConfigurationSettings.DotNetBuild) -> Unit,
) {
    DotNetConfigurationSelector(
        configuration = settings.configuration,
        onConfigurationChange = { onSettingsChange(settings.copy(configuration = it)) },
    )

    IntelliJTextField(
        value = settings.targetPath ?: "",
        onValueChange = { onSettingsChange(settings.copy(targetPath = it.ifBlank { null })) },
        label = "Solution or project:",
        placeholder = "Workspace root if empty",
        modifier = Modifier.fillMaxWidth(),
    )

    IntelliJTextField(
        value = settings.arguments.joinToString(" "),
        onValueChange = { onSettingsChange(settings.copy(arguments = parseArguments(it))) },
        label = "Arguments:",
        placeholder = "--no-incremental",
        modifier = Modifier.fillMaxWidth(),
    )

    DotNetCommonOptions(
        noRestore = settings.noRestore,
        onNoRestoreChange = { onSettingsChange(settings.copy(noRestore = it)) },
        workingDirectory = settings.workingDirectory,
        onWorkingDirectoryChange = { onSettingsChange(settings.copy(workingDirectory = it)) },
    )
}

@Composable
private fun DotNetRunSettingsEditor(
    settings: ConfigurationSettings.DotNetRun,
    onSettingsChange: (ConfigurationSettings.DotNetRun) -> Unit,
) {
    DotNetConfigurationSelector(
        configuration = settings.configuration,
        onConfigurationChange = { onSettingsChange(settings.copy(configuration = it)) },
    )

    IntelliJTextField(
        value = settings.projectPath ?: "",
        onValueChange = { onSettingsChange(settings.copy(projectPath = it.ifBlank { null })) },
        label = "Project:",
        placeholder = "Workspace root if empty",
        modifier = Modifier.fillMaxWidth(),
    )

    IntelliJTextField(
        value = settings.programArguments.joinToString(" "),
        onValueChange = { onSettingsChange(settings.copy(programArguments = parseArguments(it))) },
        label = "Program arguments:",
        modifier = Modifier.fillMaxWidth(),
    )

    DotNetCommonOptions(
        noRestore = settings.noRestore,
        onNoRestoreChange = { onSettingsChange(settings.copy(noRestore = it)) },
        workingDirectory = settings.workingDirectory,
        onWorkingDirectoryChange = { onSettingsChange(settings.copy(workingDirectory = it)) },
    )
}

@Composable
private fun DotNetTestSettingsEditor(
    settings: ConfigurationSettings.DotNetTest,
    onSettingsChange: (ConfigurationSettings.DotNetTest) -> Unit,
) {
    DotNetConfigurationSelector(
        configuration = settings.configuration,
        onConfigurationChange = { onSettingsChange(settings.copy(configuration = it)) },
    )

    IntelliJTextField(
        value = settings.targetPath ?: "",
        onValueChange = { onSettingsChange(settings.copy(targetPath = it.ifBlank { null })) },
        label = "Solution or project:",
        placeholder = "Workspace root if empty",
        modifier = Modifier.fillMaxWidth(),
    )

    IntelliJTextField(
        value = settings.filter ?: "",
        onValueChange = { onSettingsChange(settings.copy(filter = it.ifBlank { null })) },
        label = "Test filter:",
        placeholder = "FullyQualifiedName~Namespace",
        modifier = Modifier.fillMaxWidth(),
    )

    IntelliJTextField(
        value = settings.arguments.joinToString(" "),
        onValueChange = { onSettingsChange(settings.copy(arguments = parseArguments(it))) },
        label = "Arguments:",
        placeholder = "--logger trx",
        modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = settings.noBuild,
            onCheckedChange = { onSettingsChange(settings.copy(noBuild = it)) },
            colors =
                CheckboxDefaults.colors(
                    checkedColor = IntelliJColors.accent,
                    uncheckedColor = IntelliJColors.textSecondary,
                ),
        )
        Spacer(modifier = Modifier.width(Spacing.xs.dp))
        Text(
            text = "Skip build",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )
    }

    IntelliJTextField(
        value = settings.workingDirectory ?: "",
        onValueChange = { onSettingsChange(settings.copy(workingDirectory = it.ifBlank { null })) },
        label = "Working directory:",
        placeholder = "Project root by default",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DotNetConfigurationSelector(
    configuration: DotNetConfigurationType,
    onConfigurationChange: (DotNetConfigurationType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IntelliJButton(
            text = "Configuration: ${configuration.displayName}",
            onClick = { expanded = true },
            modifier = Modifier.width(180.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DotNetConfigurationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onConfigurationChange(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DotNetCommonOptions(
    noRestore: Boolean,
    onNoRestoreChange: (Boolean) -> Unit,
    workingDirectory: String?,
    onWorkingDirectoryChange: (String?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = noRestore,
            onCheckedChange = onNoRestoreChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = IntelliJColors.accent,
                    uncheckedColor = IntelliJColors.textSecondary,
                ),
        )
        Spacer(modifier = Modifier.width(Spacing.xs.dp))
        Text(
            text = "Skip restore",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
        )
    }

    IntelliJTextField(
        value = workingDirectory ?: "",
        onValueChange = { onWorkingDirectoryChange(it.ifBlank { null }) },
        label = "Working directory:",
        placeholder = "Project root by default",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DialogFooter(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onOk: () -> Unit,
    canApply: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(IntelliJColors.toolWindowBackground)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        IntelliJButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.width(80.dp),
        )

        Spacer(modifier = Modifier.width(Spacing.sm.dp))

        IntelliJButton(
            text = "Apply",
            onClick = onApply,
            enabled = canApply,
            modifier = Modifier.width(80.dp),
        )

        Spacer(modifier = Modifier.width(Spacing.sm.dp))

        IntelliJButton(
            text = "OK",
            onClick = onOk,
            enabled = canApply,
            style = ButtonStyle.PRIMARY,
            modifier = Modifier.width(80.dp),
        )
    }
}

private fun ConfigurationType.toIcon(): ImageVector =
    when (this) {
        ConfigurationType.GRADLE -> Icons.Default.Build
        ConfigurationType.MESON_BUILD -> Icons.Default.Build
        ConfigurationType.MESON_RUN -> Icons.Default.PlayArrow
        ConfigurationType.PYTHON -> Icons.Default.PlayArrow
        ConfigurationType.POETRY -> Icons.Default.Build
        ConfigurationType.UV -> Icons.Default.Build
        ConfigurationType.CARGO_BUILD -> Icons.Filled.Build
        ConfigurationType.CARGO_RUN -> Icons.Filled.PlayArrow
        ConfigurationType.CARGO_TEST -> Icons.Filled.PlayArrow
        ConfigurationType.CARGO_CLIPPY -> Icons.Filled.Build
        ConfigurationType.DOTNET_BUILD -> Icons.Default.Build
        ConfigurationType.DOTNET_RUN -> Icons.Default.PlayArrow
        ConfigurationType.DOTNET_TEST -> Icons.Default.PlayArrow
        ConfigurationType.APPLICATION -> Icons.Default.PlayArrow
        ConfigurationType.SHELL_SCRIPT -> Icons.Default.Terminal
        ConfigurationType.COMPOUND -> Icons.Default.PlayArrow
        ConfigurationType.TOMCAT_LOCAL -> Icons.Default.Build
        ConfigurationType.TOMCAT_REMOTE -> Icons.Default.Build
        ConfigurationType.SPRING_BOOT -> Icons.Default.PlayArrow
        ConfigurationType.DOCKER_BUILD -> Icons.Default.Build
        ConfigurationType.DOCKER_RUN -> Icons.Default.PlayArrow
        ConfigurationType.DOCKER_COMPOSE -> Icons.Default.PlayArrow
    }

private val configurationCreationTypes =
    listOf(
        ConfigurationType.APPLICATION,
        ConfigurationType.PYTHON,
        ConfigurationType.CARGO_RUN,
        ConfigurationType.CARGO_BUILD,
        ConfigurationType.CARGO_TEST,
        ConfigurationType.CARGO_CLIPPY,
        ConfigurationType.DOTNET_RUN,
        ConfigurationType.DOTNET_BUILD,
        ConfigurationType.DOTNET_TEST,
        ConfigurationType.GRADLE,
        ConfigurationType.MESON_BUILD,
        ConfigurationType.MESON_RUN,
        ConfigurationType.POETRY,
        ConfigurationType.UV,
        ConfigurationType.SHELL_SCRIPT,
        ConfigurationType.COMPOUND,
    )

private fun parseArguments(value: String): List<String> =
    value
        .split(" ")
        .filter { it.isNotBlank() }
