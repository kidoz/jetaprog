package su.kidoz.jetaprog.plugins.runtime.manager

import kotlinx.coroutines.flow.StateFlow
import su.kidoz.jetaprog.plugins.api.Plugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.runtime.lifecycle.PluginInfo

/**
 * Manages plugin discovery, loading, activation, and deactivation.
 */
public interface PluginManager {
    /**
     * Flow of installed plugins.
     */
    public val installedPlugins: StateFlow<List<PluginInfo>>

    /**
     * Flow of active plugins.
     */
    public val activePlugins: StateFlow<List<PluginInfo>>

    /**
     * Discovers plugins in the given directories.
     * @param directories Directories to search for plugins
     * @return List of discovered plugin manifests
     */
    public suspend fun discoverPlugins(directories: List<String>): List<PluginManifest>

    /**
     * Loads a plugin from its manifest.
     * @param manifest The plugin manifest
     * @return Result containing the loaded plugin or an error
     */
    public suspend fun loadPlugin(manifest: PluginManifest): Result<Plugin>

    /**
     * Activates a plugin by ID.
     * @param pluginId The plugin ID
     * @return Result indicating success or failure
     */
    public suspend fun activatePlugin(pluginId: String): Result<Unit>

    /**
     * Deactivates a plugin by ID.
     * @param pluginId The plugin ID
     * @return Result indicating success or failure
     */
    public suspend fun deactivatePlugin(pluginId: String): Result<Unit>

    /**
     * Uninstalls a plugin by ID.
     * @param pluginId The plugin ID
     * @return Result indicating success or failure
     */
    public suspend fun uninstallPlugin(pluginId: String): Result<Unit>

    /**
     * Gets a plugin by ID.
     * @param pluginId The plugin ID
     * @return The plugin, or null if not found
     */
    public fun getPlugin(pluginId: String): Plugin?

    /**
     * Checks if a plugin is active.
     * @param pluginId The plugin ID
     * @return true if the plugin is active
     */
    public fun isPluginActive(pluginId: String): Boolean

    /**
     * Shuts down the plugin manager, deactivating all plugins.
     */
    public suspend fun shutdown()
}
