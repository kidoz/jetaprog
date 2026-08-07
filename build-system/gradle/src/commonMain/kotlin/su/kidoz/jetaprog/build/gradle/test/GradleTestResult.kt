package su.kidoz.jetaprog.build.gradle.test

/** Outcome of an individual Gradle test case. */
public enum class GradleTestStatus {
    PASSED,
    FAILED,
    SKIPPED,
}

/** A test case read from a Gradle XML test report. */
public data class GradleTestCase(
    public val suiteName: String,
    public val name: String,
    public val status: GradleTestStatus,
    public val durationMs: Long,
    public val failureMessage: String? = null,
    public val failureDetails: String? = null,
)

/** A test suite produced by one Gradle test task. */
public data class GradleTestSuite(
    public val name: String,
    public val modulePath: String,
    public val taskPath: String,
    public val reportPath: String,
    public val cases: List<GradleTestCase>,
)

/** Aggregated results for the most recently completed Gradle invocation. */
public data class GradleTestRun(
    public val suites: List<GradleTestSuite>,
) {
    /** Total number of test cases. */
    public val totalCount: Int = suites.sumOf { suite -> suite.cases.size }

    /** Number of failed test cases. */
    public val failedCount: Int =
        suites.sumOf { suite -> suite.cases.count { it.status == GradleTestStatus.FAILED } }

    /** Number of skipped test cases. */
    public val skippedCount: Int =
        suites.sumOf { suite -> suite.cases.count { it.status == GradleTestStatus.SKIPPED } }

    /** Number of passed test cases. */
    public val passedCount: Int = totalCount - failedCount - skippedCount

    /** Total duration reported by all test cases. */
    public val durationMs: Long = suites.sumOf { suite -> suite.cases.sumOf(GradleTestCase::durationMs) }
}

/** Loads structured test results emitted by Gradle test tasks. */
public interface GradleTestReportLoader {
    /**
     * Loads reports relevant to [taskPath]. Recent reports are preferred, with
     * existing task reports used when Gradle considered tests up to date.
     */
    public suspend fun load(
        projectRoot: String,
        taskPath: String,
        startedAtMillis: Long,
    ): Result<GradleTestRun>
}
