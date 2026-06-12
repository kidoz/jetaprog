package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * Settings for editor behavior and display.
 */
@Serializable
public data class EditorSettings(
    /**
     * Number of spaces per tab.
     */
    val tabSize: Int = DEFAULT_TAB_SIZE,
    /**
     * Use tabs instead of spaces for indentation.
     */
    val useTabs: Boolean = false,
    /**
     * Show line numbers in the gutter.
     */
    val showLineNumbers: Boolean = true,
    /**
     * Show minimap on the right side.
     */
    val showMinimap: Boolean = true,
    /**
     * Wrap long lines at the viewport edge.
     */
    val wordWrap: Boolean = false,
    /**
     * Show whitespace characters (spaces, tabs).
     */
    val showWhitespace: Boolean = false,
    /**
     * Highlight the current line.
     */
    val highlightCurrentLine: Boolean = true,
    /**
     * Enable automatic bracket matching.
     */
    val bracketMatching: Boolean = true,
    /**
     * Automatically close brackets and quotes.
     */
    val autoCloseBrackets: Boolean = true,
    /**
     * Enable auto-save after a delay.
     */
    val autoSave: Boolean = false,
    /**
     * Delay in milliseconds before auto-save triggers.
     */
    val autoSaveDelayMs: Long = DEFAULT_AUTO_SAVE_DELAY_MS,
    /**
     * Remove trailing whitespace on save.
     */
    val trimTrailingWhitespace: Boolean = true,
    /**
     * Ensure file ends with a newline on save.
     */
    val insertFinalNewline: Boolean = true,
    /**
     * Maximum line length indicator column (0 = disabled).
     */
    val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
) {
    public companion object {
        public const val DEFAULT_TAB_SIZE: Int = 4
        public const val DEFAULT_AUTO_SAVE_DELAY_MS: Long = 1000L
        public const val DEFAULT_MAX_LINE_LENGTH: Int = 120

        public val DEFAULT: EditorSettings = EditorSettings()
    }
}
