package su.kidoz.jetaprog.dap.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.dap.client.DapClient
import su.kidoz.jetaprog.dap.protocol.BreakpointEventBody
import su.kidoz.jetaprog.dap.protocol.ContinuedEventBody
import su.kidoz.jetaprog.dap.protocol.DapStackFrame
import su.kidoz.jetaprog.dap.protocol.DapThread
import su.kidoz.jetaprog.dap.protocol.DapVariable
import su.kidoz.jetaprog.dap.protocol.ExitedEventBody
import su.kidoz.jetaprog.dap.protocol.OutputEventBody
import su.kidoz.jetaprog.dap.protocol.StoppedEventBody
import su.kidoz.jetaprog.dap.protocol.ThreadEventBody

/**
 * State of a debug session.
 */
public enum class DebugState {
    /** Session is initializing. */
    INITIALIZING,

    /** Debuggee is running. */
    RUNNING,

    /** Debuggee is paused (at breakpoint, exception, etc.). */
    PAUSED,

    /** Session is stopped/terminated. */
    STOPPED,
}

/**
 * Debug output from the debuggee.
 */
public data class DebugOutput(
    /** The category of output. */
    val category: String,
    /** The output text. */
    val output: String,
    /** Source file (if available). */
    val source: String? = null,
    /** Line number (if available). */
    val line: Int? = null,
    /** Timestamp. */
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Represents an active debug session.
 */
public class DebugSession(
    /** Unique session identifier. */
    public val id: String,
    /** The configuration being debugged. */
    public val configuration: RunConfiguration,
    /** The DAP client for this session. */
    public val client: DapClient,
    private val scope: CoroutineScope,
) : Disposable {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(DebugState.INITIALIZING)
    private val _currentThread = MutableStateFlow<Int?>(null)
    private val _threads = MutableStateFlow<List<DapThread>>(emptyList())
    private val _stackFrames = MutableStateFlow<List<DapStackFrame>>(emptyList())
    private val _selectedFrame = MutableStateFlow<DapStackFrame?>(null)
    private val _variables = MutableStateFlow<List<DapVariable>>(emptyList())
    private val _output = MutableStateFlow<List<DebugOutput>>(emptyList())
    private val _exitCode = MutableStateFlow<Int?>(null)

    private var eventJob: Job? = null
    private var disposed = false

    /**
     * Breakpoint manager for this session.
     */
    public val breakpointManager: BreakpointManager = BreakpointManager(client)

    /**
     * Current state of the debug session.
     */
    public val state: StateFlow<DebugState> = _state.asStateFlow()

    /**
     * The currently focused thread.
     */
    public val currentThread: StateFlow<Int?> = _currentThread.asStateFlow()

    /**
     * All threads in the debuggee.
     */
    public val threads: StateFlow<List<DapThread>> = _threads.asStateFlow()

    /**
     * Stack frames for the current thread.
     */
    public val stackFrames: StateFlow<List<DapStackFrame>> = _stackFrames.asStateFlow()

    /**
     * The currently selected stack frame.
     */
    public val selectedFrame: StateFlow<DapStackFrame?> = _selectedFrame.asStateFlow()

    /**
     * Variables in the current scope.
     */
    public val variables: StateFlow<List<DapVariable>> = _variables.asStateFlow()

    /**
     * Debug output.
     */
    public val output: StateFlow<List<DebugOutput>> = _output.asStateFlow()

    /**
     * Exit code of the debuggee (if exited).
     */
    public val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    /**
     * Start listening for events.
     */
    internal fun startEventListener() {
        eventJob =
            scope.launch {
                client.events.collect { event ->
                    handleEvent(event.event, event.body)
                }
            }
    }

    /**
     * Continue execution.
     */
    public suspend fun continueExecution(): Result<Unit> {
        val thread =
            _currentThread.value ?: return Result.failure(
                IllegalStateException("No current thread"),
            )
        return client.continueExecution(thread).map { }
    }

    /**
     * Step over (next line).
     */
    public suspend fun stepOver(): Result<Unit> {
        val thread =
            _currentThread.value ?: return Result.failure(
                IllegalStateException("No current thread"),
            )
        return client.next(thread)
    }

    /**
     * Step into (enter function).
     */
    public suspend fun stepInto(): Result<Unit> {
        val thread =
            _currentThread.value ?: return Result.failure(
                IllegalStateException("No current thread"),
            )
        return client.stepIn(thread)
    }

    /**
     * Step out (exit function).
     */
    public suspend fun stepOut(): Result<Unit> {
        val thread =
            _currentThread.value ?: return Result.failure(
                IllegalStateException("No current thread"),
            )
        return client.stepOut(thread)
    }

    /**
     * Pause execution.
     */
    public suspend fun pause(): Result<Unit> {
        val thread =
            _currentThread.value ?: _threads.value.firstOrNull()?.id
                ?: return Result.failure(IllegalStateException("No thread to pause"))
        return client.pause(thread)
    }

    /**
     * Stop the debug session.
     */
    public suspend fun stop(): Result<Unit> {
        _state.value = DebugState.STOPPED
        return client.disconnect(terminateDebuggee = true)
    }

    /**
     * Select a stack frame.
     */
    public suspend fun selectFrame(frame: DapStackFrame) {
        _selectedFrame.value = frame
        refreshVariables(frame.id)
    }

    /**
     * Evaluate an expression in the current context.
     */
    public suspend fun evaluate(expression: String): Result<String> {
        val frameId = _selectedFrame.value?.id
        return client.evaluate(expression, frameId).map { it.result }
    }

    /**
     * Refresh threads list.
     */
    public suspend fun refreshThreads() {
        client.threads().onSuccess { threads ->
            _threads.value = threads
        }
    }

    /**
     * Refresh stack trace for the current thread.
     */
    public suspend fun refreshStackTrace() {
        val thread = _currentThread.value ?: return
        client.stackTrace(thread).onSuccess { frames ->
            _stackFrames.value = frames
            if (_selectedFrame.value == null && frames.isNotEmpty()) {
                selectFrame(frames.first())
            }
        }
    }

    /**
     * Refresh variables for a frame.
     */
    private suspend fun refreshVariables(frameId: Int) {
        val allVariables = mutableListOf<DapVariable>()

        client.scopes(frameId).onSuccess { scopes ->
            scopes.forEach { scope ->
                client.variables(scope.variablesReference).onSuccess { vars ->
                    allVariables.addAll(vars)
                }
            }
        }

        _variables.value = allVariables
    }

    private suspend fun handleEvent(
        eventType: String,
        body: kotlinx.serialization.json.JsonElement?,
    ) {
        when (eventType) {
            "initialized" -> {
                // Configuration phase complete
                breakpointManager.syncAllBreakpoints()
                client.configurationDone()
            }

            "stopped" -> {
                body?.let {
                    val stopped = json.decodeFromJsonElement<StoppedEventBody>(it)
                    _state.value = DebugState.PAUSED
                    stopped.threadId?.let { threadId ->
                        _currentThread.value = threadId
                    }
                    refreshThreads()
                    refreshStackTrace()
                }
            }

            "continued" -> {
                body?.let {
                    val continued = json.decodeFromJsonElement<ContinuedEventBody>(it)
                    _state.value = DebugState.RUNNING
                    if (continued.allThreadsContinued) {
                        _stackFrames.value = emptyList()
                        _selectedFrame.value = null
                        _variables.value = emptyList()
                    }
                }
            }

            "thread" -> {
                body?.let {
                    val thread = json.decodeFromJsonElement<ThreadEventBody>(it)
                    if (_state.value != DebugState.STOPPED) {
                        refreshThreads()
                    }
                }
            }

            "output" -> {
                body?.let {
                    val output = json.decodeFromJsonElement<OutputEventBody>(it)
                    val debugOutput =
                        DebugOutput(
                            category = output.category.name.lowercase(),
                            output = output.output,
                            source = output.source?.path,
                            line = output.line,
                        )
                    _output.value = _output.value + debugOutput
                }
            }

            "breakpoint" -> {
                body?.let {
                    val event = json.decodeFromJsonElement<BreakpointEventBody>(it)
                    breakpointManager.handleBreakpointEvent(
                        event.breakpoint,
                        event.reason.name.lowercase(),
                    )
                }
            }

            "exited" -> {
                body?.let {
                    val exited = json.decodeFromJsonElement<ExitedEventBody>(it)
                    _exitCode.value = exited.exitCode
                }
            }

            "terminated" -> {
                _state.value = DebugState.STOPPED
            }
        }
    }

    /**
     * Mark session as running (after launch/attach).
     */
    internal fun setRunning() {
        _state.value = DebugState.RUNNING
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        eventJob?.cancel()
        client.dispose()
    }

    public companion object {
        /**
         * Generate a unique session ID.
         */
        public fun generateId(): String {
            val chars = "0123456789abcdef"
            return "debug-" + (1..12).map { chars.random() }.joinToString("")
        }
    }
}
