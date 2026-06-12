package su.kidoz.jetaprog.lint.state

import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintSummary

/**
 * Side effects from the lint system.
 */
public sealed interface LintEffect : Effect {
    /**
     * Lint completed for a file.
     */
    public data class FileLinted(
        val uri: String,
        val resultCount: Int,
        val durationMs: Long,
    ) : LintEffect

    /**
     * Lint completed for all files.
     */
    public data class LintCompleted(
        val summary: LintSummary,
        val filesProcessed: Int,
        val durationMs: Long,
    ) : LintEffect

    /**
     * Lint was cancelled.
     */
    public data object LintCancelled : LintEffect

    /**
     * Lint failed with an error.
     */
    public data class LintFailed(
        val error: String,
    ) : LintEffect

    /**
     * A fix was applied.
     */
    public data class FixApplied(
        val uri: String,
        val fix: LintFix,
        val newContent: String,
    ) : LintEffect

    /**
     * Multiple fixes were applied.
     */
    public data class FixesApplied(
        val uri: String,
        val fixCount: Int,
        val newContent: String,
    ) : LintEffect

    /**
     * Fix failed.
     */
    public data class FixFailed(
        val error: String,
    ) : LintEffect

    /**
     * Configuration was saved.
     */
    public data class ConfigurationSaved(
        val projectPath: String,
    ) : LintEffect

    /**
     * Configuration was loaded.
     */
    public data class ConfigurationLoaded(
        val projectPath: String,
    ) : LintEffect

    /**
     * Navigate to a location in the editor.
     */
    public data class NavigateTo(
        val uri: String,
        val line: Int,
        val column: Int,
    ) : LintEffect

    /**
     * Show an error message.
     */
    public data class ShowError(
        val message: String,
    ) : LintEffect

    /**
     * Show an info message.
     */
    public data class ShowInfo(
        val message: String,
    ) : LintEffect

    /**
     * Provider was registered.
     */
    public data class ProviderRegistered(
        val providerId: String,
        val ruleCount: Int,
    ) : LintEffect

    /**
     * Provider was unregistered.
     */
    public data class ProviderUnregistered(
        val providerId: String,
    ) : LintEffect
}
