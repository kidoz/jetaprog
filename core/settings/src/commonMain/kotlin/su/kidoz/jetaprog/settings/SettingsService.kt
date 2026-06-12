package su.kidoz.jetaprog.settings

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.settings.model.AllSettings
import su.kidoz.jetaprog.settings.model.AppearanceSettings
import su.kidoz.jetaprog.settings.model.EditorSettings
import su.kidoz.jetaprog.settings.model.LanguagesSettings
import su.kidoz.jetaprog.settings.model.PluginsSettings
import su.kidoz.jetaprog.settings.model.ToolsSettings

/**
 * Service for managing application settings.
 *
 * Provides read/write access to settings with automatic layered resolution:
 * workspace settings -> user settings -> defaults.
 */
public interface SettingsService {
    /**
     * The current project path, if any.
     */
    public val projectPath: String?

    /**
     * Get all settings resolved with layered precedence.
     */
    public val settings: Flow<AllSettings>

    /**
     * Get appearance settings.
     */
    public val appearance: Flow<AppearanceSettings>

    /**
     * Get editor settings.
     */
    public val editor: Flow<EditorSettings>

    /**
     * Get languages settings.
     */
    public val languages: Flow<LanguagesSettings>

    /**
     * Get tools settings.
     */
    public val tools: Flow<ToolsSettings>

    /**
     * Get plugins settings.
     */
    public val plugins: Flow<PluginsSettings>

    /**
     * Initialize the service and load settings.
     *
     * @param projectPath Optional project path for workspace settings
     */
    public suspend fun initialize(projectPath: String? = null)

    /**
     * Get the current resolved settings.
     */
    public fun getCurrentSettings(): AllSettings

    /**
     * Update settings for the specified scope.
     *
     * @param scope The scope to update (USER or WORKSPACE)
     * @param update Function to apply updates to the settings
     */
    public suspend fun updateSettings(
        scope: SettingsScope,
        update: AllSettings.() -> AllSettings,
    ): Result<Unit>

    /**
     * Update appearance settings.
     */
    public suspend fun updateAppearance(
        scope: SettingsScope,
        update: AppearanceSettings.() -> AppearanceSettings,
    ): Result<Unit>

    /**
     * Update editor settings.
     */
    public suspend fun updateEditor(
        scope: SettingsScope,
        update: EditorSettings.() -> EditorSettings,
    ): Result<Unit>

    /**
     * Update languages settings.
     */
    public suspend fun updateLanguages(
        scope: SettingsScope,
        update: LanguagesSettings.() -> LanguagesSettings,
    ): Result<Unit>

    /**
     * Update tools settings.
     */
    public suspend fun updateTools(
        scope: SettingsScope,
        update: ToolsSettings.() -> ToolsSettings,
    ): Result<Unit>

    /**
     * Update plugins settings.
     */
    public suspend fun updatePlugins(
        scope: SettingsScope,
        update: PluginsSettings.() -> PluginsSettings,
    ): Result<Unit>

    /**
     * Reset settings to defaults for the specified scope.
     *
     * @param scope The scope to reset
     * @param category Optional category to reset (null = all categories)
     */
    public suspend fun resetToDefaults(
        scope: SettingsScope,
        category: SettingsCategory? = null,
    ): Result<Unit>

    /**
     * Reload settings from storage.
     */
    public suspend fun reload(): Result<Unit>

    /**
     * Get raw settings for a specific scope (unresolved).
     */
    public suspend fun getRawSettings(scope: SettingsScope): AllSettings?
}
