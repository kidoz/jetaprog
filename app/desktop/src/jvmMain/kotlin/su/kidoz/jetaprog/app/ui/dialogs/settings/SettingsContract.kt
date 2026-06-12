package su.kidoz.jetaprog.app.ui.dialogs.settings

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State
import su.kidoz.jetaprog.settings.SettingsCategory
import su.kidoz.jetaprog.settings.SettingsScope
import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.CompletionProviderPreference
import su.kidoz.jetaprog.settings.model.EditorSettings
import su.kidoz.jetaprog.settings.model.LanguagesSettings
import su.kidoz.jetaprog.settings.model.McpServerConfig
import su.kidoz.jetaprog.settings.model.PluginUpdatePolicy
import su.kidoz.jetaprog.settings.model.PluginsSettings
import su.kidoz.jetaprog.settings.model.Theme
import su.kidoz.jetaprog.settings.model.ToolsSettings

/**
 * MVI State for the Settings dialog.
 */
@Serializable
public data class SettingsState(
    /**
     * Whether the settings dialog is visible.
     */
    val isVisible: Boolean = false,
    /**
     * Currently selected category in the navigation tree.
     */
    val selectedCategory: SettingsCategory = SettingsCategory.APPEARANCE,
    /**
     * Currently selected sub-item within a category (e.g., "kotlin" in Languages).
     */
    val selectedSubItem: String? = null,
    /**
     * Search query for filtering settings.
     */
    val searchQuery: String = "",
    /**
     * Whether settings are currently being loaded.
     */
    val isLoading: Boolean = false,
    /**
     * Whether there are unsaved changes.
     */
    val hasUnsavedChanges: Boolean = false,
    /**
     * The active scope for editing settings.
     */
    val activeScope: SettingsScope = SettingsScope.USER,
    /**
     * Current appearance settings.
     */
    val appearance: AppearanceSettings = AppearanceSettings.DEFAULT,
    /**
     * Current editor settings.
     */
    val editor: EditorSettings = EditorSettings.DEFAULT,
    /**
     * Current languages settings.
     */
    val languages: LanguagesSettings = LanguagesSettings.DEFAULT,
    /**
     * Current tools settings.
     */
    val tools: ToolsSettings = ToolsSettings.DEFAULT,
    /**
     * Current plugins settings.
     */
    val plugins: PluginsSettings = PluginsSettings.DEFAULT,
    /**
     * Pending changes not yet saved.
     */
    val pendingChanges: AllSettings? = null,
    /**
     * Validation errors keyed by setting path.
     */
    val validationErrors: Map<String, String> = emptyMap(),
    /**
     * MCP server currently being edited (null = not editing).
     */
    val editingMcpServer: EditingMcpServer? = null,
) : State {
    /**
     * Get the effective settings (pending changes or current).
     */
    val effectiveSettings: AllSettings
        get() =
            pendingChanges ?: AllSettings(
                appearance = appearance,
                editor = editor,
                languages = languages,
                tools = tools,
                plugins = plugins,
            )
}

/**
 * State for editing an MCP server.
 */
@Serializable
public data class EditingMcpServer(
    val id: String?,
    val isNew: Boolean,
    val config: McpServerConfig,
)

/**
 * MVI Intents for the Settings dialog.
 */
public sealed interface SettingsIntent : Intent {
    // Dialog visibility
    public data object Show : SettingsIntent

    public data object Hide : SettingsIntent

    // Navigation
    public data class SelectCategory(
        val category: SettingsCategory,
    ) : SettingsIntent

    public data class SelectSubItem(
        val subItem: String?,
    ) : SettingsIntent

    public data class SetScope(
        val scope: SettingsScope,
    ) : SettingsIntent

    // Search
    public data class Search(
        val query: String,
    ) : SettingsIntent

    public data object ClearSearch : SettingsIntent

    // Appearance settings
    public data class SetTheme(
        val theme: Theme,
    ) : SettingsIntent

    public data class SetFontFamily(
        val fontFamily: String,
    ) : SettingsIntent

    public data class SetFontSize(
        val size: Int,
    ) : SettingsIntent

    public data class SetLineHeight(
        val height: Float,
    ) : SettingsIntent

    public data class SetUiScale(
        val scale: Float,
    ) : SettingsIntent

    public data class SetShowToolbarLabels(
        val show: Boolean,
    ) : SettingsIntent

    public data class SetCompactMode(
        val compact: Boolean,
    ) : SettingsIntent

    // Editor settings
    public data class SetTabSize(
        val size: Int,
    ) : SettingsIntent

    public data class SetUseTabs(
        val useTabs: Boolean,
    ) : SettingsIntent

    public data class SetShowLineNumbers(
        val show: Boolean,
    ) : SettingsIntent

    public data class SetShowMinimap(
        val show: Boolean,
    ) : SettingsIntent

    public data class SetWordWrap(
        val wrap: Boolean,
    ) : SettingsIntent

    public data class SetShowWhitespace(
        val show: Boolean,
    ) : SettingsIntent

    public data class SetHighlightCurrentLine(
        val highlight: Boolean,
    ) : SettingsIntent

    public data class SetBracketMatching(
        val match: Boolean,
    ) : SettingsIntent

    public data class SetAutoCloseBrackets(
        val close: Boolean,
    ) : SettingsIntent

    public data class SetAutoSave(
        val enabled: Boolean,
    ) : SettingsIntent

    public data class SetAutoSaveDelay(
        val delayMs: Long,
    ) : SettingsIntent

    public data class SetTrimTrailingWhitespace(
        val trim: Boolean,
    ) : SettingsIntent

    public data class SetInsertFinalNewline(
        val insert: Boolean,
    ) : SettingsIntent

    public data class SetMaxLineLength(
        val length: Int,
    ) : SettingsIntent

    // Language settings
    public data class SetCompletionPreference(
        val languageId: String,
        val preference: CompletionProviderPreference,
    ) : SettingsIntent

    // MCP Servers
    public data object AddMcpServer : SettingsIntent

    public data class EditMcpServer(
        val id: String,
    ) : SettingsIntent

    public data class UpdateEditingMcpServer(
        val config: McpServerConfig,
    ) : SettingsIntent

    public data object SaveEditingMcpServer : SettingsIntent

    public data object CancelEditingMcpServer : SettingsIntent

    public data class RemoveMcpServer(
        val id: String,
    ) : SettingsIntent

    public data class ToggleMcpServer(
        val id: String,
        val enabled: Boolean,
    ) : SettingsIntent

    // Tools - Build Systems
    public data class SetBuildSystemAutoDetect(
        val autoDetect: Boolean,
    ) : SettingsIntent

    // Plugins
    public data class TogglePlugin(
        val pluginId: String,
        val enabled: Boolean,
    ) : SettingsIntent

    public data class SetPluginUpdatePolicy(
        val policy: PluginUpdatePolicy,
    ) : SettingsIntent

    public data class SetAllowPrerelease(
        val allow: Boolean,
    ) : SettingsIntent

    // Actions
    public data object Apply : SettingsIntent

    public data object ApplyAndClose : SettingsIntent

    public data object Cancel : SettingsIntent

    public data object ResetToDefaults : SettingsIntent

    public data class ResetCategoryToDefaults(
        val category: SettingsCategory,
    ) : SettingsIntent

    // Internal
    public data class LoadSettings(
        val settings: AllSettings,
    ) : SettingsIntent

    public data class LoadError(
        val error: String,
    ) : SettingsIntent
}

/**
 * MVI Effects for the Settings dialog.
 */
public sealed interface SettingsEffect : Effect {
    /**
     * Show an error message.
     */
    public data class ShowError(
        val message: String,
    ) : SettingsEffect

    /**
     * Show a success message.
     */
    public data class ShowSuccess(
        val message: String,
    ) : SettingsEffect

    /**
     * Settings were saved successfully.
     */
    public data object SettingsSaved : SettingsEffect

    /**
     * Some changes require restart to take effect.
     */
    public data object RequiresRestart : SettingsEffect

    /**
     * Open the JSON settings file in editor.
     */
    public data class OpenJsonEditor(
        val path: String,
    ) : SettingsEffect

    /**
     * Close the settings dialog.
     */
    public data object CloseDialog : SettingsEffect
}
