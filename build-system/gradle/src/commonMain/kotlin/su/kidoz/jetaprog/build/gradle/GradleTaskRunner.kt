package su.kidoz.jetaprog.build.gradle

import kotlinx.coroutines.flow.Flow

/**
 * Runs Gradle tasks in a project.
 */
public interface GradleTaskRunner {
    /**
     * Runs a Gradle task.
     *
     * @param project The Gradle project to run the task in.
     * @param taskPath The task path (e.g., "build", ":app:desktop:run").
     * @param args Additional arguments to pass to Gradle.
     * @return A flow of output from the task execution.
     */
    public suspend fun runTask(
        project: GradleProject,
        taskPath: String,
        args: List<String> = emptyList(),
    ): Result<Flow<GradleOutput>>

    /**
     * Cancels the currently running task.
     */
    public fun cancelTask()

    /**
     * Whether a task is currently running.
     */
    public val isRunning: Boolean

    /**
     * Discovers available tasks in a Gradle project.
     *
     * @param project The Gradle project to discover tasks in.
     * @return The project with discovered tasks.
     */
    public suspend fun discoverTasks(project: GradleProject): Result<GradleProject>
}
