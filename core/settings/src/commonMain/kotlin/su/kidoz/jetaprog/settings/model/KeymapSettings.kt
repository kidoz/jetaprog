package su.kidoz.jetaprog.settings.model

import kotlinx.serialization.Serializable

/**
 * A persisted keyboard shortcut.
 *
 * Stored as a raw platform key code plus modifier flags so the settings
 * module stays independent of any UI toolkit; the application converts
 * to/from its own key representation.
 */
@Serializable
public data class ShortcutSpec(
    /** The platform key code. */
    val keyCode: Long,
    /** Whether Ctrl (or Cmd on macOS) is required. */
    val ctrl: Boolean = false,
    /** Whether Shift is required. */
    val shift: Boolean = false,
    /** Whether Alt (or Option on macOS) is required. */
    val alt: Boolean = false,
    /** Whether Meta (Cmd on macOS, Win on Windows) is required. */
    val meta: Boolean = false,
)

/**
 * User-customized keymap settings: per-action shortcut overrides on top of
 * the built-in default keymap.
 */
@Serializable
public data class KeymapSettings(
    /**
     * Custom shortcuts keyed by action id (e.g. `navigation.gotoFile`).
     */
    val customShortcuts: Map<String, ShortcutSpec> = emptyMap(),
) {
    public companion object {
        /**
         * Default settings with no custom shortcuts.
         */
        public val DEFAULT: KeymapSettings = KeymapSettings()
    }
}
