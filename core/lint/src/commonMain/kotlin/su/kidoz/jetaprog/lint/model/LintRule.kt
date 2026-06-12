package su.kidoz.jetaprog.lint.model

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.lint.engine.LintContext

/**
 * Unique identifier for a lint rule.
 *
 * Format: `language/rule-name` (e.g., "kotlin/unused-variable", "security/hardcoded-secret")
 */
@Serializable
@JvmInline
public value class LintRuleId(
    public val value: String,
) {
    /**
     * The language prefix (e.g., "kotlin" from "kotlin/unused-variable").
     */
    public val language: String
        get() = value.substringBefore('/', "")

    /**
     * The rule name without language prefix.
     */
    public val name: String
        get() = value.substringAfter('/')

    override fun toString(): String = value

    public companion object {
        /**
         * Creates a rule ID from language and name.
         */
        public fun of(
            language: String,
            name: String,
        ): LintRuleId = LintRuleId("$language/$name")
    }
}

/**
 * Metadata describing a lint rule.
 */
@Serializable
public data class LintRuleDescriptor(
    /**
     * Unique identifier for this rule.
     */
    val id: LintRuleId,
    /**
     * Human-readable name of the rule.
     */
    val name: String,
    /**
     * Detailed description of what this rule checks.
     */
    val description: String,
    /**
     * The category this rule belongs to.
     */
    val category: LintCategory,
    /**
     * Default severity when not configured.
     */
    val defaultSeverity: LintSeverity = LintSeverity.WARNING,
    /**
     * Whether this rule can provide automatic fixes.
     */
    val hasFix: Boolean = false,
    /**
     * URL to documentation for this rule.
     */
    val documentationUrl: String? = null,
    /**
     * Tags for filtering and grouping rules.
     */
    val tags: List<String> = emptyList(),
    /**
     * Language IDs this rule applies to.
     * Empty list means the rule applies to all languages.
     */
    val languages: List<String> = emptyList(),
)

/**
 * Interface for implementing lint rules.
 *
 * Each rule checks for a specific pattern or issue in the code
 * and optionally provides fixes.
 */
public interface LintRule {
    /**
     * Metadata describing this rule.
     */
    public val descriptor: LintRuleDescriptor

    /**
     * Check the code for issues.
     *
     * @param context The context providing access to the code and utilities.
     * @return List of lint results found by this rule.
     */
    public suspend fun check(context: LintContext): List<LintResult>

    /**
     * Provide a fix for a specific issue.
     *
     * @param context The context providing access to the code.
     * @param result The lint result to fix.
     * @return The fix to apply, or null if no fix is available.
     */
    public suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix? = null
}

/**
 * Base implementation of [LintRule] with common functionality.
 */
public abstract class AbstractLintRule(
    override val descriptor: LintRuleDescriptor,
) : LintRule {
    /**
     * Convenience method to create a result for this rule.
     */
    protected fun createResult(
        message: String,
        range: su.kidoz.jetaprog.common.text.TextRange,
        severity: LintSeverity = descriptor.defaultSeverity,
        relatedLocations: List<LintRelatedLocation> = emptyList(),
        data: Map<String, String> = emptyMap(),
    ): LintResult =
        LintResult(
            ruleId = descriptor.id,
            message = message,
            range = range,
            severity = severity,
            category = descriptor.category,
            relatedLocations = relatedLocations,
            data = data,
        )
}
