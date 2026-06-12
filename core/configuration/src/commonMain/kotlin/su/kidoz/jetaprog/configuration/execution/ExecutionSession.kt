package su.kidoz.jetaprog.configuration.execution

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.RunConfiguration

/**
 * State of an execution session.
 */
public enum class ExecutionState {
    /** Session created but not started. */
    PENDING,

    /** Running before-launch tasks. */
    RUNNING_TASKS,

    /** Running the main configuration. */
    RUNNING,

    /** Session was stopped by user. */
    STOPPED,

    /** Session failed. */
    FAILED,

    /** Session completed successfully. */
    COMPLETED,
}

/**
 * Represents an active execution session.
 *
 * Tracks the state of a running configuration including before-launch tasks
 * and the main execution.
 */
public class ExecutionSession(
    /** Unique session identifier. */
    public val id: String,
    /** The configuration being executed. */
    public val configuration: RunConfiguration,
) : Disposable {
    private val _state = MutableStateFlow(ExecutionState.PENDING)
    private val _output = MutableSharedFlow<ExecutionOutput>(replay = 100)
    private val _result = MutableStateFlow<ExecutionResult?>(null)

    @Volatile
    private var stopRequested = false

    @Volatile
    private var disposed = false

    /**
     * Current state of the execution.
     */
    public val state: StateFlow<ExecutionState> = _state.asStateFlow()

    /**
     * Flow of output from the execution.
     */
    public val output: Flow<ExecutionOutput> = _output.asSharedFlow()

    /**
     * The final result of the execution, null if still running.
     */
    public val result: StateFlow<ExecutionResult?> = _result.asStateFlow()

    /**
     * Whether a stop has been requested.
     */
    public val isStopRequested: Boolean get() = stopRequested

    /**
     * Whether the session has completed (successfully or with failure).
     */
    public val isCompleted: Boolean
        get() =
            _state.value in
                setOf(
                    ExecutionState.STOPPED,
                    ExecutionState.FAILED,
                    ExecutionState.COMPLETED,
                )

    /**
     * Request to stop the execution.
     */
    public fun stop() {
        stopRequested = true
    }

    /**
     * Emit output to the session.
     * @param executionOutput The output to emit.
     */
    internal suspend fun emitOutput(executionOutput: ExecutionOutput) {
        if (!disposed) {
            _output.emit(executionOutput)
        }
    }

    /**
     * Update the session state.
     * @param newState The new state.
     */
    internal fun updateState(newState: ExecutionState) {
        if (!disposed) {
            _state.value = newState
        }
    }

    /**
     * Set the final result.
     * @param executionResult The execution result.
     */
    internal fun setResult(executionResult: ExecutionResult) {
        if (!disposed) {
            _result.value = executionResult
            _state.value =
                when (executionResult) {
                    is ExecutionResult.Success -> ExecutionState.COMPLETED
                    is ExecutionResult.Failure -> ExecutionState.FAILED
                    is ExecutionResult.Cancelled -> ExecutionState.STOPPED
                }
        }
    }

    override fun dispose() {
        disposed = true
        stopRequested = true
    }

    public companion object {
        /**
         * Generate a unique session ID.
         */
        public fun generateId(): String {
            val chars = "0123456789abcdef"
            return (1..16).map { chars.random() }.joinToString("")
        }
    }
}
