package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingRow
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingSection
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.model.EditorSettings

/**
 * Panel for editor settings.
 */
@Composable
public fun EditorPanel(
    settings: EditorSettings,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Tabs & Indentation section
        SettingSection(title = "TABS & INDENTATION") {
            SettingRow(
                label = "Tab Size",
                description = "Number of spaces per tab",
            ) {
                IntelliJTextField(
                    value = settings.tabSize.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let {
                            onIntent(SettingsIntent.SetTabSize(it.coerceIn(1, 16)))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                )
            }

            SettingRow(
                label = "Indentation Style",
                description = "Use spaces or tabs for indentation",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !settings.useTabs,
                        onClick = { onIntent(SettingsIntent.SetUseTabs(false)) },
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = IntelliJColors.accent,
                                unselectedColor = IntelliJColors.textSecondary,
                            ),
                    )
                    Text(
                        text = "Spaces",
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = Spacing.md.dp),
                    )

                    RadioButton(
                        selected = settings.useTabs,
                        onClick = { onIntent(SettingsIntent.SetUseTabs(true)) },
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = IntelliJColors.accent,
                                unselectedColor = IntelliJColors.textSecondary,
                            ),
                    )
                    Text(
                        text = "Tabs",
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                    )
                }
            }

            SettingRow(
                label = "Max Line Length",
                description = "Show indicator at column (0 = disabled)",
            ) {
                IntelliJTextField(
                    value = settings.maxLineLength.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let {
                            onIntent(SettingsIntent.SetMaxLineLength(it.coerceIn(0, 500)))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                )
            }
        }

        // Display section
        SettingSection(title = "DISPLAY") {
            SettingRow(
                label = "Show Line Numbers",
                description = "Display line numbers in the gutter",
            ) {
                IntelliJCheckbox(
                    checked = settings.showLineNumbers,
                    onCheckedChange = { onIntent(SettingsIntent.SetShowLineNumbers(it)) },
                )
            }

            SettingRow(
                label = "Show Minimap",
                description = "Display minimap on the right side",
            ) {
                IntelliJCheckbox(
                    checked = settings.showMinimap,
                    onCheckedChange = { onIntent(SettingsIntent.SetShowMinimap(it)) },
                )
            }

            SettingRow(
                label = "Word Wrap",
                description = "Wrap long lines at the viewport edge",
            ) {
                IntelliJCheckbox(
                    checked = settings.wordWrap,
                    onCheckedChange = { onIntent(SettingsIntent.SetWordWrap(it)) },
                )
            }

            SettingRow(
                label = "Show Whitespace",
                description = "Display whitespace characters (spaces, tabs)",
            ) {
                IntelliJCheckbox(
                    checked = settings.showWhitespace,
                    onCheckedChange = { onIntent(SettingsIntent.SetShowWhitespace(it)) },
                )
            }

            SettingRow(
                label = "Highlight Current Line",
                description = "Highlight the line with the cursor",
            ) {
                IntelliJCheckbox(
                    checked = settings.highlightCurrentLine,
                    onCheckedChange = { onIntent(SettingsIntent.SetHighlightCurrentLine(it)) },
                )
            }
        }

        // Editing section
        SettingSection(title = "EDITING") {
            SettingRow(
                label = "Bracket Matching",
                description = "Highlight matching brackets",
            ) {
                IntelliJCheckbox(
                    checked = settings.bracketMatching,
                    onCheckedChange = { onIntent(SettingsIntent.SetBracketMatching(it)) },
                )
            }

            SettingRow(
                label = "Auto-Close Brackets",
                description = "Automatically close brackets and quotes",
            ) {
                IntelliJCheckbox(
                    checked = settings.autoCloseBrackets,
                    onCheckedChange = { onIntent(SettingsIntent.SetAutoCloseBrackets(it)) },
                )
            }
        }

        // Save Actions section
        SettingSection(title = "SAVE ACTIONS") {
            SettingRow(
                label = "Auto-Save",
                description = "Automatically save files after a delay",
            ) {
                IntelliJCheckbox(
                    checked = settings.autoSave,
                    onCheckedChange = { onIntent(SettingsIntent.SetAutoSave(it)) },
                )
            }

            if (settings.autoSave) {
                SettingRow(
                    label = "Auto-Save Delay",
                    description = "Delay in milliseconds before saving",
                ) {
                    IntelliJTextField(
                        value = settings.autoSaveDelayMs.toString(),
                        onValueChange = { value ->
                            value.toLongOrNull()?.let {
                                onIntent(SettingsIntent.SetAutoSaveDelay(it.coerceIn(100, 10000)))
                            }
                        },
                        modifier = Modifier.width(100.dp),
                    )
                }
            }

            SettingRow(
                label = "Trim Trailing Whitespace",
                description = "Remove trailing whitespace when saving",
            ) {
                IntelliJCheckbox(
                    checked = settings.trimTrailingWhitespace,
                    onCheckedChange = { onIntent(SettingsIntent.SetTrimTrailingWhitespace(it)) },
                )
            }

            SettingRow(
                label = "Insert Final Newline",
                description = "Ensure files end with a newline",
            ) {
                IntelliJCheckbox(
                    checked = settings.insertFinalNewline,
                    onCheckedChange = { onIntent(SettingsIntent.SetInsertFinalNewline(it)) },
                )
            }
        }
    }
}
