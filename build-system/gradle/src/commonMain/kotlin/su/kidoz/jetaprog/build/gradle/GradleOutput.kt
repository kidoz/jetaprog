package su.kidoz.jetaprog.build.gradle

/**
 * Output from a Gradle task execution.
 */
public sealed interface GradleOutput {
    /** Standard output line. */
    public data class Stdout(
        val line: String,
    ) : GradleOutput

    /** Standard error line. */
    public data class Stderr(
        val line: String,
    ) : GradleOutput

    /** Task started executing. */
    public data class TaskStarted(
        val taskPath: String,
    ) : GradleOutput

    /** Task execution completed. */
    public data class TaskCompleted(
        val taskPath: String,
        val outcome: TaskOutcome,
    ) : GradleOutput

    /** Build completed. */
    public data class BuildFinished(
        val success: Boolean,
        val exitCode: Int,
    ) : GradleOutput
}

/**
 * Outcome of a Gradle task execution.
 */
public enum class TaskOutcome {
    /** Task executed successfully. */
    SUCCESS,

    /** Task failed during execution. */
    FAILED,

    /** Task was up-to-date (no work needed). */
    UP_TO_DATE,

    /** Task was skipped. */
    SKIPPED,

    /** Task execution was cancelled. */
    CANCELLED,

    /** Task has no source (nothing to process). */
    NO_SOURCE,

    /** Task was retrieved from build cache. */
    FROM_CACHE,
}
