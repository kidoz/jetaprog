package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Available color themes for the IDE.
 */
@Serializable
public enum class Theme(
    public val displayName: String,
) {
    /**
     * Dark theme optimized for low-light environments.
     */
    DARK("Dark"),

    /**
     * Light theme for bright environments.
     */
    LIGHT("Light"),

    /**
     * Follows the system theme preference.
     */
    SYSTEM("System"),
}
