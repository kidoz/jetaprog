package su.kidoz.jetaprog.build.meson

/**
 * Output from a Meson build execution.
 */
public sealed interface MesonOutput {
    /** Standard output line. */
    public data class Stdout(
        val line: String,
    ) : MesonOutput

    /** Standard error line. */
    public data class Stderr(
        val line: String,
    ) : MesonOutput

    /** Compilation started for a target. */
    public data class CompileStarted(
        val target: String,
    ) : MesonOutput

    /** Compilation progress update. */
    public data class CompileProgress(
        val current: Int,
        val total: Int,
        val target: String,
    ) : MesonOutput

    /** Compilation completed for a target. */
    public data class CompileCompleted(
        val target: String,
        val success: Boolean,
    ) : MesonOutput

    /** Test started. */
    public data class TestStarted(
        val testName: String,
    ) : MesonOutput

    /** Test completed. */
    public data class TestCompleted(
        val testName: String,
        val outcome: TestOutcome,
        val duration: Long? = null,
    ) : MesonOutput

    /** Build finished. */
    public data class BuildFinished(
        val success: Boolean,
        val exitCode: Int,
    ) : MesonOutput
}

/**
 * Outcome of a test execution.
 */
public enum class TestOutcome {
    /** Test passed. */
    OK,

    /** Test failed. */
    FAIL,

    /** Test was skipped. */
    SKIP,

    /** Test timed out. */
    TIMEOUT,

    /** Test error (not a failure, but something went wrong). */
    ERROR,
}
