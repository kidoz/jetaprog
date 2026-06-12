package su.kidoz.jetaprog.lint.config

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity

/**
 * Override configuration for a specific rule.
 */
@Serializable
public data class RuleOverride(
    /**
     * The rule ID to override.
     */
    val ruleId: LintRuleId,
    /**
     * The severity to use for this rule.
     */
    val severity: LintSeverity,
)

/**
 * Configuration for the lint system.
 */
@Serializable
public data class LintConfiguration(
    /**
     * Whether linting is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Whether to lint when files are saved.
     */
    val lintOnSave: Boolean = true,
    /**
     * Whether to lint while typing.
     */
    val lintOnType: Boolean = false,
    /**
     * Debounce delay for lint-on-type in milliseconds.
     */
    val lintOnTypeDelayMs: Long = 500,
    /**
     * Severity overrides for categories.
     */
    val categoryOverrides: Map<LintCategory, LintSeverity> = emptyMap(),
    /**
     * Overrides for specific rules.
     */
    val ruleOverrides: List<RuleOverride> = emptyList(),
    /**
     * Glob patterns for files/directories to exclude.
     */
    val excludePatterns: List<String> = DEFAULT_EXCLUDE_PATTERNS,
    /**
     * Maximum number of issues to report per file.
     */
    val maxIssuesPerFile: Int = 100,
    /**
     * Maximum number of issues to report per project.
     */
    val maxIssuesTotal: Int = 1000,
    /**
     * Whether to show inline hints in the editor.
     */
    val showInlineHints: Boolean = true,
    /**
     * Whether to show the fix suggestions.
     */
    val showQuickFixes: Boolean = true,
    /**
     * Enabled rule categories.
     */
    val enabledCategories: Set<LintCategory> = LintCategory.entries.toSet(),
) {
    /**
     * Checks if a category is enabled.
     */
    public fun isCategoryEnabled(category: LintCategory): Boolean {
        if (category !in enabledCategories) return false
        val override = categoryOverrides[category]
        return override?.isEnabled ?: true
    }

    /**
     * Gets the effective severity for a rule.
     */
    public fun getRuleSeverity(
        ruleId: LintRuleId,
        category: LintCategory,
        defaultSeverity: LintSeverity,
    ): LintSeverity {
        // Check rule override first
        val ruleOverride = ruleOverrides.find { it.ruleId == ruleId }
        if (ruleOverride != null) return ruleOverride.severity

        // Check category override
        val categoryOverride = categoryOverrides[category]
        if (categoryOverride != null) return categoryOverride

        return defaultSeverity
    }

    /**
     * Checks if a file path should be excluded from linting.
     */
    public fun isExcluded(filePath: String): Boolean =
        excludePatterns.any { pattern ->
            matchGlobPattern(pattern, filePath)
        }

    public companion object {
        /**
         * Default patterns to exclude from linting.
         */
        public val DEFAULT_EXCLUDE_PATTERNS: List<String> =
            listOf(
                "**/build/**",
                "**/node_modules/**",
                "**/.git/**",
                "**/.idea/**",
                "**/.gradle/**",
                "**/target/**",
                "**/__pycache__/**",
                "**/*.min.js",
                "**/*.min.css",
            )

        /**
         * Default configuration.
         */
        public val DEFAULT: LintConfiguration = LintConfiguration()

        /**
         * Simple glob pattern matching.
         */
        private fun matchGlobPattern(
            pattern: String,
            path: String,
        ): Boolean {
            val regexPattern =
                pattern
                    .replace(".", "\\.")
                    .replace("**", "<<<DOUBLESTAR>>>")
                    .replace("*", "[^/]*")
                    .replace("<<<DOUBLESTAR>>>", ".*")
                    .replace("?", ".")

            return Regex(regexPattern).containsMatchIn(path)
        }
    }
}

/**
 * Stored format for lint configuration (for JSON persistence).
 */
@Serializable
public data class LintConfigurationData(
    val version: Int = 1,
    val enabled: Boolean = true,
    val lintOnSave: Boolean = true,
    val lintOnType: Boolean = false,
    val lintOnTypeDelayMs: Long = 500,
    val categoryOverrides: Map<String, String> = emptyMap(),
    val ruleOverrides: List<RuleOverrideData> = emptyList(),
    val excludePatterns: List<String> = LintConfiguration.DEFAULT_EXCLUDE_PATTERNS,
    val maxIssuesPerFile: Int = 100,
    val maxIssuesTotal: Int = 1000,
    val showInlineHints: Boolean = true,
    val showQuickFixes: Boolean = true,
    val enabledCategories: List<String>? = null,
) {
    /**
     * Converts to runtime configuration.
     */
    public fun toConfiguration(): LintConfiguration {
        val categoryMap =
            categoryOverrides
                .mapNotNull { (key, value) ->
                    val category = LintCategory.entries.find { it.name.equals(key, ignoreCase = true) }
                    val severity = LintSeverity.entries.find { it.name.equals(value, ignoreCase = true) }
                    if (category != null && severity != null) category to severity else null
                }.toMap()

        val ruleList =
            ruleOverrides.mapNotNull { data ->
                val severity = LintSeverity.entries.find { it.name.equals(data.severity, ignoreCase = true) }
                if (severity != null) {
                    RuleOverride(LintRuleId(data.ruleId), severity)
                } else {
                    null
                }
            }

        val categories =
            enabledCategories
                ?.mapNotNull { name ->
                    LintCategory.entries.find { it.name.equals(name, ignoreCase = true) }
                }?.toSet() ?: LintCategory.entries.toSet()

        return LintConfiguration(
            enabled = enabled,
            lintOnSave = lintOnSave,
            lintOnType = lintOnType,
            lintOnTypeDelayMs = lintOnTypeDelayMs,
            categoryOverrides = categoryMap,
            ruleOverrides = ruleList,
            excludePatterns = excludePatterns,
            maxIssuesPerFile = maxIssuesPerFile,
            maxIssuesTotal = maxIssuesTotal,
            showInlineHints = showInlineHints,
            showQuickFixes = showQuickFixes,
            enabledCategories = categories,
        )
    }

    public companion object {
        /**
         * Creates storage data from runtime configuration.
         */
        public fun from(config: LintConfiguration): LintConfigurationData =
            LintConfigurationData(
                enabled = config.enabled,
                lintOnSave = config.lintOnSave,
                lintOnType = config.lintOnType,
                lintOnTypeDelayMs = config.lintOnTypeDelayMs,
                categoryOverrides =
                    config.categoryOverrides
                        .map { (k, v) ->
                            k.name.lowercase() to v.name.lowercase()
                        }.toMap(),
                ruleOverrides =
                    config.ruleOverrides.map { override ->
                        RuleOverrideData(override.ruleId.value, override.severity.name.lowercase())
                    },
                excludePatterns = config.excludePatterns,
                maxIssuesPerFile = config.maxIssuesPerFile,
                maxIssuesTotal = config.maxIssuesTotal,
                showInlineHints = config.showInlineHints,
                showQuickFixes = config.showQuickFixes,
                enabledCategories = config.enabledCategories.map { it.name.lowercase() },
            )
    }
}

/**
 * Stored format for rule override.
 */
@Serializable
public data class RuleOverrideData(
    val ruleId: String,
    val severity: String,
)
