package su.kidoz.jetaprog.configuration.execution

import kotlinx.serialization.Serializable

/**
 * Result of executing a before-launch task.
 */
@Serializable
public sealed interface TaskResult {
    /**
     * Task completed successfully.
     */
    @Serializable
    public data object Success : TaskResult

    /**
     * Task failed with an error message.
     */
    @Serializable
    public data class Failure(
        val message: String,
        val exitCode: Int = -1,
    ) : TaskResult
}

/**
 * Result of the overall execution.
 */
@Serializable
public sealed interface ExecutionResult {
    /**
     * Execution completed successfully.
     */
    @Serializable
    public data class Success(
        val exitCode: Int = 0,
    ) : ExecutionResult

    /**
     * Execution failed.
     */
    @Serializable
    public data class Failure(
        val message: String,
        val exitCode: Int = -1,
        val phase: ExecutionPhase = ExecutionPhase.MAIN,
    ) : ExecutionResult

    /**
     * Execution was cancelled.
     */
    @Serializable
    public data object Cancelled : ExecutionResult
}

/**
 * Phase of execution where failure occurred.
 */
@Serializable
public enum class ExecutionPhase {
    /** Failure during before-launch tasks. */
    BEFORE_LAUNCH,

    /** Failure during main execution. */
    MAIN,
}
