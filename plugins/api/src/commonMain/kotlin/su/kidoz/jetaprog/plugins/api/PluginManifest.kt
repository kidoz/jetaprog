package su.kidoz.jetaprog.plugins.api

import kotlinx.serialization.Serializable

/**
 * Plugin manifest containing metadata about a plugin.
 * Corresponds to the plugin.json file.
 */
@Serializable
public data class PluginManifest(
    /**
     * Unique plugin identifier (e.g., "su.kidoz.jetaprog.kotlin-support").
     */
    val id: String,
    /**
     * Human-readable plugin name.
     */
    val name: String,
    /**
     * Plugin version (semver format).
     */
    val version: String,
    /**
     * Minimum API version this plugin requires.
     */
    val apiVersion: String = "1.0",
    /**
     * Plugin description.
     */
    val description: String = "",
    /**
     * Plugin author information.
     */
    val author: Author? = null,
    /**
     * Plugin license.
     */
    val license: String = "MIT",
    /**
     * Repository URL.
     */
    val repository: String? = null,
    /**
     * Engine compatibility requirements.
     */
    val engines: Engines = Engines(),
    /**
     * Events that trigger plugin activation.
     */
    val activationEvents: List<String> = emptyList(),
    /**
     * Plugin contributions (languages, commands, etc.).
     */
    val contributes: Contributions = Contributions(),
    /**
     * Dependencies on other plugins.
     */
    val dependencies: Map<String, String> = emptyMap(),
    /**
     * Fully qualified class name of the plugin entry point.
     */
    val main: String? = null,
)

/**
 * Plugin author information.
 */
@Serializable
public data class Author(
    val name: String,
    val email: String? = null,
    val url: String? = null,
)

/**
 * Engine compatibility requirements.
 */
@Serializable
public data class Engines(
    /**
     * Minimum JetaProg version required (semver range).
     */
    val jetaprog: String = ">=1.0.0",
)

/**
 * Plugin contributions.
 */
@Serializable
public data class Contributions(
    /**
     * Language contributions.
     */
    val languages: List<LanguageContribution> = emptyList(),
    /**
     * Command contributions.
     */
    val commands: List<CommandContribution> = emptyList(),
    /**
     * Menu contributions.
     */
    val menus: MenuContributions? = null,
    /**
     * Configuration contributions.
     */
    val configuration: ConfigurationContribution? = null,
    /**
     * Keybinding contributions.
     */
    val keybindings: List<KeybindingContribution> = emptyList(),
)

/**
 * Language contribution.
 */
@Serializable
public data class LanguageContribution(
    val id: String,
    val extensions: List<String> = emptyList(),
    val filenames: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val configuration: String? = null,
)

/**
 * Command contribution.
 */
@Serializable
public data class CommandContribution(
    val command: String,
    val title: String,
    val category: String? = null,
    val icon: String? = null,
)

/**
 * Menu contributions.
 */
@Serializable
public data class MenuContributions(
    val editorContext: List<MenuItemContribution> = emptyList(),
    val explorerContext: List<MenuItemContribution> = emptyList(),
    val commandPalette: List<MenuItemContribution> = emptyList(),
)

/**
 * Menu item contribution.
 */
@Serializable
public data class MenuItemContribution(
    val command: String,
    val group: String? = null,
    val `when`: String? = null,
)

/**
 * Configuration contribution.
 */
@Serializable
public data class ConfigurationContribution(
    val title: String,
    val properties: Map<String, ConfigurationProperty> = emptyMap(),
)

/**
 * Configuration property.
 */
@Serializable
public data class ConfigurationProperty(
    val type: String,
    val default: String? = null,
    val description: String? = null,
    val enum: List<String>? = null,
)

/**
 * Keybinding contribution.
 */
@Serializable
public data class KeybindingContribution(
    val command: String,
    val key: String,
    val mac: String? = null,
    val `when`: String? = null,
)
