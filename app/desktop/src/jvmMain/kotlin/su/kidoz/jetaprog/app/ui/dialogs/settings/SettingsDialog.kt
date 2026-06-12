package su.kidoz.jetaprog.app.ui.dialogs.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.AppearancePanel
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.EditorPanel
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.LanguagesPanel
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.PluginsPanel
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.ToolsPanel
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.Elevation
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.SettingsCategory
import su.kidoz.jetaprog.settings.SettingsScope

/**
 * Main Settings dialog composable.
 */
@Composable
public fun SettingsDialog(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogOverlay(
        isVisible = state.isVisible,
        onDismiss = { onIntent(SettingsIntent.Cancel) },
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .width(Dimensions.dialogSettingsWidth.dp)
                    .height(Dimensions.dialogSettingsHeight.dp)
                    .shadow(
                        Elevation.dialog.dp,
                        RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
                    ).clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.toolWindowBackground),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                SettingsHeader(
                    searchQuery = state.searchQuery,
                    activeScope = state.activeScope,
                    onSearchChange = { onIntent(SettingsIntent.Search(it)) },
                    onScopeChange = { onIntent(SettingsIntent.SetScope(it)) },
                )

                HorizontalDivider(color = IntelliJColors.border)

                // Main content
                Row(modifier = Modifier.weight(1f)) {
                    // Left: Navigation tree
                    SettingsTree(
                        selectedCategory = state.selectedCategory,
                        selectedSubItem = state.selectedSubItem,
                        onSelectCategory = { onIntent(SettingsIntent.SelectCategory(it)) },
                        onSelectSubItem = { onIntent(SettingsIntent.SelectSubItem(it)) },
                        modifier =
                            Modifier
                                .width(Dimensions.dialogSettingsNavWidth.dp)
                                .fillMaxHeight()
                                .background(IntelliJColors.backgroundDarker),
                    )

                    VerticalDivider(color = IntelliJColors.border)

                    // Right: Settings panel
                    SettingsPanel(
                        state = state,
                        onIntent = onIntent,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }

                HorizontalDivider(color = IntelliJColors.border)

                // Footer
                SettingsFooter(
                    hasUnsavedChanges = state.hasUnsavedChanges,
                    onResetToDefaults = { onIntent(SettingsIntent.ResetToDefaults) },
                    onCancel = { onIntent(SettingsIntent.Cancel) },
                    onApply = { onIntent(SettingsIntent.Apply) },
                    onOk = { onIntent(SettingsIntent.ApplyAndClose) },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    searchQuery: String,
    activeScope: SettingsScope,
    onSearchChange: (String) -> Unit,
    onScopeChange: (SettingsScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.md.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Settings",
            color = IntelliJColors.textPrimary,
            fontSize = 16.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Scope selector
            ScopeSelector(
                activeScope = activeScope,
                onScopeChange = onScopeChange,
            )

            Spacer(modifier = Modifier.width(Spacing.md.dp))

            // Search field
            IntelliJTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search settings...",
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun ScopeSelector(
    activeScope: SettingsScope,
    onScopeChange: (SettingsScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(IntelliJColors.backgroundDarker),
        horizontalArrangement = Arrangement.Center,
    ) {
        SettingsScope.entries.forEach { scope ->
            val isSelected = scope == activeScope
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) IntelliJColors.accent else IntelliJColors.backgroundDarker,
                        ).padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            ) {
                Text(
                    text = scope.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSelected) IntelliJColors.textInverse else IntelliJColors.textSecondary,
                    fontSize = 12.sp,
                    modifier =
                        Modifier
                            .padding(horizontal = Spacing.xs.dp)
                            .then(
                                if (!isSelected) {
                                    Modifier.background(IntelliJColors.backgroundDarker)
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(IntelliJColors.toolWindowBackground)
                .padding(Spacing.lg.dp),
    ) {
        when (state.selectedCategory) {
            SettingsCategory.APPEARANCE -> {
                AppearancePanel(
                    settings = state.effectiveSettings.appearance,
                    onIntent = onIntent,
                )
            }

            SettingsCategory.EDITOR -> {
                EditorPanel(
                    settings = state.effectiveSettings.editor,
                    onIntent = onIntent,
                )
            }

            SettingsCategory.LANGUAGES -> {
                LanguagesPanel(
                    settings = state.effectiveSettings.languages,
                    selectedSubItem = state.selectedSubItem,
                    onIntent = onIntent,
                )
            }

            SettingsCategory.TOOLS -> {
                ToolsPanel(
                    settings = state.effectiveSettings.tools,
                    editingServer = state.editingMcpServer,
                    onIntent = onIntent,
                )
            }

            SettingsCategory.PLUGINS -> {
                PluginsPanel(
                    settings = state.effectiveSettings.plugins,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun SettingsFooter(
    hasUnsavedChanges: Boolean,
    onResetToDefaults: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.md.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntelliJButton(
            text = "Reset to Defaults",
            onClick = onResetToDefaults,
            style = ButtonStyle.SECONDARY,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            IntelliJButton(
                text = "Cancel",
                onClick = onCancel,
                style = ButtonStyle.SECONDARY,
            )
            IntelliJButton(
                text = "Apply",
                onClick = onApply,
                style = ButtonStyle.SECONDARY,
                enabled = hasUnsavedChanges,
            )
            IntelliJButton(
                text = "OK",
                onClick = onOk,
                style = ButtonStyle.PRIMARY,
            )
        }
    }
}
