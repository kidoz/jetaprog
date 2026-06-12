package su.kidoz.jetaprog.plugins.runtime.context

import su.kidoz.jetaprog.plugins.api.services.CommandService
import su.kidoz.jetaprog.plugins.api.services.EditorService
import su.kidoz.jetaprog.plugins.api.services.LanguageService
import su.kidoz.jetaprog.plugins.api.services.LintService
import su.kidoz.jetaprog.plugins.api.services.NotificationService
import su.kidoz.jetaprog.plugins.api.services.SettingsAccessService
import su.kidoz.jetaprog.plugins.api.services.StorageService
import su.kidoz.jetaprog.plugins.api.services.TerminalService
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService
import su.kidoz.jetaprog.plugins.runtime.activation.ActivationEventService

/**
 * Container for all services available to plugins.
 *
 * This is used by PluginContextImpl to provide services to plugins.
 * Services are created once and shared across all plugins.
 */
public class ServiceContainer(
    /**
     * Service for workspace operations (files, folders, settings).
     */
    public val workspace: WorkspaceService,
    /**
     * Service for editor operations (open files, edit text).
     */
    public val editor: EditorService,
    /**
     * Service for language features (completion, hover, diagnostics).
     */
    public val languages: LanguageService,
    /**
     * Service for registering and executing commands.
     */
    public val commands: CommandService,
    /**
     * Service for showing notifications to the user.
     */
    public val notifications: NotificationService,
    /**
     * Service for terminal operations.
     */
    public val terminal: TerminalService,
    /**
     * Service for lint operations.
     */
    public val lint: LintService,
    /**
     * Factory for creating storage services for plugins.
     */
    public val storageFactory: StorageServiceFactory,
    /**
     * Service for firing activation events.
     *
     * IDE components use this service to notify when activation triggers
     * occur (e.g., file opened, command invoked) for lazy plugin activation.
     */
    public val activationEvents: ActivationEventService,
    /**
     * Read-only settings access service for plugins.
     */
    public val settingsAccess: SettingsAccessService,
)

/**
 * Factory for creating storage services for individual plugins.
 */
public fun interface StorageServiceFactory {
    /**
     * Creates a storage service for a plugin.
     *
     * @param pluginId The plugin ID.
     * @return A storage service scoped to the plugin.
     */
    public fun create(pluginId: String): StorageService
}
