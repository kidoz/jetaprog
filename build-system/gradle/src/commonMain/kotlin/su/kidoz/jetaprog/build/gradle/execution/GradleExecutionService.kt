package su.kidoz.jetaprog.build.gradle.execution

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.build.gradle.test.GradleTestRun

/** Event emitted while executing a Gradle task. */
public sealed interface GradleExecutionEvent {
    /** Console or lifecycle output from Gradle. */
    public data class Output(
        public val value: GradleOutput,
    ) : GradleExecutionEvent

    /** Structured test results discovered after task completion. */
    public data class TestResults(
        public val value: GradleTestRun,
    ) : GradleExecutionEvent

    /** Non-fatal failure while loading structured test results. */
    public data class TestReportFailure(
        public val message: String,
    ) : GradleExecutionEvent
}

/**
 * Project-scoped boundary for Gradle task execution, task discovery, model
 * import, and test-report collection.
 *
 * Operations are serialized. [cancel] stops the active operation regardless of
 * whether it is backed by a Gradle process or the Tooling API.
 */
public interface GradleExecutionService {
    /** Whether this service currently owns an active Gradle operation. */
    public val isRunning: Boolean

    /** Executes [taskPath] and emits output followed by any matching test results. */
    public fun runTask(
        project: GradleProject,
        taskPath: String,
        args: List<String> = emptyList(),
    ): Flow<GradleExecutionEvent>

    /** Discovers all tasks available in [project]. */
    public suspend fun discoverTasks(project: GradleProject): Result<GradleProject>

    /** Imports the IDE model for the build rooted at [projectRoot]. */
    public suspend fun importModel(projectRoot: String): Result<GradleImportModel>

    /** Cancels the active Gradle operation, if any. */
    public fun cancel()
}
