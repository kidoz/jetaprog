package su.kidoz.jetaprog.lint.state

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.mvi.State
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.model.LintSummary

/**
 * Status of a lint operation.
 */
@Serializable
public enum class LintStatus {
    /**
     * Idle - no lint operation running.
     */
    IDLE,

    /**
     * Currently running lint.
     */
    RUNNING,

    /**
     * Lint completed successfully.
     */
    COMPLETED,

    /**
     * Lint was cancelled.
     */
    CANCELLED,

    /**
     * Lint failed with an error.
     */
    ERROR,
}

/**
 * Lint results for a single file.
 */
@Serializable
public data class FileLintResults(
    /**
     * The file URI.
     */
    val uri: String,
    /**
     * Lint results for this file.
     */
    val results: List<LintResult>,
    /**
     * When the lint was last run.
     */
    val timestamp: Long,
    /**
     * Whether the content has changed since last lint.
     */
    val isStale: Boolean = false,
)

/**
 * State for the lint system.
 */
@Serializable
public data class LintState(
    /**
     * Current lint configuration.
     */
    val configuration: LintConfiguration = LintConfiguration.DEFAULT,
    /**
     * Status of the lint operation.
     */
    val status: LintStatus = LintStatus.IDLE,
    /**
     * Progress percentage (0-100).
     */
    val progress: Int = 0,
    /**
     * Currently processing file URI.
     */
    val currentFile: String? = null,
    /**
     * Results by file URI.
     */
    val resultsByFile: Map<String, FileLintResults> = emptyMap(),
    /**
     * Summary of all lint results.
     */
    val summary: LintSummary = LintSummary.EMPTY,
    /**
     * Number of registered rules.
     */
    val ruleCount: Int = 0,
    /**
     * Number of registered providers.
     */
    val providerCount: Int = 0,
    /**
     * Error message if lint failed.
     */
    val error: String? = null,
    /**
     * Whether the lint panel is visible.
     */
    val isPanelVisible: Boolean = false,
    /**
     * Filter for displayed results.
     */
    val filter: LintFilter = LintFilter(),
) : State {
    /**
     * All lint results across all files.
     */
    public val allResults: List<LintResult>
        get() = resultsByFile.values.flatMap { it.results }

    /**
     * Filtered results based on current filter settings.
     */
    public val filteredResults: List<LintResult>
        get() =
            allResults.filter { result ->
                filter.matches(result)
            }

    /**
     * Results for a specific file.
     */
    public fun getResultsForFile(uri: String): List<LintResult> = resultsByFile[uri]?.results ?: emptyList()

    /**
     * Whether there are any errors.
     */
    public val hasErrors: Boolean
        get() = summary.errorCount > 0

    /**
     * Whether lint is currently running.
     */
    public val isRunning: Boolean
        get() = status == LintStatus.RUNNING
}

/**
 * Filter for lint results.
 */
@Serializable
public data class LintFilter(
    /**
     * Severities to include.
     */
    val severities: Set<LintSeverity> =
        setOf(
            LintSeverity.ERROR,
            LintSeverity.WARNING,
            LintSeverity.INFO,
            LintSeverity.HINT,
        ),
    /**
     * Categories to include (empty means all).
     */
    val categories: Set<LintCategory> = emptySet(),
    /**
     * Specific rules to include (empty means all).
     */
    val ruleIds: Set<LintRuleId> = emptySet(),
    /**
     * Text search query.
     */
    val searchQuery: String = "",
    /**
     * File path filter (glob pattern).
     */
    val filePattern: String = "",
) {
    /**
     * Check if a result matches this filter.
     */
    public fun matches(result: LintResult): Boolean {
        if (result.severity !in severities) return false
        if (categories.isNotEmpty() && result.category !in categories) return false
        if (ruleIds.isNotEmpty() && result.ruleId !in ruleIds) return false
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            if (!result.message.lowercase().contains(query) &&
                !result.ruleId.value
                    .lowercase()
                    .contains(query)
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Whether any filter is active.
     */
    public val isActive: Boolean
        get() =
            severities.size < 4 ||
                categories.isNotEmpty() ||
                ruleIds.isNotEmpty() ||
                searchQuery.isNotBlank() ||
                filePattern.isNotBlank()
}
