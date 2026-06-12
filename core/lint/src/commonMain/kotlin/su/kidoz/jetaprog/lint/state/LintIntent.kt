package su.kidoz.jetaprog.lint.state

import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.LintProvider

/**
 * Intents for the lint system.
 */
public sealed interface LintIntent : Intent {
    /**
     * Initialize the lint system for a project.
     */
    public data class Initialize(
        val projectPath: String,
    ) : LintIntent

    /**
     * Lint a single file.
     */
    public data class LintFile(
        val uri: String,
        val languageId: String,
        val content: String,
        val isOnSave: Boolean = false,
    ) : LintIntent

    /**
     * Lint multiple files.
     */
    public data class LintFiles(
        val files: List<FileToLint>,
    ) : LintIntent {
        public data class FileToLint(
            val uri: String,
            val languageId: String,
            val content: String,
        )
    }

    /**
     * Lint the entire project.
     */
    public data class LintProject(
        val projectPath: String,
    ) : LintIntent

    /**
     * Cancel the current lint operation.
     */
    public data object CancelLint : LintIntent

    /**
     * Clear results for a specific file.
     */
    public data class ClearFileResults(
        val uri: String,
    ) : LintIntent

    /**
     * Clear all lint results.
     */
    public data object ClearAllResults : LintIntent

    /**
     * Mark file results as stale (content changed).
     */
    public data class MarkFileStale(
        val uri: String,
    ) : LintIntent

    /**
     * Apply a fix for a lint result.
     */
    public data class ApplyFix(
        val uri: String,
        val result: LintResult,
    ) : LintIntent

    /**
     * Apply all available fixes for a file.
     */
    public data class ApplyAllFixes(
        val uri: String,
        val safeOnly: Boolean = true,
    ) : LintIntent

    /**
     * Update lint configuration.
     */
    public data class UpdateConfiguration(
        val configuration: LintConfiguration,
    ) : LintIntent

    /**
     * Save configuration to file.
     */
    public data class SaveConfiguration(
        val projectPath: String,
    ) : LintIntent

    /**
     * Reload configuration from file.
     */
    public data class ReloadConfiguration(
        val projectPath: String,
    ) : LintIntent

    /**
     * Set severity for a category.
     */
    public data class SetCategorySeverity(
        val category: LintCategory,
        val severity: LintSeverity,
    ) : LintIntent

    /**
     * Set severity for a specific rule.
     */
    public data class SetRuleSeverity(
        val ruleId: LintRuleId,
        val severity: LintSeverity,
    ) : LintIntent

    /**
     * Register a lint provider.
     */
    public data class RegisterProvider(
        val provider: LintProvider,
    ) : LintIntent

    /**
     * Unregister a lint provider.
     */
    public data class UnregisterProvider(
        val providerId: String,
    ) : LintIntent

    /**
     * Update the filter settings.
     */
    public data class UpdateFilter(
        val filter: LintFilter,
    ) : LintIntent

    /**
     * Toggle panel visibility.
     */
    public data object TogglePanel : LintIntent

    /**
     * Show the lint panel.
     */
    public data object ShowPanel : LintIntent

    /**
     * Hide the lint panel.
     */
    public data object HidePanel : LintIntent

    /**
     * Clear the error state.
     */
    public data object ClearError : LintIntent

    /**
     * Navigate to a lint result location.
     */
    public data class NavigateToResult(
        val result: LintResult,
    ) : LintIntent
}
