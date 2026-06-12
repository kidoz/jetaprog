package su.kidoz.jetaprog.dap.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base class for all DAP protocol messages.
 */
@Serializable
public sealed interface DapMessage {
    public val seq: Int
    public val type: String
}

/**
 * A client or debug adapter initiated request.
 */
@Serializable
@SerialName("request")
public data class DapRequest(
    override val seq: Int,
    override val type: String = "request",
    /** The command to execute. */
    val command: String,
    /** Object containing arguments for the command (optional). */
    val arguments: JsonElement? = null,
) : DapMessage

/**
 * Response to a request.
 */
@Serializable
@SerialName("response")
public data class DapResponse(
    override val seq: Int,
    override val type: String = "response",
    /** Sequence number of the corresponding request. */
    @SerialName("request_seq")
    val requestSeq: Int,
    /** Outcome of the request. */
    val success: Boolean,
    /** The command requested. */
    val command: String,
    /** Contains the raw error message (only for unsuccessful responses). */
    val message: String? = null,
    /** Contains request result or error details (optional). */
    val body: JsonElement? = null,
) : DapMessage

/**
 * A debug adapter initiated event.
 */
@Serializable
@SerialName("event")
public data class DapEvent(
    override val seq: Int,
    override val type: String = "event",
    /** Type of event. */
    val event: String,
    /** Event-specific information (optional). */
    val body: JsonElement? = null,
) : DapMessage

/**
 * Capabilities of the debug adapter.
 */
@Serializable
public data class DapCapabilities(
    /** The debug adapter supports the configurationDone request. */
    val supportsConfigurationDoneRequest: Boolean = false,
    /** The debug adapter supports function breakpoints. */
    val supportsFunctionBreakpoints: Boolean = false,
    /** The debug adapter supports conditional breakpoints. */
    val supportsConditionalBreakpoints: Boolean = false,
    /** The debug adapter supports hit conditional breakpoints. */
    val supportsHitConditionalBreakpoints: Boolean = false,
    /** The debug adapter supports evaluate for data hovers. */
    val supportsEvaluateForHovers: Boolean = false,
    /** Available exception filter options. */
    val exceptionBreakpointFilters: List<ExceptionBreakpointsFilter>? = null,
    /** The debug adapter supports stepping back via the stepBack and reverseContinue requests. */
    val supportsStepBack: Boolean = false,
    /** The debug adapter supports setting a variable to a value. */
    val supportsSetVariable: Boolean = false,
    /** The debug adapter supports restarting a frame. */
    val supportsRestartFrame: Boolean = false,
    /** The debug adapter supports the goto targets request. */
    val supportsGotoTargetsRequest: Boolean = false,
    /** The debug adapter supports the stepInTargets request. */
    val supportsStepInTargetsRequest: Boolean = false,
    /** The debug adapter supports the completions request. */
    val supportsCompletionsRequest: Boolean = false,
    /** The debug adapter supports the modules request. */
    val supportsModulesRequest: Boolean = false,
    /** The debug adapter supports the restart request. */
    val supportsRestartRequest: Boolean = false,
    /** The debug adapter supports exception options. */
    val supportsExceptionOptions: Boolean = false,
    /** The debug adapter supports value formatting options. */
    val supportsValueFormattingOptions: Boolean = false,
    /** The debug adapter supports exception info request. */
    val supportsExceptionInfoRequest: Boolean = false,
    /** The debug adapter supports terminate debuggee request. */
    val supportTerminateDebuggee: Boolean = false,
    /** The debug adapter supports terminate request. */
    val supportsTerminateRequest: Boolean = false,
    /** The debug adapter supports delayed loading of stack frames. */
    val supportsDelayedStackTraceLoading: Boolean = false,
    /** The debug adapter supports loaded sources request. */
    val supportsLoadedSourcesRequest: Boolean = false,
    /** The debug adapter supports log points. */
    val supportsLogPoints: Boolean = false,
    /** The debug adapter supports terminate threads request. */
    val supportsTerminateThreadsRequest: Boolean = false,
    /** The debug adapter supports set expression request. */
    val supportsSetExpression: Boolean = false,
    /** The debug adapter supports data breakpoints. */
    val supportsDataBreakpoints: Boolean = false,
    /** The debug adapter supports read memory request. */
    val supportsReadMemoryRequest: Boolean = false,
    /** The debug adapter supports write memory request. */
    val supportsWriteMemoryRequest: Boolean = false,
    /** The debug adapter supports disassemble request. */
    val supportsDisassembleRequest: Boolean = false,
    /** The debug adapter supports cancel request. */
    val supportsCancelRequest: Boolean = false,
    /** The debug adapter supports breakpoint locations request. */
    val supportsBreakpointLocationsRequest: Boolean = false,
    /** The debug adapter supports clipboard context. */
    val supportsClipboardContext: Boolean = false,
    /** The debug adapter supports stepping granularity. */
    val supportsSteppingGranularity: Boolean = false,
    /** The debug adapter supports instruction breakpoints. */
    val supportsInstructionBreakpoints: Boolean = false,
    /** The debug adapter supports exception filter options. */
    val supportsExceptionFilterOptions: Boolean = false,
    /** The debug adapter supports single thread execution requests. */
    val supportsSingleThreadExecutionRequests: Boolean = false,
)

/**
 * Exception breakpoints filter.
 */
@Serializable
public data class ExceptionBreakpointsFilter(
    /** The internal ID of the filter. */
    val filter: String,
    /** The name of the filter. */
    val label: String,
    /** A description of the filter. */
    val description: String? = null,
    /** Initial value of the filter. */
    val default: Boolean = false,
    /** Whether the filter supports condition. */
    val supportsCondition: Boolean = false,
    /** Label for the condition input field. */
    val conditionDescription: String? = null,
)
