package su.kidoz.jetaprog.build.cmake

/**
 * Structured output from a CMake or CTest execution.
 */
public sealed interface CMakeOutput {
    /** An unclassified standard output line. */
    public data class Stdout(
        val line: String,
    ) : CMakeOutput

    /** A standard error line. */
    public data class Stderr(
        val line: String,
    ) : CMakeOutput

    /** A command was started. */
    public data class CommandStarted(
        val command: String,
        val args: List<String>,
    ) : CMakeOutput

    /** The configure step detected a compiler or feature. */
    public data class ConfigureProgress(
        val message: String,
    ) : CMakeOutput

    /** Build progress reported as a percentage by the generator. */
    public data class BuildProgress(
        val percent: Int,
        val message: String,
    ) : CMakeOutput

    /** A translation unit is being compiled. */
    public data class Compiling(
        val target: String,
        val language: String? = null,
    ) : CMakeOutput

    /** A target is being linked. */
    public data class Linking(
        val target: String,
        val language: String? = null,
    ) : CMakeOutput

    /** A compiler diagnostic. */
    public data class Diagnostic(
        val severity: DiagnosticSeverity,
        val message: String,
        val file: String? = null,
        val line: Int? = null,
        val column: Int? = null,
    ) : CMakeOutput

    /** A CTest test case started. */
    public data class TestStarted(
        val testName: String,
    ) : CMakeOutput

    /** A CTest test case finished. */
    public data class TestCompleted(
        val testName: String,
        val outcome: CTestOutcome,
        val durationSeconds: Double? = null,
    ) : CMakeOutput

    /** The CTest run summary. */
    public data class TestSummary(
        val passed: Int,
        val failed: Int,
        val total: Int,
    ) : CMakeOutput

    /** The command finished. */
    public data class CommandFinished(
        val success: Boolean,
        val exitCode: Int,
    ) : CMakeOutput
}

/**
 * Severity of a compiler diagnostic parsed out of build output.
 */
public enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    NOTE,
}

/**
 * Outcome of a single CTest test case.
 */
public enum class CTestOutcome {
    PASSED,
    FAILED,
    TIMEOUT,
    SKIPPED,
    NOT_RUN,
}
