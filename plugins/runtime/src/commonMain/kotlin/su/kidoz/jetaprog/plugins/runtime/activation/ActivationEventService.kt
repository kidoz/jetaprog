package su.kidoz.jetaprog.plugins.runtime.activation

import kotlinx.coroutines.flow.Flow

/**
 * Represents a trigger that can activate plugins.
 *
 * Triggers are fired by IDE events and matched against plugin activation events
 * to determine which plugins should be activated.
 */
public sealed interface ActivationTrigger {
    /**
     * Triggered when a file of a specific language is opened in the editor.
     */
    public data class LanguageOpened(
        /**
         * The language identifier of the opened file.
         */
        val languageId: String,
    ) : ActivationTrigger

    /**
     * Triggered when a command is invoked.
     */
    public data class CommandInvoked(
        /**
         * The command identifier that was invoked.
         */
        val commandId: String,
    ) : ActivationTrigger

    /**
     * Triggered when IDE startup has finished.
     */
    public data object StartupFinished : ActivationTrigger

    /**
     * Triggered when a workspace file matches a glob pattern.
     */
    public data class WorkspaceContainsMatched(
        /**
         * The glob pattern that was matched.
         */
        val glob: String,
    ) : ActivationTrigger
}

/**
 * Service for firing and observing activation triggers.
 *
 * This service bridges IDE events (file opens, command invocations, etc.)
 * with the plugin activation system.
 */
public interface ActivationEventService {
    /**
     * Flow of activation triggers.
     *
     * Subscribers (like [LazyPluginActivator]) observe this flow to
     * activate plugins when matching triggers are fired.
     */
    public val triggers: Flow<ActivationTrigger>

    /**
     * Fires a trigger when a file of the specified language is opened.
     *
     * @param languageId The language identifier (e.g., "kotlin", "python").
     */
    public suspend fun fireLanguageOpened(languageId: String)

    /**
     * Fires a trigger when a command is invoked.
     *
     * @param commandId The command identifier.
     */
    public suspend fun fireCommandInvoked(commandId: String)

    /**
     * Fires a trigger when IDE startup has finished.
     */
    public suspend fun fireStartupFinished()

    /**
     * Checks if the workspace contains a file matching the glob pattern.
     *
     * @param glob The glob pattern to check.
     * @return true if a matching file exists in the workspace.
     */
    public suspend fun checkWorkspaceContains(glob: String): Boolean
}
