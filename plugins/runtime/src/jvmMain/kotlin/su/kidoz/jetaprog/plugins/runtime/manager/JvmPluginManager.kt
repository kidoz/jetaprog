package su.kidoz.jetaprog.plugins.runtime.manager

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.plugins.api.Plugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.runtime.activation.LazyPluginActivator
import su.kidoz.jetaprog.plugins.runtime.context.PluginContextImpl
import su.kidoz.jetaprog.plugins.runtime.context.ServiceContainer
import su.kidoz.jetaprog.plugins.runtime.lifecycle.PluginInfo
import su.kidoz.jetaprog.plugins.runtime.lifecycle.PluginState

private val logger = KotlinLogging.logger {}

/**
 * Entry for a registered plugin with its context and state.
 */
private data class PluginEntry(
    val plugin: Plugin,
    var state: PluginState,
    var context: PluginContextImpl? = null,
)

/**
 * JVM implementation of PluginManager for bundled plugins.
 *
 * This implementation supports explicit registration of bundled plugins
 * (plugins compiled into the application) rather than dynamic discovery
 * from JAR files.
 *
 * Plugins are lazily activated based on their declared activation events.
 *
 * Usage:
 * ```kotlin
 * val lazyActivator = LazyPluginActivator(activationEventService, contributionRegistry, scope)
 * val manager = JvmPluginManager(services, lazyActivator, basePath)
 *
 * // Register bundled plugins
 * manager.registerBundledPlugin(KotlinPlugin())
 * manager.registerBundledPlugin(PythonPlugin())
 *
 * // Start listening for activation triggers
 * lazyActivator.start()
 *
 * // Shutdown when done
 * manager.shutdown()
 * ```
 */
public class JvmPluginManager(
    private val services: ServiceContainer,
    private val lazyActivator: LazyPluginActivator,
    private val basePath: String = System.getProperty("user.home") + "/.jetaprog",
) : PluginManager {
    private val mutex = Mutex()
    private val plugins = mutableMapOf<String, PluginEntry>()

    private val _installedPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    override val installedPlugins: StateFlow<List<PluginInfo>> = _installedPlugins.asStateFlow()

    private val _activePlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    override val activePlugins: StateFlow<List<PluginInfo>> = _activePlugins.asStateFlow()

    /**
     * Registers a bundled plugin.
     *
     * - Plugins with `*` or empty activation events will be in [PluginState.Loaded]
     *   and activated immediately
     * - Plugins with specific activation events will be in [PluginState.Pending]
     *   (awaiting activation trigger)
     *
     * @param plugin The plugin to register.
     * @return true if the plugin should be activated immediately, false if pending lazy activation.
     */
    public suspend fun registerBundledPlugin(plugin: Plugin): Boolean {
        val manifest = plugin.manifest
        val pluginId = manifest.id

        val shouldActivateImmediately =
            mutex.withLock {
                if (plugins.containsKey(pluginId)) {
                    logger.warn { "Plugin $pluginId is already registered, skipping" }
                    return@withLock null
                }

                logger.info { "Registering bundled plugin: ${manifest.name} (${manifest.id})" }

                val activateNow = lazyActivator.registerForLazyActivation(manifest)

                plugins[pluginId] =
                    PluginEntry(
                        plugin = plugin,
                        state = if (activateNow) PluginState.Loaded else PluginState.Pending,
                    )

                updateStateFlows()
                activateNow
            }

        return shouldActivateImmediately ?: false
    }

    // ========================================================================
    // PluginManager Interface Implementation
    // ========================================================================

    override suspend fun discoverPlugins(directories: List<String>): List<PluginManifest> {
        // For bundled plugins, discovery is not needed.
        // This method is kept for interface compatibility.
        logger.debug { "discoverPlugins called but bundled plugins don't use discovery" }
        return emptyList()
    }

    override suspend fun loadPlugin(manifest: PluginManifest): Result<Plugin> =
        mutex.withLock {
            val entry = plugins[manifest.id]
            if (entry != null) {
                return@withLock Result.success(entry.plugin)
            }

            // For bundled plugins, loading is done via registerBundledPlugin
            return@withLock Result.failure(
                IllegalStateException("Bundled plugins must be registered via registerBundledPlugin()"),
            )
        }

    override suspend fun activatePlugin(pluginId: String): Result<Unit> =
        mutex.withLock {
            val entry =
                plugins[pluginId]
                    ?: return@withLock Result.failure(
                        NoSuchElementException("Plugin not found: $pluginId"),
                    )

            if (entry.state == PluginState.Active) {
                logger.debug { "Plugin $pluginId is already active" }
                return@withLock Result.success(Unit)
            }

            val canActivate =
                entry.state == PluginState.Loaded ||
                    entry.state == PluginState.Pending ||
                    entry.state == PluginState.Deactivated
            if (!canActivate) {
                return@withLock Result.failure(
                    IllegalStateException(
                        "Plugin $pluginId cannot be activated from state ${entry.state}",
                    ),
                )
            }

            return@withLock try {
                logger.info { "Activating plugin: $pluginId" }
                entry.state = PluginState.Activating
                updateStateFlows()

                val context =
                    PluginContextImpl(
                        manifest = entry.plugin.manifest,
                        services = services,
                        basePath = basePath,
                    )
                entry.context = context

                entry.plugin.activate(context)

                entry.state = PluginState.Active
                updateStateFlows()

                logger.info { "Plugin activated: $pluginId" }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(e) { "Failed to activate plugin: $pluginId" }
                entry.state = PluginState.Failed(e)
                entry.context?.dispose()
                entry.context = null
                updateStateFlows()
                Result.failure(e)
            }
        }

    override suspend fun deactivatePlugin(pluginId: String): Result<Unit> =
        mutex.withLock {
            val entry =
                plugins[pluginId]
                    ?: return@withLock Result.failure(
                        NoSuchElementException("Plugin not found: $pluginId"),
                    )

            if (entry.state != PluginState.Active) {
                logger.debug { "Plugin $pluginId is not active, nothing to deactivate" }
                return@withLock Result.success(Unit)
            }

            return@withLock try {
                logger.info { "Deactivating plugin: $pluginId" }
                entry.state = PluginState.Deactivating
                updateStateFlows()

                entry.plugin.deactivate()
                entry.context?.dispose()
                entry.context = null

                entry.state = PluginState.Deactivated
                updateStateFlows()

                logger.info { "Plugin deactivated: $pluginId" }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(e) { "Failed to deactivate plugin: $pluginId" }
                entry.state = PluginState.Failed(e)
                entry.context?.dispose()
                entry.context = null
                updateStateFlows()
                Result.failure(e)
            }
        }

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> =
        mutex.withLock {
            val entry =
                plugins[pluginId]
                    ?: return@withLock Result.failure(
                        NoSuchElementException("Plugin not found: $pluginId"),
                    )

            // Deactivate if active
            if (entry.state == PluginState.Active) {
                deactivatePlugin(pluginId)
            }

            plugins.remove(pluginId)
            updateStateFlows()

            logger.info { "Plugin uninstalled: $pluginId" }
            Result.success(Unit)
        }

    override fun getPlugin(pluginId: String): Plugin? = plugins[pluginId]?.plugin

    override fun isPluginActive(pluginId: String): Boolean = plugins[pluginId]?.state == PluginState.Active

    /**
     * Checks if a plugin is pending activation.
     *
     * @param pluginId The plugin ID.
     * @return true if the plugin is registered and pending activation trigger.
     */
    public fun isPluginPending(pluginId: String): Boolean = plugins[pluginId]?.state == PluginState.Pending

    /**
     * Gets the list of pending plugin IDs.
     *
     * @return List of plugin IDs that are awaiting activation trigger.
     */
    public fun getPendingPlugins(): List<String> =
        plugins.entries
            .filter { it.value.state == PluginState.Pending }
            .map { it.key }

    override suspend fun shutdown() {
        lazyActivator.stop()
        logger.info { "Shutting down plugin manager" }

        // Deactivate all active plugins
        val activePluginIds =
            mutex.withLock {
                plugins.entries
                    .filter { it.value.state == PluginState.Active }
                    .map { it.key }
            }

        for (pluginId in activePluginIds) {
            try {
                deactivatePlugin(pluginId)
            } catch (e: Exception) {
                logger.error(e) { "Error deactivating plugin $pluginId during shutdown" }
            }
        }

        mutex.withLock {
            plugins.clear()
            _installedPlugins.value = emptyList()
            _activePlugins.value = emptyList()
        }

        logger.info { "Plugin manager shut down" }
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private fun updateStateFlows() {
        val allPlugins =
            plugins.map { (id, entry) ->
                PluginInfo(
                    id = id,
                    name = entry.plugin.manifest.name,
                    version = entry.plugin.manifest.version,
                    state = entry.state,
                    path = "bundled:$id",
                )
            }

        _installedPlugins.update { allPlugins }
        _activePlugins.update { allPlugins.filter { it.state == PluginState.Active } }
    }
}
