package su.kidoz.jetaprog.configuration.execution

import kotlinx.serialization.Serializable

/**
 * Output from an execution session.
 */
@Serializable
public sealed interface ExecutionOutput {
    /**
     * Standard output line.
     */
    @Serializable
    public data class Stdout(
        val line: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Standard error line.
     */
    @Serializable
    public data class Stderr(
        val line: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Status message from the orchestrator.
     */
    @Serializable
    public data class Status(
        val message: String,
        val type: StatusType = StatusType.INFO,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Before-launch task started.
     */
    @Serializable
    public data class TaskStarted(
        val taskIndex: Int,
        val taskDescription: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Before-launch task completed.
     */
    @Serializable
    public data class TaskCompleted(
        val taskIndex: Int,
        val taskDescription: String,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Main execution started.
     */
    @Serializable
    public data class MainExecutionStarted(
        val configurationName: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput

    /**
     * Execution finished.
     */
    @Serializable
    public data class ExecutionFinished(
        val result: ExecutionResult,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ExecutionOutput
}

/**
 * Type of status message.
 */
@Serializable
public enum class StatusType {
    INFO,
    WARNING,
    ERROR,
}
