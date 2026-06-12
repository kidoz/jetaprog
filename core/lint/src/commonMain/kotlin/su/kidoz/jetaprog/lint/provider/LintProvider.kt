package su.kidoz.jetaprog.lint.provider

import su.kidoz.jetaprog.lint.model.LintRule

/**
 * Provides lint rules for a specific language or domain.
 *
 * Lint providers are registered with the [LintProviderRegistry] to contribute
 * rules to the lint engine.
 */
public interface LintProvider {
    /**
     * Unique identifier for this provider.
     */
    public val id: String

    /**
     * Human-readable name for this provider.
     */
    public val name: String

    /**
     * The language IDs this provider supports.
     * Empty list means the provider applies to all languages.
     */
    public val languages: List<String>

    /**
     * Get all rules provided by this provider.
     */
    public fun getRules(): List<LintRule>

    /**
     * Called when the provider is registered.
     */
    public fun onRegister() {}

    /**
     * Called when the provider is unregistered.
     */
    public fun onUnregister() {}
}

/**
 * Base implementation of [LintProvider] with common functionality.
 */
public abstract class AbstractLintProvider(
    override val id: String,
    override val name: String,
    override val languages: List<String> = emptyList(),
) : LintProvider {
    private val rules = mutableListOf<LintRule>()

    override fun getRules(): List<LintRule> = rules.toList()

    /**
     * Register a rule with this provider.
     */
    protected fun registerRule(rule: LintRule) {
        rules.add(rule)
    }

    /**
     * Register multiple rules with this provider.
     */
    protected fun registerRules(vararg rulesToAdd: LintRule) {
        rules.addAll(rulesToAdd)
    }
}
