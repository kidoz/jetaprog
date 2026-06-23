package su.kidoz.jetaprog.build.gradle.state

import su.kidoz.jetaprog.build.gradle.GradleDiagnostic
import su.kidoz.jetaprog.build.gradle.GradleProject

/**
 * State for the Gradle build panel.
 */
public data class GradleState(
    /** The current Gradle project. */
    val project: GradleProject? = null,
    /** Whether a task is currently running. */
    val isRunning: Boolean = false,
    /** The currently running task path. */
    val runningTask: String? = null,
    /** Build output lines. */
    val output: List<OutputLine> = emptyList(),
    /** Diagnostics extracted from build output. */
    val diagnostics: List<GradleDiagnostic> = emptyList(),
    /** The last build result. */
    val lastBuildResult: BuildResult? = null,
    /** Whether the panel is visible. */
    val isVisible: Boolean = false,
    /** Favorite/pinned tasks for quick access. */
    val favoriteTasks: List<String> = listOf(":app:desktop:run", "build", "test", "ktlintCheck", "detekt"),
)

/**
 * A line of build output.
 */
public data class OutputLine(
    val text: String,
    val type: OutputType,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Type of output line.
 */
public enum class OutputType {
    STDOUT,
    STDERR,
    INFO,
    SUCCESS,
    ERROR,
}

/**
 * Result of a build execution.
 */
public data class BuildResult(
    val success: Boolean,
    val exitCode: Int,
    val taskPath: String,
    val durationMs: Long,
    val diagnosticsCount: Int = 0,
)

/**
 * Intents for Gradle panel actions.
 */
public sealed interface GradleIntent {
    /** Initialize with a project path. */
    public data class Initialize(
        val projectPath: String,
    ) : GradleIntent

    /** Run a Gradle task. */
    public data class RunTask(
        val taskPath: String,
        val args: List<String> = emptyList(),
    ) : GradleIntent

    /** Cancel the running task. */
    public data object CancelTask : GradleIntent

    /** Clear the output. */
    public data object ClearOutput : GradleIntent

    /** Toggle panel visibility. */
    public data object ToggleVisibility : GradleIntent

    /** Refresh/discover tasks. */
    public data object RefreshTasks : GradleIntent

    /** Add a task to favorites. */
    public data class AddFavorite(
        val taskPath: String,
    ) : GradleIntent

    /** Remove a task from favorites. */
    public data class RemoveFavorite(
        val taskPath: String,
    ) : GradleIntent
}

/**
 * Effects for Gradle panel side effects.
 */
public sealed interface GradleEffect {
    /** Build completed successfully. */
    public data class BuildSucceeded(
        val taskPath: String,
        val durationMs: Long,
    ) : GradleEffect

    /** Build failed. */
    public data class BuildFailed(
        val taskPath: String,
        val exitCode: Int,
        val diagnosticsCount: Int,
    ) : GradleEffect

    /** Navigate to a diagnostic location. */
    public data class NavigateToDiagnostic(
        val diagnostic: GradleDiagnostic,
    ) : GradleEffect
}
