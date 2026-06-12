package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.model.LintSummary
import su.kidoz.jetaprog.lint.provider.LintProvider

/**
 * Service for lint operations.
 *
 * Provides access to lint functionality for plugins, including
 * registering providers, triggering lints, and accessing results.
 */
public interface LintService {
    /**
     * Register a lint provider.
     *
     * @param provider The provider to register.
     * @return Disposable to unregister the provider.
     */
    public fun registerProvider(provider: LintProvider): Disposable

    /**
     * Lint a single file.
     *
     * @param uri The file URI.
     * @param languageId The language ID.
     * @param content The file content.
     * @return The lint results.
     */
    public suspend fun lintFile(
        uri: String,
        languageId: String,
        content: String,
    ): List<LintResult>

    /**
     * Get lint results for a file.
     *
     * @param uri The file URI.
     * @return The cached lint results, or empty list if not linted.
     */
    public fun getResults(uri: String): List<LintResult>

    /**
     * Get all lint results.
     */
    public fun getAllResults(): Map<String, List<LintResult>>

    /**
     * Get the lint summary.
     */
    public fun getSummary(): LintSummary

    /**
     * Observe lint results for a file.
     *
     * @param uri The file URI.
     * @return Flow of lint results that updates when results change.
     */
    public fun observeResults(uri: String): Flow<List<LintResult>>

    /**
     * Observe the lint summary.
     */
    public fun observeSummary(): Flow<LintSummary>

    /**
     * Get a fix for a lint result.
     *
     * @param uri The file URI.
     * @param result The lint result.
     * @return The fix, or null if not available.
     */
    public suspend fun getFix(
        uri: String,
        result: LintResult,
    ): LintFix?

    /**
     * Apply a fix to a file.
     *
     * @param uri The file URI.
     * @param fix The fix to apply.
     * @return The new file content after applying the fix.
     */
    public suspend fun applyFix(
        uri: String,
        fix: LintFix,
    ): String

    /**
     * Get the current lint configuration.
     */
    public fun getConfiguration(): LintConfiguration

    /**
     * Update the lint configuration.
     */
    public fun setConfiguration(configuration: LintConfiguration)

    /**
     * Set severity for a category.
     */
    public fun setCategorySeverity(
        category: LintCategory,
        severity: LintSeverity,
    )

    /**
     * Set severity for a rule.
     */
    public fun setRuleSeverity(
        ruleId: LintRuleId,
        severity: LintSeverity,
    )

    /**
     * Check if lint is currently running.
     */
    public fun isLinting(): Boolean

    /**
     * Cancel the current lint operation.
     */
    public fun cancelLint()
}
