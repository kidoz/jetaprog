package su.kidoz.jetaprog.dap.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============================================================================
// Request Arguments
// ============================================================================

/**
 * Arguments for the 'initialize' request.
 */
@Serializable
public data class InitializeRequestArguments(
    /** The ID of the client using this adapter. */
    val clientID: String? = null,
    /** The human-readable name of the client using this adapter. */
    val clientName: String? = null,
    /** The ID of the debug adapter. */
    val adapterID: String,
    /** The ISO-639 locale of the client. */
    val locale: String? = null,
    /** If true, the client supports run in terminal request. */
    val supportsRunInTerminalRequest: Boolean = false,
    /** If true, the client supports memory references. */
    val supportsMemoryReferences: Boolean = false,
    /** If true, the client supports progress reporting. */
    val supportsProgressReporting: Boolean = false,
    /** If true, the client supports invalidated events. */
    val supportsInvalidatedEvent: Boolean = false,
    /** If true, the client supports the memory event. */
    val supportsMemoryEvent: Boolean = false,
    /** If true, all line numbers are 1-based (default). */
    val linesStartAt1: Boolean = true,
    /** If true, all column numbers are 1-based (default). */
    val columnsStartAt1: Boolean = true,
    /** Determines format of paths (path or uri). */
    val pathFormat: String = "path",
    /** Client supports variable type. */
    val supportsVariableType: Boolean = false,
    /** Client supports variable paging. */
    val supportsVariablePaging: Boolean = false,
    /** Client supports the startDebugging request. */
    val supportsStartDebuggingRequest: Boolean = false,
)

/**
 * Arguments for the 'launch' request.
 */
@Serializable
public data class LaunchRequestArguments(
    /** If true, don't actually start the debuggee. */
    val noDebug: Boolean = false,
    /** Restart data if this is a restart request. */
    @SerialName("__restart")
    val restart: JsonElement? = null,
    /** Program to launch (adapter-specific). */
    val program: String? = null,
    /** Arguments to pass to the program. */
    val args: List<String>? = null,
    /** Working directory. */
    val cwd: String? = null,
    /** Environment variables. */
    val env: Map<String, String>? = null,
    /** Stop at entry point. */
    val stopOnEntry: Boolean = false,
    /** Stop at entry point for adapters that use the VS Code C# field name. */
    val stopAtEntry: Boolean? = null,
    /** Console type (integratedTerminal, externalTerminal, internalConsole). */
    val console: String? = null,
)

/**
 * Arguments for the 'attach' request.
 */
@Serializable
public data class AttachRequestArguments(
    /** Restart data if this is a restart request. */
    @SerialName("__restart")
    val restart: JsonElement? = null,
    /** Process ID to attach to. */
    val processId: Int? = null,
    /** Port to connect to. */
    val port: Int? = null,
    /** Host to connect to. */
    val host: String? = null,
)

/**
 * Arguments for the 'setBreakpoints' request.
 */
@Serializable
public data class SetBreakpointsArguments(
    /** The source location of the breakpoints. */
    val source: DapSource,
    /** The code locations of the breakpoints. */
    val breakpoints: List<DapSourceBreakpoint>? = null,
    /** Deprecated: Use 'breakpoints' instead. */
    val lines: List<Int>? = null,
    /** Whether the debug adapter should use source modified information. */
    val sourceModified: Boolean = false,
)

/**
 * Arguments for the 'setFunctionBreakpoints' request.
 */
@Serializable
public data class SetFunctionBreakpointsArguments(
    /** The function breakpoints. */
    val breakpoints: List<DapFunctionBreakpoint>,
)

/**
 * Arguments for the 'setExceptionBreakpoints' request.
 */
@Serializable
public data class SetExceptionBreakpointsArguments(
    /** Set of exception filters to enable. */
    val filters: List<String>,
    /** Set of exception filters with conditions. */
    val filterOptions: List<DapExceptionFilterOptions>? = null,
    /** Configuration options for selected exceptions. */
    val exceptionOptions: List<DapExceptionOptions>? = null,
)

/**
 * Arguments for the 'continue' request.
 */
@Serializable
public data class ContinueArguments(
    /** Continue execution for this thread. */
    val threadId: Int,
    /** If true, continue all threads (optional). */
    val singleThread: Boolean = false,
)

/**
 * Arguments for the 'next' request (step over).
 */
@Serializable
public data class NextArguments(
    /** Execute next for this thread. */
    val threadId: Int,
    /** If true, step only the thread. */
    val singleThread: Boolean = false,
    /** Stepping granularity. */
    val granularity: SteppingGranularity? = null,
)

/**
 * Arguments for the 'stepIn' request.
 */
@Serializable
public data class StepInArguments(
    /** Step into for this thread. */
    val threadId: Int,
    /** If true, step only the thread. */
    val singleThread: Boolean = false,
    /** Target to step into (if step in targets are supported). */
    val targetId: Int? = null,
    /** Stepping granularity. */
    val granularity: SteppingGranularity? = null,
)

/**
 * Arguments for the 'stepOut' request.
 */
@Serializable
public data class StepOutArguments(
    /** Step out for this thread. */
    val threadId: Int,
    /** If true, step only the thread. */
    val singleThread: Boolean = false,
    /** Stepping granularity. */
    val granularity: SteppingGranularity? = null,
)

/**
 * Arguments for the 'pause' request.
 */
@Serializable
public data class PauseArguments(
    /** Pause execution for this thread. */
    val threadId: Int,
)

/**
 * Arguments for the 'stackTrace' request.
 */
@Serializable
public data class StackTraceArguments(
    /** Retrieve the stacktrace for this thread. */
    val threadId: Int,
    /** The index of the first frame to return (0-based). */
    val startFrame: Int? = null,
    /** The maximum number of frames to return. */
    val levels: Int? = null,
    /** Stack frame format options. */
    val format: StackFrameFormat? = null,
)

/**
 * Arguments for the 'scopes' request.
 */
@Serializable
public data class ScopesArguments(
    /** Retrieve the scopes for this stack frame. */
    val frameId: Int,
)

/**
 * Arguments for the 'variables' request.
 */
@Serializable
public data class VariablesArguments(
    /** The variables reference. */
    val variablesReference: Int,
    /** Optional filter to retrieve only named or indexed variables. */
    val filter: VariableFilter? = null,
    /** The index of the first variable to return. */
    val start: Int? = null,
    /** The number of variables to return. */
    val count: Int? = null,
    /** Formatting options for the value. */
    val format: ValueFormat? = null,
)

/**
 * Variable filter type.
 */
@Serializable
public enum class VariableFilter {
    @SerialName("indexed")
    INDEXED,

    @SerialName("named")
    NAMED,
}

/**
 * Arguments for the 'evaluate' request.
 */
@Serializable
public data class EvaluateArguments(
    /** The expression to evaluate. */
    val expression: String,
    /** Evaluate in the scope of this stack frame. */
    val frameId: Int? = null,
    /** The context in which the evaluate request is made. */
    val context: EvaluateContext? = null,
    /** Formatting options for the result. */
    val format: ValueFormat? = null,
)

/**
 * Evaluation context.
 */
@Serializable
public enum class EvaluateContext {
    @SerialName("watch")
    WATCH,

    @SerialName("repl")
    REPL,

    @SerialName("hover")
    HOVER,

    @SerialName("clipboard")
    CLIPBOARD,

    @SerialName("variables")
    VARIABLES,
}

/**
 * Arguments for the 'source' request.
 */
@Serializable
public data class SourceArguments(
    /** The source to retrieve. */
    val source: DapSource? = null,
    /** The reference to the source. */
    val sourceReference: Int,
)

/**
 * Arguments for the 'disconnect' request.
 */
@Serializable
public data class DisconnectArguments(
    /** Whether to terminate the debuggee. */
    val terminateDebuggee: Boolean = false,
    /** Whether to restart the debuggee. */
    val restart: Boolean = false,
    /** If true, suspend the debuggee before terminating. */
    val suspendDebuggee: Boolean = false,
)

/**
 * Arguments for the 'terminate' request.
 */
@Serializable
public data class TerminateArguments(
    /** Whether to restart the debuggee after termination. */
    val restart: Boolean = false,
)

/**
 * Arguments for the 'setVariable' request.
 */
@Serializable
public data class SetVariableArguments(
    /** The reference of the variable container. */
    val variablesReference: Int,
    /** The name of the variable in the container. */
    val name: String,
    /** The value of the variable. */
    val value: String,
    /** Formatting options for the value. */
    val format: ValueFormat? = null,
)

// ============================================================================
// Response Bodies
// ============================================================================

/**
 * Response body for 'initialize' request.
 */
@Serializable
public data class InitializeResponseBody(
    /** The capabilities of the debug adapter. */
    val capabilities: DapCapabilities = DapCapabilities(),
)

/**
 * Response body for 'setBreakpoints' request.
 */
@Serializable
public data class SetBreakpointsResponseBody(
    /** Information about the breakpoints. */
    val breakpoints: List<DapBreakpoint>,
)

/**
 * Response body for 'setFunctionBreakpoints' request.
 */
@Serializable
public data class SetFunctionBreakpointsResponseBody(
    /** Information about the breakpoints. */
    val breakpoints: List<DapBreakpoint>,
)

/**
 * Response body for 'continue' request.
 */
@Serializable
public data class ContinueResponseBody(
    /** If true, all threads have been resumed. */
    val allThreadsContinued: Boolean = true,
)

/**
 * Response body for 'threads' request.
 */
@Serializable
public data class ThreadsResponseBody(
    /** All threads. */
    val threads: List<DapThread>,
)

/**
 * Response body for 'stackTrace' request.
 */
@Serializable
public data class StackTraceResponseBody(
    /** The frames of the stack frame. */
    val stackFrames: List<DapStackFrame>,
    /** Total number of frames available. */
    val totalFrames: Int? = null,
)

/**
 * Response body for 'scopes' request.
 */
@Serializable
public data class ScopesResponseBody(
    /** The scopes of the stack frame. */
    val scopes: List<DapScope>,
)

/**
 * Response body for 'variables' request.
 */
@Serializable
public data class VariablesResponseBody(
    /** All child variables. */
    val variables: List<DapVariable>,
)

/**
 * Response body for 'evaluate' request.
 */
@Serializable
public data class EvaluateResponseBody(
    /** The result of the evaluate request. */
    val result: String,
    /** The type of the result. */
    val type: String? = null,
    /** Presentation hint for the result. */
    val presentationHint: VariablePresentationHint? = null,
    /** If greater than 0, the result has children. */
    val variablesReference: Int = 0,
    /** Number of named child variables. */
    val namedVariables: Int? = null,
    /** Number of indexed child variables. */
    val indexedVariables: Int? = null,
    /** Memory reference for the result. */
    val memoryReference: String? = null,
)

/**
 * Response body for 'source' request.
 */
@Serializable
public data class SourceResponseBody(
    /** Content of the source. */
    val content: String,
    /** MIME type of the content. */
    val mimeType: String? = null,
)

/**
 * Response body for 'setVariable' request.
 */
@Serializable
public data class SetVariableResponseBody(
    /** The new value of the variable. */
    val value: String,
    /** The type of the new value. */
    val type: String? = null,
    /** If greater than 0, the result has children. */
    val variablesReference: Int = 0,
    /** Number of named child variables. */
    val namedVariables: Int? = null,
    /** Number of indexed child variables. */
    val indexedVariables: Int? = null,
)

// ============================================================================
// Event Bodies
// ============================================================================

/**
 * Body for 'initialized' event.
 */
@Serializable
public class InitializedEventBody

/**
 * Body for 'stopped' event.
 */
@Serializable
public data class StoppedEventBody(
    /** The reason for the stop. */
    val reason: StoppedReason,
    /** Additional description of the reason. */
    val description: String? = null,
    /** The thread which was stopped. */
    val threadId: Int? = null,
    /** Indicates if all threads are stopped. */
    val allThreadsStopped: Boolean? = null,
    /** Additional information for UI. */
    val text: String? = null,
    /** If true, this event should not change the focus. */
    val preserveFocusHint: Boolean = false,
    /** IDs of threads that stopped. */
    val hitBreakpointIds: List<Int>? = null,
)

/**
 * Reason the debuggee stopped.
 */
@Serializable
public enum class StoppedReason {
    @SerialName("step")
    STEP,

    @SerialName("breakpoint")
    BREAKPOINT,

    @SerialName("exception")
    EXCEPTION,

    @SerialName("pause")
    PAUSE,

    @SerialName("entry")
    ENTRY,

    @SerialName("goto")
    GOTO,

    @SerialName("function breakpoint")
    FUNCTION_BREAKPOINT,

    @SerialName("data breakpoint")
    DATA_BREAKPOINT,

    @SerialName("instruction breakpoint")
    INSTRUCTION_BREAKPOINT,
}

/**
 * Body for 'continued' event.
 */
@Serializable
public data class ContinuedEventBody(
    /** The thread which was continued. */
    val threadId: Int,
    /** If true, all threads have been continued. */
    val allThreadsContinued: Boolean = true,
)

/**
 * Body for 'exited' event.
 */
@Serializable
public data class ExitedEventBody(
    /** The exit code of the debuggee. */
    val exitCode: Int,
)

/**
 * Body for 'terminated' event.
 */
@Serializable
public data class TerminatedEventBody(
    /** A restart request was issued. */
    val restart: JsonElement? = null,
)

/**
 * Body for 'thread' event.
 */
@Serializable
public data class ThreadEventBody(
    /** The reason for the event. */
    val reason: ThreadEventReason,
    /** The identifier of the thread. */
    val threadId: Int,
)

/**
 * Reason for thread event.
 */
@Serializable
public enum class ThreadEventReason {
    @SerialName("started")
    STARTED,

    @SerialName("exited")
    EXITED,
}

/**
 * Body for 'output' event.
 */
@Serializable
public data class OutputEventBody(
    /** The output category. */
    val category: OutputCategory = OutputCategory.CONSOLE,
    /** The output to report. */
    val output: String,
    /** Which group this output belongs to. */
    val group: OutputGroup? = null,
    /** Reference to variables (if structured output). */
    val variablesReference: Int? = null,
    /** Source location for the output. */
    val source: DapSource? = null,
    /** Line in the source. */
    val line: Int? = null,
    /** Column in the source. */
    val column: Int? = null,
    /** Additional data for this output. */
    val data: JsonElement? = null,
)

/**
 * Output category.
 */
@Serializable
public enum class OutputCategory {
    @SerialName("console")
    CONSOLE,

    @SerialName("important")
    IMPORTANT,

    @SerialName("stdout")
    STDOUT,

    @SerialName("stderr")
    STDERR,

    @SerialName("telemetry")
    TELEMETRY,
}

/**
 * Output grouping.
 */
@Serializable
public enum class OutputGroup {
    @SerialName("start")
    START,

    @SerialName("startCollapsed")
    START_COLLAPSED,

    @SerialName("end")
    END,
}

/**
 * Body for 'breakpoint' event.
 */
@Serializable
public data class BreakpointEventBody(
    /** The reason for the event. */
    val reason: BreakpointEventReason,
    /** The breakpoint. */
    val breakpoint: DapBreakpoint,
)

/**
 * Reason for breakpoint event.
 */
@Serializable
public enum class BreakpointEventReason {
    @SerialName("changed")
    CHANGED,

    @SerialName("new")
    NEW,

    @SerialName("removed")
    REMOVED,
}

/**
 * Body for 'module' event.
 */
@Serializable
public data class ModuleEventBody(
    /** The reason for the event. */
    val reason: ModuleEventReason,
    /** The module. */
    val module: DapModule,
)

/**
 * Reason for module event.
 */
@Serializable
public enum class ModuleEventReason {
    @SerialName("new")
    NEW,

    @SerialName("changed")
    CHANGED,

    @SerialName("removed")
    REMOVED,
}

/**
 * Body for 'process' event.
 */
@Serializable
public data class ProcessEventBody(
    /** The logical name of the process. */
    val name: String,
    /** The system process ID of the debugged process. */
    val systemProcessId: Int? = null,
    /** If true, the process is running on the same computer as the debug adapter. */
    val isLocalProcess: Boolean = true,
    /** Describes how the debug engine started debugging this process. */
    val startMethod: ProcessStartMethod? = null,
    /** The size of a pointer in bits. */
    val pointerSize: Int? = null,
)

/**
 * How the process was started.
 */
@Serializable
public enum class ProcessStartMethod {
    @SerialName("launch")
    LAUNCH,

    @SerialName("attach")
    ATTACH,

    @SerialName("attachForSuspendedLaunch")
    ATTACH_FOR_SUSPENDED_LAUNCH,
}
