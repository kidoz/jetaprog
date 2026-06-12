package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.IntelliJDropdown
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.controls.SettingSection
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.model.CompletionProviderPreference
import su.kidoz.jetaprog.settings.model.LanguagesSettings

/**
 * Panel for language and framework settings.
 */
@Composable
public fun LanguagesPanel(
    settings: LanguagesSettings,
    selectedSubItem: String?,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        when (selectedSubItem) {
            "Kotlin" -> KotlinLanguageSettings(settings, onIntent)
            "Vala" -> ValaLanguageSettings(settings)
            "LSP Servers" -> LspServersSettings(settings)
            else -> DefaultLanguagesOverview(settings)
        }
    }
}

@Composable
private fun DefaultLanguagesOverview(
    settings: LanguagesSettings,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingSection(title = "LANGUAGE DEFAULTS") {
            Text(
                text = "Default Encoding: ${settings.defaults.encoding}",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = Spacing.xs.dp),
            )
            Text(
                text = "Line Endings: ${settings.defaults.lineEndings.name}",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = Spacing.xs.dp),
            )
            Text(
                text = "Default Tab Size: ${settings.defaults.tabSize}",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
            )
        }

        SettingSection(title = "CONFIGURED LANGUAGES") {
            if (settings.languages.isEmpty()) {
                Text(
                    text = "No language-specific configurations. Select a language from the tree to configure.",
                    color = IntelliJColors.textMuted,
                    fontSize = 12.sp,
                )
            } else {
                settings.languages.forEach { (langId, _) ->
                    Text(
                        text = "• $langId",
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = Spacing.xxs.dp),
                    )
                }
            }
        }

        SettingSection(title = "LANGUAGE SERVERS") {
            if (settings.languageServers.isEmpty()) {
                Text(
                    text = "No language servers configured. Select 'LSP Servers' to add one.",
                    color = IntelliJColors.textMuted,
                    fontSize = 12.sp,
                )
            } else {
                settings.languageServers.forEach { (serverId, server) ->
                    val status = if (server.enabled) "enabled" else "disabled"
                    Text(
                        text = "• $serverId ($status)",
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = Spacing.xxs.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KotlinLanguageSettings(
    settings: LanguagesSettings,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kotlinConfig =
        settings.languages["kotlin"] ?: su.kidoz.jetaprog.settings.model
            .LanguageConfig()

    Column(modifier = modifier.padding(Spacing.md.dp)) {
        SettingSection(title = "KOTLIN SETTINGS") {
            IntelliJDropdown(
                label = "Completion Provider",
                items = CompletionProviderPreference.entries,
                selectedItem = kotlinConfig.completionPreference,
                onItemSelected = { preference ->
                    onIntent(SettingsIntent.SetCompletionPreference("kotlin", preference))
                },
                itemToString = { it.name },
                modifier = Modifier.padding(bottom = Spacing.md.dp),
            )

            Text(
                text = "Tab Size: ${kotlinConfig.tabSize ?: "default"}",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = Spacing.sm.dp),
            )
            Text(
                text = "Format on Save: ${if (kotlinConfig.formatOnSave) "Yes" else "No"}",
                color = IntelliJColors.textPrimary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ValaLanguageSettings(
    settings: LanguagesSettings,
    modifier: Modifier = Modifier,
) {
    val valaConfig = settings.languages["vala"]

    Column(modifier = modifier) {
        SettingSection(title = "VALA SETTINGS") {
            Text(
                text = "Vala language-specific settings will be available here.",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
            )

            if (valaConfig != null) {
                Text(
                    text = "Language Server: ${valaConfig.languageServerId ?: "none"}",
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = Spacing.sm.dp),
                )
            }
        }
    }
}

@Composable
private fun LspServersSettings(
    settings: LanguagesSettings,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingSection(title = "LANGUAGE SERVER PROTOCOL") {
            Text(
                text = "Configure Language Server Protocol (LSP) servers for enhanced language support.",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = Spacing.md.dp),
            )

            if (settings.languageServers.isEmpty()) {
                Text(
                    text = "No language servers configured.",
                    color = IntelliJColors.textSecondary,
                    fontSize = 13.sp,
                )
            } else {
                settings.languageServers.forEach { (serverId, server) ->
                    Column(modifier = Modifier.padding(bottom = Spacing.md.dp)) {
                        Text(
                            text = serverId,
                            color = IntelliJColors.textPrimary,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Command: ${server.command} ${server.args.joinToString(" ")}",
                            color = IntelliJColors.textMuted,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "Languages: ${server.languages.joinToString(", ")}",
                            color = IntelliJColors.textMuted,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "Status: ${if (server.enabled) "Enabled" else "Disabled"}",
                            color = if (server.enabled) IntelliJColors.success else IntelliJColors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
