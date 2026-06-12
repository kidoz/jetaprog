package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Settings for the visual appearance of the IDE.
 */
@Serializable
public data class AppearanceSettings(
    /**
     * The color theme to use.
     */
    val theme: Theme = Theme.DARK,
    /**
     * Font family for the editor and UI.
     */
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    /**
     * Font size in points for the editor.
     */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    /**
     * Line height multiplier (1.0 = single-spaced).
     */
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    /**
     * UI scale factor (1.0 = 100%).
     */
    val uiScale: Float = DEFAULT_UI_SCALE,
    /**
     * Show text labels on toolbar buttons.
     */
    val showToolbarLabels: Boolean = true,
    /**
     * Use compact mode for reduced padding.
     */
    val compactMode: Boolean = false,
) {
    public companion object {
        public const val DEFAULT_FONT_FAMILY: String = "JetBrains Mono"
        public const val DEFAULT_FONT_SIZE: Int = 13
        public const val DEFAULT_LINE_HEIGHT: Float = 1.5f
        public const val DEFAULT_UI_SCALE: Float = 1.0f

        public val DEFAULT: AppearanceSettings = AppearanceSettings()
    }
}
