package su.kidoz.jetaprog.plugins.runtime.activation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import su.kidoz.jetaprog.common.DisposableCollection
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.services.CommandService
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Tracks contributions registered by a plugin.
 */
private data class PluginContributions(
    val manifest: PluginManifest,
    val disposables: DisposableCollection = DisposableCollection(),
    var activated: Boolean = false,
)

/**
 * JVM implementation of [ContributionRegistry].
 *
 * This implementation pre-registers command stubs that trigger plugin
 * activation on first invocation. After the plugin activates, its real
 * command handlers replace the stubs.
 */
public class ContributionRegistryImpl(
    private val commandService: CommandService,
    private val scope: CoroutineScope,
) : ContributionRegistry {
    private val contributions = ConcurrentHashMap<String, PluginContributions>()

    override fun registerContributions(
        manifest: PluginManifest,
        onCommandActivation: suspend (commandId: String) -> Unit,
    ) {
        val pluginId = manifest.id

        if (contributions.containsKey(pluginId)) {
            logger.warn { "Contributions already registered for plugin: $pluginId" }
            return
        }

        val pluginContributions = PluginContributions(manifest)

        // Register stub commands that trigger activation
        for (command in manifest.contributes.commands) {
            val commandId = command.command
            logger.debug { "Registering stub command '$commandId' for plugin '$pluginId'" }

            val disposable =
                commandService.registerCommand(commandId) { args ->
                    // When the stub is invoked, trigger plugin activation
                    logger.info { "Stub command '$commandId' invoked, activating plugin '$pluginId'" }
                    onCommandActivation(commandId)

                    // Re-execute the command now that the plugin should have registered the real handler
                    // Note: The real handler should now be registered, so we need to call executeCommand
                    // However, we need to be careful about recursion. The plugin's real handler
                    // should have replaced this stub by now.
                    if (pluginContributions.activated) {
                        commandService.executeCommand(commandId, *args.toTypedArray())
                    } else {
                        logger.warn { "Plugin '$pluginId' activation did not complete for command '$commandId'" }
                        null
                    }
                }

            pluginContributions.disposables.add(disposable)
        }

        contributions[pluginId] = pluginContributions
        logger.info {
            "Registered contributions for plugin '$pluginId': ${manifest.contributes.commands.size} commands"
        }
    }

    override fun activateContributions(pluginId: String) {
        val pluginContributions = contributions[pluginId]
        if (pluginContributions == null) {
            logger.debug { "No contributions to activate for plugin: $pluginId" }
            return
        }

        pluginContributions.activated = true
        logger.debug { "Marked contributions as activated for plugin: $pluginId" }

        // Note: We don't dispose the stub command registrations here.
        // The plugin's real command registration will override them.
        // If the CommandService supports replacement, the stubs will be replaced.
        // Otherwise, the stub will check `activated` flag and re-invoke.
    }

    override fun unregisterContributions(pluginId: String) {
        val pluginContributions = contributions.remove(pluginId)
        if (pluginContributions == null) {
            logger.debug { "No contributions to unregister for plugin: $pluginId" }
            return
        }

        pluginContributions.disposables.dispose()
        logger.info { "Unregistered contributions for plugin: $pluginId" }
    }

    override fun hasContributions(pluginId: String): Boolean = contributions.containsKey(pluginId)
}
