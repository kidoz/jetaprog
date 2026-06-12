package su.kidoz.jetaprog.lint.model

import kotlinx.serialization.Serializable

/**
 * Severity levels for lint issues.
 *
 * Severity determines how the issue is displayed and whether it
 * affects build status.
 */
@Serializable
public enum class LintSeverity {
    /**
     * Critical issue that should fail builds.
     * Typically indicates bugs or security vulnerabilities.
     */
    ERROR,

    /**
     * Significant issue that should be addressed.
     * Shown prominently but doesn't fail builds by default.
     */
    WARNING,

    /**
     * Informational suggestion for improvement.
     * Less prominent display, optional to address.
     */
    INFO,

    /**
     * Subtle hint for minor improvements.
     * Minimal visual impact, easy to ignore.
     */
    HINT,

    /**
     * Rule is disabled.
     * Issues are not reported.
     */
    OFF,
    ;

    /**
     * Whether this severity level is enabled (not OFF).
     */
    public val isEnabled: Boolean
        get() = this != OFF

    /**
     * Whether this severity level should fail builds.
     */
    public val failsBuild: Boolean
        get() = this == ERROR

    /**
     * Human-readable display name.
     */
    public val displayName: String
        get() =
            when (this) {
                ERROR -> "Error"
                WARNING -> "Warning"
                INFO -> "Info"
                HINT -> "Hint"
                OFF -> "Off"
            }

    public companion object {
        /**
         * Default severity for most rules.
         */
        public val DEFAULT: LintSeverity = WARNING
    }
}
