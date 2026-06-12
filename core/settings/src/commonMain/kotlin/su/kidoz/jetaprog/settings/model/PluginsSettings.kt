package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Settings for plugin management.
 */
@Serializable
public data class PluginsSettings(
    /**
     * Set of disabled plugin IDs.
     */
    val disabledPlugins: Set<String> = emptySet(),
    /**
     * Plugin update policy.
     */
    val updatePolicy: PluginUpdatePolicy = PluginUpdatePolicy.NOTIFY,
    /**
     * Allow installation of pre-release versions.
     */
    val allowPrerelease: Boolean = false,
    /**
     * Trusted plugin sources/publishers.
     */
    val trustedSources: Set<String> = emptySet(),
    /**
     * Per-plugin settings keyed by plugin ID.
     */
    val pluginSettings: Map<String, Map<String, String>> = emptyMap(),
) {
    public companion object {
        public val DEFAULT: PluginsSettings = PluginsSettings()
    }
}

/**
 * Plugin update policy.
 */
@Serializable
public enum class PluginUpdatePolicy(
    public val displayName: String,
) {
    /**
     * Automatically update plugins.
     */
    AUTO("Automatic"),

    /**
     * Notify about available updates.
     */
    NOTIFY("Notify"),

    /**
     * Manual updates only.
     */
    MANUAL("Manual"),
}
