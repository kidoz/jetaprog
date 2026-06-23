package su.kidoz.jetaprog.build.gradle

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * A diagnostic (error/warning) from Gradle build output.
 */
public data class GradleDiagnostic(
    /** Severity of the diagnostic. */
    val severity: GradleDiagnosticSeverity,
    /** Path to the source file. */
    val filePath: String,
    /** Position in the file where the issue occurred. */
    val position: TextPosition,
    /** Diagnostic message. */
    val message: String,
    /** Error code if available (e.g., Kotlin compiler error code). */
    val code: String? = null,
)

/**
 * Severity level of a Gradle diagnostic.
 */
public enum class GradleDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

/**
 * Parser for extracting diagnostics from Gradle/Kotlin compiler output.
 */
public object GradleDiagnosticsParser {
    // Kotlin compiler error pattern: e: file:///path/to/File.kt:10:5 message
    private val kotlinErrorPattern =
        Regex(
            """^([ew]):\s*(?:file://)?(.+?):(\d+):(\d+)\s+(.+)$""",
        )

    // Gradle task failure pattern: > Task :module:task FAILED
    private val taskFailedPattern =
        Regex(
            """^>\s*Task\s+([:A-Za-z0-9_.-]+)\s+FAILED$""",
        )

    /**
     * Parses a single line of Gradle output for diagnostics.
     */
    public fun parseLine(line: String): GradleDiagnostic? {
        kotlinErrorPattern.matchEntire(line)?.let { match ->
            val (severityChar, filePath, lineNum, colNum, message) = match.destructured
            val severity =
                when (severityChar) {
                    "e" -> GradleDiagnosticSeverity.ERROR
                    "w" -> GradleDiagnosticSeverity.WARNING
                    else -> GradleDiagnosticSeverity.INFO
                }
            return GradleDiagnostic(
                severity = severity,
                filePath = filePath,
                position =
                    TextPosition(
                        line = lineNum.toIntOrNull()?.minus(1) ?: 0,
                        column = colNum.toIntOrNull()?.minus(1) ?: 0,
                    ),
                message = message,
            )
        }
        return null
    }

    /**
     * Parses multiple lines of output and extracts all diagnostics.
     */
    public fun parseOutput(output: List<String>): List<GradleDiagnostic> = output.mapNotNull { parseLine(it) }

    /**
     * Checks if a line indicates a task failure.
     */
    public fun isTaskFailure(line: String): Boolean = taskFailedPattern.matches(line.trim())

    /**
     * Extracts the failed task path from a line.
     */
    public fun extractFailedTask(line: String): String? =
        taskFailedPattern.matchEntire(line.trim())?.groupValues?.get(1)
}
