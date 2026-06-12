package su.kidoz.jetaprog.settings

import kotlinx.serialization.Serializable

/**
 * Categories for organizing settings in the Settings UI.
 */
@Serializable
public enum class SettingsCategory(
    public val displayName: String,
    public val description: String,
) {
    /**
     * Visual appearance settings: theme, fonts, UI scale.
     */
    APPEARANCE(
        displayName = "Appearance",
        description = "Theme, fonts, and visual settings",
    ),

    /**
     * Editor behavior settings: tabs, line numbers, word wrap.
     */
    EDITOR(
        displayName = "Editor",
        description = "Editor behavior and display options",
    ),

    /**
     * Language-specific settings and Language Server configurations.
     */
    LANGUAGES(
        displayName = "Languages & Frameworks",
        description = "Language-specific settings and LSP configurations",
    ),

    /**
     * Build systems, MCP servers, and external tools.
     */
    TOOLS(
        displayName = "Tools",
        description = "Build systems, MCP servers, and external tools",
    ),

    /**
     * Plugin management: enable, disable, configure.
     */
    PLUGINS(
        displayName = "Plugins",
        description = "Manage installed plugins",
    ),
}
