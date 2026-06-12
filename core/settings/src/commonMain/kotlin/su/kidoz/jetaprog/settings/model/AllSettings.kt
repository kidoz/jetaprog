package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Container for all application settings.
 *
 * This class aggregates all setting categories into a single serializable structure
 * for storage and retrieval.
 */
@Serializable
public data class AllSettings(
    /**
     * Visual appearance settings.
     */
    val appearance: AppearanceSettings = AppearanceSettings.DEFAULT,
    /**
     * Editor behavior and display settings.
     */
    val editor: EditorSettings = EditorSettings.DEFAULT,
    /**
     * Language-specific settings and LSP configurations.
     */
    val languages: LanguagesSettings = LanguagesSettings.DEFAULT,
    /**
     * Build systems, MCP servers, and external tools.
     */
    val tools: ToolsSettings = ToolsSettings.DEFAULT,
    /**
     * Plugin management settings.
     */
    val plugins: PluginsSettings = PluginsSettings.DEFAULT,
) {
    public companion object {
        /**
         * Default settings with all default values.
         */
        public val DEFAULT: AllSettings = AllSettings()
    }
}
