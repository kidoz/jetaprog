package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJDropdown
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingRow
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingSection
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.model.PluginUpdatePolicy
import su.kidoz.jetaprog.settings.model.PluginsSettings

/**
 * Panel for plugin management settings.
 */
@Composable
public fun PluginsPanel(
    settings: PluginsSettings,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Update Policy section
        SettingSection(title = "UPDATE POLICY") {
            SettingRow(
                label = "Update Policy",
                description = "How to handle plugin updates",
            ) {
                IntelliJDropdown(
                    items = PluginUpdatePolicy.entries.toList(),
                    selectedItem = settings.updatePolicy,
                    onItemSelected = { onIntent(SettingsIntent.SetPluginUpdatePolicy(it)) },
                    itemToString = { it.displayName },
                )
            }

            SettingRow(
                label = "Allow Pre-release Versions",
                description = "Include beta and alpha versions in updates",
            ) {
                IntelliJCheckbox(
                    checked = settings.allowPrerelease,
                    onCheckedChange = { onIntent(SettingsIntent.SetAllowPrerelease(it)) },
                )
            }
        }

        // Installed Plugins section
        SettingSection(title = "INSTALLED PLUGINS") {
            Text(
                text = "Manage installed plugins. Disabled plugins will not be loaded on startup.",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = Spacing.md.dp),
            )

            // Bundled plugins (hardcoded for now)
            BUNDLED_PLUGINS.forEach { plugin ->
                PluginRow(
                    id = plugin.id,
                    name = plugin.name,
                    description = plugin.description,
                    version = plugin.version,
                    isBundled = true,
                    isEnabled = plugin.id !in settings.disabledPlugins,
                    onToggle = { enabled -> onIntent(SettingsIntent.TogglePlugin(plugin.id, enabled)) },
                )
            }
        }

        // Disabled Plugins section
        if (settings.disabledPlugins.isNotEmpty()) {
            SettingSection(title = "DISABLED PLUGINS") {
                settings.disabledPlugins.forEach { pluginId ->
                    val plugin = BUNDLED_PLUGINS.find { it.id == pluginId }
                    Text(
                        text = "• ${plugin?.name ?: pluginId}",
                        color = IntelliJColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = Spacing.xxs.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    id: String,
    name: String,
    description: String,
    version: String,
    isBundled: Boolean,
    isEnabled: Boolean,
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
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = if (isEnabled) IntelliJColors.accent else IntelliJColors.textMuted,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = if (isEnabled) IntelliJColors.textPrimary else IntelliJColors.textMuted,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "v$version",
                        color = IntelliJColors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = Spacing.sm.dp),
                    )
                    if (isBundled) {
                        Text(
                            text = "Bundled",
                            color = IntelliJColors.textMuted,
                            fontSize = 10.sp,
                            modifier =
                                Modifier
                                    .padding(start = Spacing.sm.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IntelliJColors.backgroundDarker)
                                    .padding(horizontal = Spacing.xs.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = description,
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        IntelliJCheckbox(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

/**
 * Information about a plugin.
 */
private data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
)

/**
 * List of bundled plugins (placeholder data).
 */
private val BUNDLED_PLUGINS =
    listOf(
        PluginInfo(
            id = "kotlin-language",
            name = "Kotlin Language Support",
            description = "Syntax highlighting, code completion, and navigation for Kotlin",
            version = "1.0.0",
        ),
        PluginInfo(
            id = "vala-language",
            name = "Vala Language Support",
            description = "Syntax highlighting and LSP integration for Vala",
            version = "0.9.0",
        ),
        PluginInfo(
            id = "git-integration",
            name = "Git Integration",
            description = "Version control with Git, diff viewer, and commit history",
            version = "1.0.0",
        ),
        PluginInfo(
            id = "mcp-integration",
            name = "MCP Integration",
            description = "Model Context Protocol server for AI agent integration",
            version = "1.0.0",
        ),
        PluginInfo(
            id = "terminal",
            name = "Terminal",
            description = "Integrated terminal emulator",
            version = "1.0.0",
        ),
    )
