package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to IDE settings for plugins.
 *
 * Provides plugins with the ability to read current settings
 * and observe settings changes, without direct write access
 * to the core settings system.
 */
public interface SettingsAccessService {
    /**
     * Gets a string setting value.
     *
     * @param category The settings category (e.g., "editor", "appearance")
     * @param key The setting key (e.g., "tabSize", "theme")
     * @param defaultValue The default value if the setting is not found
     * @return The setting value
     */
    public fun getString(
        category: String,
        key: String,
        defaultValue: String = "",
    ): String

    /**
     * Gets an integer setting value.
     *
     * @param category The settings category
     * @param key The setting key
     * @param defaultValue The default value if the setting is not found
     * @return The setting value
     */
    public fun getInt(
        category: String,
        key: String,
        defaultValue: Int = 0,
    ): Int

    /**
     * Gets a boolean setting value.
     *
     * @param category The settings category
     * @param key The setting key
     * @param defaultValue The default value if the setting is not found
     * @return The setting value
     */
    public fun getBoolean(
        category: String,
        key: String,
        defaultValue: Boolean = false,
    ): Boolean

    /**
     * Observes a settings category for changes.
     *
     * @param category The settings category to observe, or null for all categories
     * @return A flow that emits the category name whenever settings change
     */
    public fun observeChanges(category: String? = null): Flow<String>

    /**
     * Gets all settings as a flat map of "category.key" to string values.
     *
     * @return A map of all settings
     */
    public fun getAllSettings(): Map<String, String>
}
