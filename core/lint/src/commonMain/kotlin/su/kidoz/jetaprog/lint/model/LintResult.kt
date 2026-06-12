package su.kidoz.jetaprog.lint.model

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Result of a lint check - represents a single issue found in the code.
 */
@Serializable
public data class LintResult(
    /**
     * The rule that produced this result.
     */
    val ruleId: LintRuleId,
    /**
     * Description of the issue.
     */
    val message: String,
    /**
     * The code range where the issue was found.
     */
    val range: TextRange,
    /**
     * Severity of the issue.
     */
    val severity: LintSeverity,
    /**
     * Category of the issue.
     */
    val category: LintCategory,
    /**
     * Related locations that provide additional context.
     */
    val relatedLocations: List<LintRelatedLocation> = emptyList(),
    /**
     * Additional data for the issue (for fixes or reporting).
     */
    val data: Map<String, String> = emptyMap(),
)

/**
 * A related location referenced by a lint result.
 */
@Serializable
public data class LintRelatedLocation(
    /**
     * The file URI.
     */
    val uri: String,
    /**
     * The range in the file.
     */
    val range: TextRange,
    /**
     * Description of why this location is relevant.
     */
    val message: String,
)

/**
 * Summary of lint results for a file or project.
 */
@Serializable
public data class LintSummary(
    /**
     * Total number of issues found.
     */
    val totalIssues: Int,
    /**
     * Number of errors.
     */
    val errorCount: Int,
    /**
     * Number of warnings.
     */
    val warningCount: Int,
    /**
     * Number of info-level issues.
     */
    val infoCount: Int,
    /**
     * Number of hints.
     */
    val hintCount: Int,
    /**
     * Issues grouped by category.
     */
    val byCategory: Map<LintCategory, Int>,
    /**
     * Issues grouped by rule.
     */
    val byRule: Map<LintRuleId, Int>,
    /**
     * Number of issues that have available fixes.
     */
    val fixableCount: Int,
) {
    public companion object {
        /**
         * Empty summary with no issues.
         */
        public val EMPTY: LintSummary =
            LintSummary(
                totalIssues = 0,
                errorCount = 0,
                warningCount = 0,
                infoCount = 0,
                hintCount = 0,
                byCategory = emptyMap(),
                byRule = emptyMap(),
                fixableCount = 0,
            )

        /**
         * Creates a summary from a list of lint results.
         *
         * @param results The lint results to summarize.
         * @param fixableRules Set of rule IDs that have fixes available.
         * @return The computed summary.
         */
        public fun from(
            results: List<LintResult>,
            fixableRules: Set<LintRuleId> = emptySet(),
        ): LintSummary {
            if (results.isEmpty()) return EMPTY

            var errorCount = 0
            var warningCount = 0
            var infoCount = 0
            var hintCount = 0
            var fixableCount = 0

            val byCategory = mutableMapOf<LintCategory, Int>()
            val byRule = mutableMapOf<LintRuleId, Int>()

            for (result in results) {
                when (result.severity) {
                    LintSeverity.ERROR -> {
                        errorCount++
                    }

                    LintSeverity.WARNING -> {
                        warningCount++
                    }

                    LintSeverity.INFO -> {
                        infoCount++
                    }

                    LintSeverity.HINT -> {
                        hintCount++
                    }

                    LintSeverity.OFF -> { /* should not happen */ }
                }

                byCategory[result.category] = (byCategory[result.category] ?: 0) + 1
                byRule[result.ruleId] = (byRule[result.ruleId] ?: 0) + 1

                if (result.ruleId in fixableRules) {
                    fixableCount++
                }
            }

            return LintSummary(
                totalIssues = results.size,
                errorCount = errorCount,
                warningCount = warningCount,
                infoCount = infoCount,
                hintCount = hintCount,
                byCategory = byCategory,
                byRule = byRule,
                fixableCount = fixableCount,
            )
        }
    }
}
