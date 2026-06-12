package su.kidoz.jetaprog.app.ui.toolbar

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.RunConfiguration

/**
 * Run configuration selector dropdown with run/stop buttons.
 *
 * Styled to match IntelliJ IDEA's run configuration widget in the toolbar.
 */
@Composable
public fun RunConfigurationSelector(
    state: ConfigurationState,
    onSelect: (ConfigurationId) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onEditConfigurations: () -> Unit,
    onCreateRecommended: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        // Configuration dropdown
        ConfigurationDropdown(
            activeConfiguration = state.activeConfiguration,
            configurations = state.configurations,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onSelect = { id ->
                onSelect(id)
                expanded = false
            },
            onEditConfigurations = {
                onEditConfigurations()
                expanded = false
            },
            onCreateRecommended = {
                onCreateRecommended()
                expanded = false
            },
        )

        // Run button
        ToolbarButton(
            icon = Icons.Default.PlayArrow,
            contentDescription = "Run",
            enabled = state.activeConfiguration != null && !state.isRunning,
            onClick = onRun,
            tint = IntelliJColors.success,
        )

        // Stop button
        ToolbarButton(
            icon = Icons.Default.Stop,
            contentDescription = "Stop",
            enabled = state.isRunning,
            onClick = onStop,
            tint = IntelliJColors.error,
        )
    }
}

@Composable
private fun ConfigurationDropdown(
    activeConfiguration: RunConfiguration?,
    configurations: List<RunConfiguration>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (ConfigurationId) -> Unit,
    onEditConfigurations: () -> Unit,
    onCreateRecommended: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        // Dropdown trigger — transparent background, highlight on hover only
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isHovered || expanded) {
                            IntelliJColors.buttonBackgroundHover
                        } else {
                            Color.Transparent
                        },
                    ).hoverable(interactionSource)
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp)
                    .widthIn(min = 150.dp, max = 250.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Configuration icon
            Icon(
                imageVector = activeConfiguration?.type?.toIcon() ?: Icons.Default.Settings,
                contentDescription = null,
                tint = if (activeConfiguration != null) IntelliJColors.textSecondary else IntelliJColors.textMuted,
                modifier = Modifier.size(16.dp),
            )

            Spacer(modifier = Modifier.width(Spacing.xs.dp))

            // Configuration name
            Text(
                text = activeConfiguration?.name ?: "Add Configuration...",
                color =
                    if (activeConfiguration != null) {
                        IntelliJColors.textPrimary
                    } else {
                        IntelliJColors.textMuted
                    },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Dropdown arrow
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = IntelliJColors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        // Dropdown menu — fixed width, flat list
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier =
                Modifier
                    .background(IntelliJColors.surface)
                    .width(250.dp),
        ) {
            // Split into permanent and temporary configs
            val permanent = configurations.filter { !it.isTemporary }
            val temporary = configurations.filter { it.isTemporary }

            // Permanent configurations — flat list with type icon
            permanent.forEach { config ->
                ConfigurationMenuItem(
                    configuration = config,
                    isSelected = config.id == activeConfiguration?.id,
                    onClick = { onSelect(config.id) },
                )
            }

            // Divider between permanent and temporary
            if (permanent.isNotEmpty() && temporary.isNotEmpty()) {
                HorizontalDivider(color = IntelliJColors.border)
            }

            // Temporary configurations
            temporary.forEach { config ->
                ConfigurationMenuItem(
                    configuration = config,
                    isSelected = config.id == activeConfiguration?.id,
                    onClick = { onSelect(config.id) },
                )
            }

            if (configurations.isNotEmpty()) {
                HorizontalDivider(color = IntelliJColors.border)
            }

            // "Add Recommended" — subtle link style
            ActionMenuItem(
                icon = Icons.Default.Add,
                text = "Add Recommended...",
                textColor = IntelliJColors.textLink,
                onClick = onCreateRecommended,
            )

            HorizontalDivider(color = IntelliJColors.border)

            // "Edit Configurations..." — bottom action
            ActionMenuItem(
                icon = Icons.Default.Settings,
                text = "Edit Configurations...",
                textColor = IntelliJColors.textPrimary,
                onClick = onEditConfigurations,
            )
        }
    }
}

@Composable
private fun ConfigurationMenuItem(
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
                        else -> Color.Transparent
                    },
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = configuration.type.toIcon(),
            contentDescription = null,
            tint = if (isSelected) IntelliJColors.accent else IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )

        Spacer(modifier = Modifier.width(Spacing.sm.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = configuration.name,
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
                fontStyle = if (configuration.isTemporary) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Subtitle — show detail based on configuration type
            val subtitle = configuration.subtitle()
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = IntelliJColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Temporary indicator badge
        if (configuration.isTemporary) {
            Text(
                text = "temp",
                color = IntelliJColors.textMuted,
                fontSize = 9.sp,
            )
        }
    }
}

/**
 * Bottom action menu item (Edit Configurations, Add Recommended).
 */
@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (isHovered) IntelliJColors.buttonBackgroundHover else Color.Transparent,
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = IntelliJColors.textPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isHovered && enabled) {
                        IntelliJColors.buttonBackgroundHover
                    } else {
                        Color.Transparent
                    },
                ).hoverable(interactionSource)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else IntelliJColors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Extract a subtitle string from the configuration settings for display.
 */
private fun RunConfiguration.subtitle(): String? =
    when (val s = settings) {
        is ConfigurationSettings.Gradle -> s.taskPath
        is ConfigurationSettings.MesonBuild -> s.target ?: s.buildDirectory
        is ConfigurationSettings.MesonRun -> s.executable
        is ConfigurationSettings.Python -> s.module ?: s.scriptPath.substringAfterLast('/')
        is ConfigurationSettings.Poetry -> "poetry ${s.command.value}"
        is ConfigurationSettings.Uv -> "uv ${s.command.value}"
        is ConfigurationSettings.CargoBuild -> s.package_ ?: s.profile.displayName
        is ConfigurationSettings.CargoRun -> s.bin ?: s.profile.displayName
        is ConfigurationSettings.CargoTest -> s.testName ?: "all tests"
        is ConfigurationSettings.CargoClippy -> if (s.fix) "fix" else null
        is ConfigurationSettings.Application -> s.executablePath.substringAfterLast('/')
        is ConfigurationSettings.ShellScript -> s.script.substringAfterLast('/').take(SUBTITLE_MAX_LENGTH)
        is ConfigurationSettings.Compound -> "${s.configurationIds.size} configuration(s)"
        else -> null
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

private const val SUBTITLE_MAX_LENGTH = 40
