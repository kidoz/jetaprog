package su.kidoz.jetaprog.lint.model

import kotlinx.serialization.Serializable

/**
 * Categories for lint rules.
 *
 * Rules are grouped by category to help developers understand the nature
 * of the issue and prioritize fixes appropriately.
 */
@Serializable
public enum class LintCategory {
    /**
     * Bugs, logic errors, and incorrect behavior.
     * These issues can cause runtime failures or incorrect results.
     */
    CORRECTNESS,

    /**
     * Security vulnerabilities and unsafe patterns.
     * Includes OWASP and CWE-style security issues.
     */
    SECURITY,

    /**
     * Inefficient patterns that impact performance.
     * Includes memory issues, slow algorithms, and resource leaks.
     */
    PERFORMANCE,

    /**
     * Code style and formatting issues.
     * Includes naming conventions, indentation, and code layout.
     */
    STYLE,

    /**
     * Complex code that is hard to understand.
     * Includes high cyclomatic complexity and deep nesting.
     */
    COMPLEXITY,

    /**
     * Code smells and maintainability issues.
     * Includes duplication, large files, and poor structure.
     */
    MAINTAINABILITY,

    /**
     * Opportunities to use newer language features.
     * Suggests modern syntax and API replacements.
     */
    MODERNIZE,

    /**
     * Usage of deprecated APIs or patterns.
     * Warns about features scheduled for removal.
     */
    DEPRECATED,
    ;

    /**
     * Human-readable display name for the category.
     */
    public val displayName: String
        get() =
            when (this) {
                CORRECTNESS -> "Correctness"
                SECURITY -> "Security"
                PERFORMANCE -> "Performance"
                STYLE -> "Style"
                COMPLEXITY -> "Complexity"
                MAINTAINABILITY -> "Maintainability"
                MODERNIZE -> "Modernize"
                DEPRECATED -> "Deprecated"
            }

    /**
     * Description of what this category covers.
     */
    public val description: String
        get() =
            when (this) {
                CORRECTNESS -> "Bugs, logic errors, and incorrect behavior"
                SECURITY -> "Security vulnerabilities and unsafe patterns"
                PERFORMANCE -> "Inefficient patterns that impact performance"
                STYLE -> "Code style and formatting issues"
                COMPLEXITY -> "Complex code that is hard to understand"
                MAINTAINABILITY -> "Code smells and maintainability issues"
                MODERNIZE -> "Opportunities to use newer language features"
                DEPRECATED -> "Usage of deprecated APIs or patterns"
            }
}
