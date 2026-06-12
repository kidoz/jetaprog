package su.kidoz.jetaprog.plugins.runtime.activation

import su.kidoz.jetaprog.plugins.api.PluginManifest

/**
 * Registry for pre-registering plugin contributions before plugin activation.
 *
 * This allows commands, languages, and other contributions declared in
 * the plugin manifest to be available immediately, even when the plugin
 * is pending activation. When a user invokes a stub command, it triggers
 * plugin activation, and then the real handler takes over.
 */
public interface ContributionRegistry {
    /**
     * Pre-registers contributions from a plugin manifest.
     *
     * For commands, this registers stub handlers that trigger plugin
     * activation on first invocation.
     *
     * @param manifest The plugin manifest containing contributions.
     * @param onCommandActivation Callback to invoke when a stub command
     *        is invoked, triggering plugin activation.
     */
    public fun registerContributions(
        manifest: PluginManifest,
        onCommandActivation: suspend (commandId: String) -> Unit,
    )

    /**
     * Activates contributions for a plugin after it has been activated.
     *
     * This replaces stub command handlers with the real handlers
     * registered by the plugin.
     *
     * @param pluginId The plugin ID.
     */
    public fun activateContributions(pluginId: String)

    /**
     * Unregisters all contributions from a plugin.
     *
     * @param pluginId The plugin ID.
     */
    public fun unregisterContributions(pluginId: String)

    /**
     * Checks if a plugin has registered contributions.
     *
     * @param pluginId The plugin ID.
     * @return true if the plugin has registered contributions.
     */
    public fun hasContributions(pluginId: String): Boolean
}
