package su.kidoz.jetaprog.plugins.runtime.lifecycle

/**
 * Represents the state of a plugin in its lifecycle.
 */
public sealed interface PluginState {
    /**
     * Plugin is pending activation.
     */
    public data object Pending : PluginState

    /**
     * Plugin has been discovered but not loaded.
     */
    public data object Discovered : PluginState

    /**
     * Plugin is being loaded.
     */
    public data object Loading : PluginState

    /**
     * Plugin is loaded but not activated.
     */
    public data object Loaded : PluginState

    /**
     * Plugin is being activated.
     */
    public data object Activating : PluginState

    /**
     * Plugin is active and running.
     */
    public data object Active : PluginState

    /**
     * Plugin is being deactivated.
     */
    public data object Deactivating : PluginState

    /**
     * Plugin has been deactivated.
     */
    public data object Deactivated : PluginState

    /**
     * Plugin failed to load or activate.
     */
    public data class Failed(
        val error: Throwable,
    ) : PluginState
}

/**
 * Information about a plugin.
 */
public data class PluginInfo(
    /**
     * The plugin ID.
     */
    val id: String,
    /**
     * The plugin name.
     */
    val name: String,
    /**
     * The plugin version.
     */
    val version: String,
    /**
     * The current state.
     */
    val state: PluginState,
    /**
     * The path to the plugin.
     */
    val path: String,
)
