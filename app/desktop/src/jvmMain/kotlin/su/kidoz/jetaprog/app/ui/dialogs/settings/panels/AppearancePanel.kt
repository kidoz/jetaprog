package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJDropdown
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingRow
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingSection
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.Theme

/**
 * Panel for appearance settings.
 */
@Composable
public fun AppearancePanel(
    settings: AppearanceSettings,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Theme section
        SettingSection(title = "THEME") {
            SettingRow(
                label = "Color Theme",
                description = "Choose the color theme for the IDE",
            ) {
                IntelliJDropdown(
                    items = Theme.entries.toList(),
                    selectedItem = settings.theme,
                    onItemSelected = { onIntent(SettingsIntent.SetTheme(it)) },
                    itemToString = { it.displayName },
                    modifier = Modifier.width(150.dp),
                )
            }
        }

        // Font section
        SettingSection(title = "FONT") {
            SettingRow(
                label = "Font Family",
                description = "Font used for the editor and UI",
            ) {
                IntelliJDropdown(
                    items = AVAILABLE_FONTS,
                    selectedItem = settings.fontFamily,
                    onItemSelected = { onIntent(SettingsIntent.SetFontFamily(it)) },
                    itemToString = { it },
                    modifier = Modifier.width(180.dp),
                )
            }

            SettingRow(
                label = "Font Size",
                description = "Size in points",
            ) {
                IntelliJTextField(
                    value = settings.fontSize.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let {
                            onIntent(SettingsIntent.SetFontSize(it.coerceIn(8, 72)))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                )
            }

            SettingRow(
                label = "Line Height",
                description = "Line height multiplier",
            ) {
                IntelliJTextField(
                    value = settings.lineHeight.toString(),
                    onValueChange = { value ->
                        value.toFloatOrNull()?.let {
                            onIntent(SettingsIntent.SetLineHeight(it.coerceIn(1.0f, 3.0f)))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                )
            }
        }

        // UI Scale section
        SettingSection(title = "UI") {
            SettingRow(
                label = "UI Scale",
                description = "Scale factor for the user interface",
            ) {
                IntelliJDropdown(
                    items = UI_SCALES,
                    selectedItem = settings.uiScale,
                    onItemSelected = { onIntent(SettingsIntent.SetUiScale(it)) },
                    itemToString = { "${(it * 100).toInt()}%" },
                    modifier = Modifier.width(100.dp),
                )
            }

            SettingRow(
                label = "Show Toolbar Labels",
                description = "Display text labels on toolbar buttons",
            ) {
                IntelliJCheckbox(
                    checked = settings.showToolbarLabels,
                    onCheckedChange = { onIntent(SettingsIntent.SetShowToolbarLabels(it)) },
                )
            }

            SettingRow(
                label = "Compact Mode",
                description = "Reduce padding for more compact UI",
            ) {
                IntelliJCheckbox(
                    checked = settings.compactMode,
                    onCheckedChange = { onIntent(SettingsIntent.SetCompactMode(it)) },
                )
            }
        }
    }
}

private val AVAILABLE_FONTS =
    listOf(
        "JetBrains Mono",
        "Fira Code",
        "Source Code Pro",
        "Consolas",
        "Monaco",
        "Menlo",
        "Ubuntu Mono",
        "Roboto Mono",
    )

private val UI_SCALES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
