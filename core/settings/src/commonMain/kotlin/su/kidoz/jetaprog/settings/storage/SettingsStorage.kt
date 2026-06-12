package su.kidoz.jetaprog.settings.storage

import su.kidoz.jetaprog.settings.SettingsScope
import su.kidoz.jetaprog.settings.model.AllSettings

/**
 * Interface for loading and saving settings to persistent storage.
 */
public interface SettingsStorage {
    /**
     * Load settings from the specified scope.
     *
     * @param scope The settings scope (USER or WORKSPACE)
     * @param projectPath The project path (required for WORKSPACE scope)
     * @return The loaded settings, or null if no settings file exists
     */
    public suspend fun load(
        scope: SettingsScope,
        projectPath: String? = null,
    ): Result<AllSettings?>

    /**
     * Save settings to the specified scope.
     *
     * @param scope The settings scope (USER or WORKSPACE)
     * @param settings The settings to save
     * @param projectPath The project path (required for WORKSPACE scope)
     */
    public suspend fun save(
        scope: SettingsScope,
        settings: AllSettings,
        projectPath: String? = null,
    ): Result<Unit>

    /**
     * Check if a settings file exists for the specified scope.
     *
     * @param scope The settings scope
     * @param projectPath The project path (required for WORKSPACE scope)
     */
    public suspend fun exists(
        scope: SettingsScope,
        projectPath: String? = null,
    ): Boolean

    /**
     * Delete settings file for the specified scope.
     *
     * @param scope The settings scope
     * @param projectPath The project path (required for WORKSPACE scope)
     */
    public suspend fun delete(
        scope: SettingsScope,
        projectPath: String? = null,
    ): Result<Unit>

    /**
     * Get the file path for the specified scope.
     *
     * @param scope The settings scope
     * @param projectPath The project path (required for WORKSPACE scope)
     */
    public fun getPath(
        scope: SettingsScope,
        projectPath: String? = null,
    ): String
}
