package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.settings.EditingMcpServer
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingRow
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingSection
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.model.McpServerConfig
import su.kidoz.jetaprog.settings.model.ToolsSettings

/**
 * Panel for tools settings (build systems, MCP servers, external tools).
 */
@Composable
public fun ToolsPanel(
    settings: ToolsSettings,
    editingServer: EditingMcpServer?,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Build Systems section
        SettingSection(title = "BUILD SYSTEMS") {
            SettingRow(
                label = "Auto-detect Build Systems",
                description = "Automatically detect build systems in the project",
            ) {
                IntelliJCheckbox(
                    checked = settings.buildSystems.autoDetect,
                    onCheckedChange = { onIntent(SettingsIntent.SetBuildSystemAutoDetect(it)) },
                )
            }

            Text(
                text = "Gradle",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = Spacing.md.dp, bottom = Spacing.xs.dp),
            )
            Text(
                text = "Use Wrapper: ${if (settings.buildSystems.gradle.useWrapper) "Yes" else "No"}",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
            )
            Text(
                text = "Parallel Builds: ${if (settings.buildSystems.gradle.parallelBuilds) "Yes" else "No"}",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
            )

            Text(
                text = "Meson",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = Spacing.md.dp, bottom = Spacing.xs.dp),
            )
            Text(
                text = "Build Type: ${settings.buildSystems.meson.buildType.value}",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
            )
            Text(
                text = "Backend: ${settings.buildSystems.meson.backend.value}",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
            )
        }

        // MCP Servers section
        SettingSection(title = "MCP SERVERS") {
            Text(
                text = "Configure Model Context Protocol (MCP) servers for AI integration.",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = Spacing.md.dp),
            )

            // Server list
            settings.mcpServers.forEach { (id, server) ->
                McpServerRow(
                    id = id,
                    server = server,
                    onEdit = { onIntent(SettingsIntent.EditMcpServer(id)) },
                    onRemove = { onIntent(SettingsIntent.RemoveMcpServer(id)) },
                    onToggle = { enabled -> onIntent(SettingsIntent.ToggleMcpServer(id, enabled)) },
                )
            }

            // Add button
            IntelliJButton(
                text = "Add MCP Server",
                onClick = { onIntent(SettingsIntent.AddMcpServer) },
                style = ButtonStyle.SECONDARY,
                icon = Icons.Default.Add,
                modifier = Modifier.padding(top = Spacing.sm.dp),
            )
        }

        // MCP Server edit dialog
        if (editingServer != null) {
            McpServerEditPanel(
                editingServer = editingServer,
                onUpdate = { onIntent(SettingsIntent.UpdateEditingMcpServer(it)) },
                onSave = { onIntent(SettingsIntent.SaveEditingMcpServer) },
                onCancel = { onIntent(SettingsIntent.CancelEditingMcpServer) },
            )
        }

        // External Tools section
        SettingSection(title = "EXTERNAL TOOLS") {
            if (settings.externalTools.isEmpty()) {
                Text(
                    text = "No external tools configured.",
                    color = IntelliJColors.textMuted,
                    fontSize = 12.sp,
                )
            } else {
                settings.externalTools.forEach { tool ->
                    Text(
                        text = "• ${tool.name}",
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = tool.command,
                        color = IntelliJColors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = Spacing.xs.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerRow(
    id: String,
    server: McpServerConfig,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(IntelliJColors.surfaceContainer)
                .padding(Spacing.sm.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IntelliJCheckbox(
                checked = server.enabled,
                onCheckedChange = onToggle,
            )
            Column(modifier = Modifier.padding(start = Spacing.sm.dp)) {
                Text(
                    text = server.name,
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
                )
                Text(
                    text = "${server.command} ${server.args.joinToString(" ")}",
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                )
            }
        }

        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = IntelliJColors.textSecondary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = IntelliJColors.error,
                )
            }
        }
    }
}

@Composable
private fun McpServerEditPanel(
    editingServer: EditingMcpServer,
    onUpdate: (McpServerConfig) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IntelliJColors.surfaceElevated)
                .padding(Spacing.md.dp),
    ) {
        Text(
            text = if (editingServer.isNew) "Add MCP Server" else "Edit MCP Server",
            color = IntelliJColors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = Spacing.md.dp),
        )

        IntelliJTextField(
            value = editingServer.config.name,
            onValueChange = { onUpdate(editingServer.config.copy(name = it)) },
            label = "Name",
            placeholder = "Server name",
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm.dp),
        )

        IntelliJTextField(
            value = editingServer.config.command,
            onValueChange = { onUpdate(editingServer.config.copy(command = it)) },
            label = "Command",
            placeholder = "Command to start the server",
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm.dp),
        )

        IntelliJTextField(
            value = editingServer.config.args.joinToString(" "),
            onValueChange = {
                onUpdate(
                    editingServer.config.copy(
                        args =
                            it.split(" ").filter { a ->
                                a.isNotBlank()
                            },
                    ),
                )
            },
            label = "Arguments",
            placeholder = "Command-line arguments",
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm.dp),
        )

        IntelliJTextField(
            value = editingServer.config.workingDirectory ?: "",
            onValueChange = { onUpdate(editingServer.config.copy(workingDirectory = it.ifBlank { null })) },
            label = "Working Directory",
            placeholder = "Optional working directory",
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm.dp),
        )

        IntelliJCheckbox(
            checked = editingServer.config.autoStart,
            onCheckedChange = { onUpdate(editingServer.config.copy(autoStart = it)) },
            label = "Auto-start with IDE",
            modifier = Modifier.padding(bottom = Spacing.md.dp),
        )

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IntelliJButton(
                text = "Cancel",
                onClick = onCancel,
                style = ButtonStyle.SECONDARY,
                modifier = Modifier.padding(end = Spacing.sm.dp),
            )
            IntelliJButton(
                text = "Save",
                onClick = onSave,
                style = ButtonStyle.PRIMARY,
                enabled = editingServer.config.name.isNotBlank() && editingServer.config.command.isNotBlank(),
            )
        }
    }
}
