package su.kidoz.jetaprog.settings

import kotlinx.serialization.Serializable

/**
 * Defines the scope where settings are stored and resolved.
 *
 * Settings are resolved in order: WORKSPACE -> USER -> IDE -> defaults.
 *
 * The resolution chain means workspace settings take highest precedence,
 * followed by user settings, then IDE-level settings, then built-in defaults.
 */
@Serializable
public enum class SettingsScope {
    /**
     * IDE-level settings stored in the IDE installation directory.
     * These settings are persistent across all users on the machine
     * and survive IDE restarts. Not synced across machines.
     * Stored in: `<IDE_HOME>/config/settings.json`
     */
    IDE,

    /**
     * User-level settings stored in ~/.jetaprog/settings.json.
     * These settings apply to all projects for the current user.
     * Suitable for syncing across machines via Settings Sync.
     */
    USER,

    /**
     * Workspace-level settings stored in .jetaprog/settings.json.
     * These settings are project-specific and can be shared via version control.
     * Highest precedence in the resolution chain.
     */
    WORKSPACE,
}
