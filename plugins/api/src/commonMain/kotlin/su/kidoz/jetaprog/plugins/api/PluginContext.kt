package su.kidoz.jetaprog.plugins.api

import su.kidoz.jetaprog.common.DisposableCollection
import su.kidoz.jetaprog.plugins.api.services.CommandService
import su.kidoz.jetaprog.plugins.api.services.EditorService
import su.kidoz.jetaprog.plugins.api.services.LanguageService
import su.kidoz.jetaprog.plugins.api.services.LintService
import su.kidoz.jetaprog.plugins.api.services.NotificationService
import su.kidoz.jetaprog.plugins.api.services.SettingsAccessService
import su.kidoz.jetaprog.plugins.api.services.StorageService
import su.kidoz.jetaprog.plugins.api.services.TerminalService
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService

/**
 * Context provided to plugins during activation.
 *
 * Provides access to IDE services and plugin-specific paths.
 * Services should be used to integrate with the IDE.
 */
public interface PluginContext {
    /**
     * Service for workspace operations (files, folders, settings).
     */
    public val workspace: WorkspaceService

    /**
     * Service for editor operations (open files, edit text).
     */
    public val editor: EditorService

    /**
     * Service for language features (completion, hover, diagnostics).
     */
    public val languages: LanguageService

    /**
     * Service for registering and executing commands.
     */
    public val commands: CommandService

    /**
     * Service for showing notifications to the user.
     */
    public val notifications: NotificationService

    /**
     * Service for plugin storage (key-value store).
     */
    public val storage: StorageService

    /**
     * Service for terminal operations.
     */
    public val terminal: TerminalService

    /**
     * Service for lint operations.
     */
    public val lint: LintService

    /**
     * Read-only access to IDE settings.
     * Plugins can read settings and observe changes but cannot write directly.
     */
    public val settings: SettingsAccessService

    /**
     * The absolute path to the plugin's installation directory.
     */
    public val extensionPath: String

    /**
     * The absolute path to a directory for global plugin storage.
     */
    public val globalStoragePath: String

    /**
     * The absolute path to a directory for workspace-specific plugin storage.
     */
    public val workspaceStoragePath: String

    /**
     * Collection of subscriptions that will be disposed when the plugin is deactivated.
     * Add any disposables here that should be cleaned up automatically.
     */
    public val subscriptions: DisposableCollection
}
