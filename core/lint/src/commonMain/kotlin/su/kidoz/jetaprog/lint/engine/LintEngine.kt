package su.kidoz.jetaprog.lint.engine

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintFix
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRule
import su.kidoz.jetaprog.lint.model.LintRuleId

/**
 * Progress information during lint execution.
 */
public sealed interface LintProgress {
    /**
     * Lint started.
     */
    public data class Started(
        val fileCount: Int,
        val ruleCount: Int,
    ) : LintProgress

    /**
     * Started processing a file.
     */
    public data class FileStarted(
        val uri: String,
        val index: Int,
        val total: Int,
    ) : LintProgress

    /**
     * Finished processing a file.
     */
    public data class FileFinished(
        val uri: String,
        val results: List<LintResult>,
    ) : LintProgress

    /**
     * A rule encountered an error.
     */
    public data class RuleError(
        val ruleId: LintRuleId,
        val error: String,
    ) : LintProgress

    /**
     * Lint completed.
     */
    public data class Completed(
        val totalResults: Int,
        val filesProcessed: Int,
        val durationMs: Long,
    ) : LintProgress

    /**
     * Lint was cancelled.
     */
    public data object Cancelled : LintProgress
}

/**
 * Input for a lint operation.
 */
public data class LintInput(
    /**
     * The file URI.
     */
    val uri: String,
    /**
     * The language ID.
     */
    val languageId: String,
    /**
     * The file content.
     */
    val content: String,
    /**
     * Whether this lint was triggered by save.
     */
    val isOnSave: Boolean = false,
)

/**
 * Output from a lint operation on a single file.
 */
public data class LintOutput(
    /**
     * The file URI that was linted.
     */
    val uri: String,
    /**
     * The lint results found.
     */
    val results: List<LintResult>,
    /**
     * Duration of the lint operation in milliseconds.
     */
    val durationMs: Long,
)

/**
 * Engine for running lint rules on code.
 */
public interface LintEngine {
    /**
     * Register a lint rule.
     */
    public fun registerRule(rule: LintRule)

    /**
     * Unregister a lint rule.
     */
    public fun unregisterRule(ruleId: LintRuleId)

    /**
     * Get all registered rules.
     */
    public fun getRules(): List<LintRule>

    /**
     * Get a specific rule by ID.
     */
    public fun getRule(ruleId: LintRuleId): LintRule?

    /**
     * Get rules applicable to a language.
     */
    public fun getRulesForLanguage(languageId: String): List<LintRule>

    /**
     * Lint a single file.
     *
     * @param input The file to lint.
     * @param configuration The lint configuration to use.
     * @return The lint output with results.
     */
    public suspend fun lint(
        input: LintInput,
        configuration: LintConfiguration,
    ): LintOutput

    /**
     * Lint multiple files with progress reporting.
     *
     * @param inputs The files to lint.
     * @param configuration The lint configuration to use.
     * @return A flow of progress updates.
     */
    public fun lintWithProgress(
        inputs: List<LintInput>,
        configuration: LintConfiguration,
    ): Flow<LintProgress>

    /**
     * Get a fix for a specific lint result.
     *
     * @param input The file content.
     * @param result The lint result to fix.
     * @param configuration The lint configuration.
     * @return The fix, or null if no fix is available.
     */
    public suspend fun getFix(
        input: LintInput,
        result: LintResult,
        configuration: LintConfiguration,
    ): LintFix?

    /**
     * Apply a fix to content.
     *
     * @param content The current content.
     * @param fix The fix to apply.
     * @return The modified content.
     */
    public fun applyFix(
        content: String,
        fix: LintFix,
    ): String
}
