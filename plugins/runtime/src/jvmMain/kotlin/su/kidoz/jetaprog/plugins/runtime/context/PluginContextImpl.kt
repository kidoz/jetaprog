package su.kidoz.jetaprog.plugins.runtime.context

import su.kidoz.jetaprog.common.DisposableCollection
import su.kidoz.jetaprog.plugins.api.PluginContext
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.services.CommandService
import su.kidoz.jetaprog.plugins.api.services.EditorService
import su.kidoz.jetaprog.plugins.api.services.LanguageService
import su.kidoz.jetaprog.plugins.api.services.LintService
import su.kidoz.jetaprog.plugins.api.services.NotificationService
import su.kidoz.jetaprog.plugins.api.services.SettingsAccessService
import su.kidoz.jetaprog.plugins.api.services.StorageService
import su.kidoz.jetaprog.plugins.api.services.TerminalService
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService
import java.io.File

/**
 * Implementation of PluginContext for bundled plugins.
 *
 * Provides access to IDE services and plugin-specific storage paths.
 */
public class PluginContextImpl(
    private val manifest: PluginManifest,
    private val services: ServiceContainer,
    private val basePath: String,
) : PluginContext {
    override val workspace: WorkspaceService = services.workspace

    override val editor: EditorService = services.editor

    override val languages: LanguageService = services.languages

    override val commands: CommandService = services.commands

    override val notifications: NotificationService = services.notifications

    override val storage: StorageService = services.storageFactory.create(manifest.id)

    override val terminal: TerminalService = services.terminal

    override val lint: LintService = services.lint

    override val settings: SettingsAccessService = services.settingsAccess

    override val extensionPath: String by lazy {
        File(basePath, "plugins/${manifest.id}").absolutePath.also { path ->
            File(path).mkdirs()
        }
    }

    override val globalStoragePath: String by lazy {
        File(basePath, "storage/global/${manifest.id}").absolutePath.also { path ->
            File(path).mkdirs()
        }
    }

    override val workspaceStoragePath: String by lazy {
        File(basePath, "storage/workspace/${manifest.id}").absolutePath.also { path ->
            File(path).mkdirs()
        }
    }

    override val subscriptions: DisposableCollection = DisposableCollection()

    /**
     * Disposes all subscriptions.
     * Called when the plugin is deactivated.
     */
    internal fun dispose() {
        subscriptions.dispose()
    }
}
