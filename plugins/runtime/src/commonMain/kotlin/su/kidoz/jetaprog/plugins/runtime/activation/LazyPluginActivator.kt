package su.kidoz.jetaprog.plugins.runtime.activation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.plugins.api.PluginManifest

/**
 * Callback interface for activating a plugin by ID.
 */
public fun interface PluginActivator {
    /**
     * Activates a plugin by its ID.
     *
     * @param pluginId The plugin ID to activate.
     * @return Result indicating success or failure.
     */
    public suspend fun activatePlugin(pluginId: String): Result<Unit>
}

/**
 * Orchestrates lazy plugin activation based on activation events.
 *
 * This class manages the lifecycle of lazy-activated plugins:
 * 1. Registers plugins for lazy activation based on their manifest
 * 2. Pre-registers contributions (commands, etc.) as stubs
 * 3. Listens for activation triggers from [ActivationEventService]
 * 4. Activates plugins when matching triggers fire
 *
 * Plugins with empty activation events or `*` are activated immediately.
 * Plugins with `workspaceContains` patterns are checked at registration time.
 */
public class LazyPluginActivator(
    private val activationEventService: ActivationEventService,
    private val contributionRegistry: ContributionRegistry,
    private val pluginActivator: PluginActivator,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /**
     * Maps plugin IDs to their parsed activation events.
     */
    private val pendingPlugins = mutableMapOf<String, List<ActivationEvent>>()

    /**
     * Set of plugin IDs that have been activated to avoid double-activation.
     */
    private val activatedPlugins = mutableSetOf<String>()

    private var triggerListenerJob: Job? = null

    /**
     * Starts listening for activation triggers.
     *
     * Call this once after registering all plugins to begin
     * responding to activation events.
     */
    public fun start() {
        triggerListenerJob =
            scope.launch {
                activationEventService.triggers.collect { trigger ->
                    handleTrigger(trigger)
                }
            }
    }

    /**
     * Stops listening for activation triggers.
     */
    public fun stop() {
        triggerListenerJob?.cancel()
        triggerListenerJob = null
    }

    /**
     * Registers a plugin for lazy activation based on its manifest.
     *
     * This method evaluates the plugin's activation events and determines
     * whether to activate immediately or wait for triggers:
     *
     * - Empty activation events: Activates immediately
     * - `*` (Always): Activates immediately
     * - `workspaceContains:` patterns: Checks workspace and activates if matched
     * - Other events: Registers for lazy activation
     *
     * @param manifest The plugin manifest.
     * @return true if the plugin should be activated immediately,
     *         false if it's pending activation.
     */
    public suspend fun registerForLazyActivation(manifest: PluginManifest): Boolean {
        val pluginId = manifest.id
        val events = ActivationEvent.parseAll(manifest.activationEvents)

        // Empty activation events means activate immediately
        if (events.isEmpty()) {
            return true
        }

        // Check for immediate activation events
        val hasAlways = events.any { it is ActivationEvent.Always }
        if (hasAlways) {
            return true
        }

        // Check workspaceContains patterns
        for (event in events) {
            if (event is ActivationEvent.WorkspaceContains) {
                val matches = activationEventService.checkWorkspaceContains(event.glob)
                if (matches) {
                    return true
                }
            }
        }

        // Register contributions (stub commands) before plugin activates
        contributionRegistry.registerContributions(manifest) { commandId ->
            activatePluginOnCommand(pluginId, commandId)
        }

        // Register for lazy activation
        mutex.withLock {
            pendingPlugins[pluginId] = events
        }

        return false
    }

    /**
     * Handles a command invocation that should trigger plugin activation.
     */
    private suspend fun activatePluginOnCommand(
        pluginId: String,
        commandId: String,
    ) {
        activateIfPending(pluginId)
    }

    /**
     * Handles an activation trigger.
     */
    private suspend fun handleTrigger(trigger: ActivationTrigger) {
        val pluginsToActivate =
            mutex.withLock {
                pendingPlugins.entries
                    .filter { (_, events) -> matchesTrigger(events, trigger) }
                    .map { it.key }
            }

        for (pluginId in pluginsToActivate) {
            activateIfPending(pluginId)
        }
    }

    /**
     * Checks if any of the activation events match the given trigger.
     */
    private fun matchesTrigger(
        events: List<ActivationEvent>,
        trigger: ActivationTrigger,
    ): Boolean = events.any { event -> eventMatchesTrigger(event, trigger) }

    /**
     * Checks if a single activation event matches the trigger.
     */
    private fun eventMatchesTrigger(
        event: ActivationEvent,
        trigger: ActivationTrigger,
    ): Boolean =
        when {
            event is ActivationEvent.OnLanguage && trigger is ActivationTrigger.LanguageOpened -> {
                event.languageId == trigger.languageId
            }

            event is ActivationEvent.OnCommand && trigger is ActivationTrigger.CommandInvoked -> {
                event.commandId == trigger.commandId
            }

            event is ActivationEvent.OnStartupFinished && trigger is ActivationTrigger.StartupFinished -> {
                true
            }

            event is ActivationEvent.WorkspaceContains && trigger is ActivationTrigger.WorkspaceContainsMatched -> {
                event.glob == trigger.glob
            }

            else -> {
                false
            }
        }

    /**
     * Activates a plugin if it's still pending.
     */
    private suspend fun activateIfPending(pluginId: String) {
        val shouldActivate =
            mutex.withLock {
                if (activatedPlugins.contains(pluginId)) {
                    false
                } else {
                    pendingPlugins.remove(pluginId)
                    activatedPlugins.add(pluginId)
                    true
                }
            }

        if (shouldActivate) {
            pluginActivator.activatePlugin(pluginId)
            contributionRegistry.activateContributions(pluginId)
        }
    }

    /**
     * Unregisters a plugin from lazy activation.
     *
     * @param pluginId The plugin ID.
     */
    public suspend fun unregister(pluginId: String) {
        mutex.withLock {
            pendingPlugins.remove(pluginId)
            activatedPlugins.remove(pluginId)
        }
        contributionRegistry.unregisterContributions(pluginId)
    }

    /**
     * Checks if a plugin is pending activation.
     *
     * @param pluginId The plugin ID.
     * @return true if the plugin is registered and pending activation.
     */
    public suspend fun isPending(pluginId: String): Boolean =
        mutex.withLock {
            pendingPlugins.containsKey(pluginId)
        }

    /**
     * Gets the number of pending plugins.
     */
    public suspend fun pendingCount(): Int =
        mutex.withLock {
            pendingPlugins.size
        }
}
