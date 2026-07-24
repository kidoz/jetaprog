package su.kidoz.jetaprog.lint.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import su.kidoz.jetaprog.lint.engine.LintEngine
import su.kidoz.jetaprog.lint.model.LintRule

/**
 * Registry for lint providers.
 *
 * Manages registration of lint providers and synchronizes rules
 * with the lint engine.
 */
public class LintProviderRegistry(
    private val engine: LintEngine,
) {
    private val providers = mutableMapOf<String, LintProvider>()
    private val providerRules = mutableMapOf<String, List<LintRule>>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)

    /**
     * Emits whenever the set of registered rules changes.
     *
     * Lazily activated plugins register their providers after documents are
     * already open, so results produced before they arrived are stale;
     * observers use this to re-run analysis.
     */
    public val changes: Flow<Unit> = _changes.asSharedFlow()

    /**
     * Register a lint provider.
     *
     * All rules from the provider will be registered with the engine.
     */
    public fun register(provider: LintProvider) {
        if (provider.id in providers) {
            unregister(provider.id)
        }

        providers[provider.id] = provider
        provider.onRegister()

        val rules = provider.getRules()
        providerRules[provider.id] = rules

        for (rule in rules) {
            engine.registerRule(rule)
        }
        notifyChanged()
    }

    /**
     * Unregister a lint provider by ID.
     *
     * All rules from the provider will be unregistered from the engine.
     */
    public fun unregister(providerId: String) {
        val provider = providers.remove(providerId) ?: return

        val rules = providerRules.remove(providerId) ?: emptyList()
        for (rule in rules) {
            engine.unregisterRule(rule.descriptor.id)
        }

        provider.onUnregister()
        notifyChanged()
    }

    /**
     * Get a registered provider by ID.
     */
    public fun getProvider(providerId: String): LintProvider? = providers[providerId]

    /**
     * Get all registered providers.
     */
    public fun getProviders(): List<LintProvider> = providers.values.toList()

    /**
     * Get providers for a specific language.
     */
    public fun getProvidersForLanguage(languageId: String): List<LintProvider> =
        providers.values.filter { provider ->
            provider.languages.isEmpty() || languageId in provider.languages
        }

    /**
     * Refresh rules from a provider.
     *
     * Call this when a provider's rules may have changed.
     */
    public fun refreshProvider(providerId: String) {
        val provider = providers[providerId] ?: return

        // Remove old rules
        val oldRules = providerRules[providerId] ?: emptyList()
        for (rule in oldRules) {
            engine.unregisterRule(rule.descriptor.id)
        }

        // Add new rules
        val newRules = provider.getRules()
        providerRules[providerId] = newRules
        for (rule in newRules) {
            engine.registerRule(rule)
        }
        notifyChanged()
    }

    /**
     * Get the total count of registered rules.
     */
    public fun getRuleCount(): Int = providerRules.values.sumOf { it.size }

    /**
     * Clear all registered providers.
     */
    public fun clear() {
        for (providerId in providers.keys.toList()) {
            unregister(providerId)
        }
    }

    private fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    private companion object {
        /** Buffer so non-suspending registration never drops a change signal. */
        const val CHANGE_BUFFER = 16
    }
}
