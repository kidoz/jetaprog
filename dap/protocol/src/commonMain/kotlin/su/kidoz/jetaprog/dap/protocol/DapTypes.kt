package su.kidoz.jetaprog.dap.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A source file.
 */
@Serializable
public data class DapSource(
    /** The short name of the source. */
    val name: String? = null,
    /** The path of the source. */
    val path: String? = null,
    /** If sourceReference > 0 the contents of the source must be retrieved. */
    val sourceReference: Int? = null,
    /** An optional hint for how to present the source. */
    val presentationHint: SourcePresentationHint? = null,
    /** The origin of the source. */
    val origin: String? = null,
    /** A list of sources included in this source. */
    val sources: List<DapSource>? = null,
    /** Additional data about the source. */
    val adapterData: String? = null,
    /** Checksums associated with this file. */
    val checksums: List<DapChecksum>? = null,
)

/**
 * Presentation hint for a source.
 */
@Serializable
public enum class SourcePresentationHint {
    @SerialName("normal")
    NORMAL,

    @SerialName("emphasize")
    EMPHASIZE,

    @SerialName("deemphasize")
    DEEMPHASIZE,
}

/**
 * A checksum for a source file.
 */
@Serializable
public data class DapChecksum(
    /** The algorithm used to calculate the checksum. */
    val algorithm: ChecksumAlgorithm,
    /** The checksum value. */
    val checksum: String,
)

/**
 * Checksum algorithm.
 */
@Serializable
public enum class ChecksumAlgorithm {
    @SerialName("MD5")
    MD5,

    @SerialName("SHA1")
    SHA1,

    @SerialName("SHA256")
    SHA256,

    @SerialName("timestamp")
    TIMESTAMP,
}

/**
 * Information about a breakpoint.
 */
@Serializable
public data class DapBreakpoint(
    /** The identifier for the breakpoint. */
    val id: Int? = null,
    /** If true, the breakpoint could be set. */
    val verified: Boolean,
    /** An optional message about the breakpoint state. */
    val message: String? = null,
    /** The source where the breakpoint is located. */
    val source: DapSource? = null,
    /** The line position of the breakpoint. */
    val line: Int? = null,
    /** Start position within the line. */
    val column: Int? = null,
    /** End line of the range covered by the breakpoint. */
    val endLine: Int? = null,
    /** End position within the end line. */
    val endColumn: Int? = null,
    /** An optional unique identifier for the instruction. */
    val instructionReference: String? = null,
    /** An optional offset from the instruction reference. */
    val offset: Int? = null,
)

/**
 * A stack frame.
 */
@Serializable
public data class DapStackFrame(
    /** An identifier for the stack frame. */
    val id: Int,
    /** The name of the stack frame (typically function name). */
    val name: String,
    /** The source of the frame (optional). */
    val source: DapSource? = null,
    /** The line within the file. */
    val line: Int,
    /** The column within the line. */
    val column: Int,
    /** An optional end line. */
    val endLine: Int? = null,
    /** An optional end column. */
    val endColumn: Int? = null,
    /** Indicates whether the frame can be restarted. */
    val canRestart: Boolean? = null,
    /** An optional hint for the frame presentation. */
    val presentationHint: StackFramePresentationHint? = null,
    /** An optional instruction pointer reference. */
    val instructionPointerReference: String? = null,
    /** Module associated with this frame (optional). */
    val moduleId: Int? = null,
)

/**
 * Presentation hint for a stack frame.
 */
@Serializable
public enum class StackFramePresentationHint {
    @SerialName("normal")
    NORMAL,

    @SerialName("label")
    LABEL,

    @SerialName("subtle")
    SUBTLE,
}

/**
 * A scope within a stack frame.
 */
@Serializable
public data class DapScope(
    /** Name of the scope (e.g., "Locals", "Arguments"). */
    val name: String,
    /** An optional hint for how to present this scope. */
    val presentationHint: String? = null,
    /** Reference to retrieve the variables of this scope. */
    val variablesReference: Int,
    /** Number of named variables in this scope. */
    val namedVariables: Int? = null,
    /** Number of indexed variables in this scope. */
    val indexedVariables: Int? = null,
    /** If true, the number of variables is large. */
    val expensive: Boolean = false,
    /** Source for this scope (optional). */
    val source: DapSource? = null,
    /** Start line for the scope. */
    val line: Int? = null,
    /** Start column for the scope. */
    val column: Int? = null,
    /** End line for the scope. */
    val endLine: Int? = null,
    /** End column for the scope. */
    val endColumn: Int? = null,
)

/**
 * A variable.
 */
@Serializable
public data class DapVariable(
    /** The variable's name. */
    val name: String,
    /** The variable's value. */
    val value: String,
    /** The type of the variable's value (optional). */
    val type: String? = null,
    /** An optional hint for how to present this variable. */
    val presentationHint: VariablePresentationHint? = null,
    /** An optional evaluatable expression for the variable. */
    val evaluateName: String? = null,
    /** Reference to retrieve child variables. */
    val variablesReference: Int = 0,
    /** Number of named child variables. */
    val namedVariables: Int? = null,
    /** Number of indexed child variables. */
    val indexedVariables: Int? = null,
    /** Memory reference (if available). */
    val memoryReference: String? = null,
)

/**
 * Presentation hint for a variable.
 */
@Serializable
public data class VariablePresentationHint(
    /** Kind of variable (e.g., "property", "method", "class"). */
    val kind: String? = null,
    /** Attributes (e.g., "static", "constant", "readOnly"). */
    val attributes: List<String>? = null,
    /** Visibility (e.g., "public", "private", "protected"). */
    val visibility: String? = null,
    /** If true, clients should render the variable lazily. */
    val lazy: Boolean? = null,
)

/**
 * A thread in the debuggee.
 */
@Serializable
public data class DapThread(
    /** Unique identifier for the thread. */
    val id: Int,
    /** A human-readable name for the thread. */
    val name: String,
)

/**
 * A module in the debuggee.
 */
@Serializable
public data class DapModule(
    /** Unique identifier for the module. */
    val id: String,
    /** A name for the module. */
    val name: String,
    /** The path to the module on disk (optional). */
    val path: String? = null,
    /** True if the module is optimized. */
    val isOptimized: Boolean? = null,
    /** True if symbols are available. */
    val isUserCode: Boolean? = null,
    /** Version of the module. */
    val version: String? = null,
    /** Symbol file location. */
    val symbolFilePath: String? = null,
    /** Date and time when module was created. */
    val dateTimeStamp: String? = null,
    /** Address range covered by this module. */
    val addressRange: String? = null,
)

/**
 * An exception breakpoint filter.
 */
@Serializable
public data class DapExceptionFilterOptions(
    /** ID of the exception filter. */
    val filterId: String,
    /** Condition for the filter. */
    val condition: String? = null,
)

/**
 * An exception path segment.
 */
@Serializable
public data class DapExceptionPathSegment(
    /** If true, match when exception type is one of the names. */
    val negate: Boolean = false,
    /** Exception names to match. */
    val names: List<String>,
)

/**
 * Exception options.
 */
@Serializable
public data class DapExceptionOptions(
    /** Exception path segments. */
    val path: List<DapExceptionPathSegment>? = null,
    /** Condition when to break. */
    val breakMode: ExceptionBreakMode,
)

/**
 * Mode for breaking on exceptions.
 */
@Serializable
public enum class ExceptionBreakMode {
    @SerialName("never")
    NEVER,

    @SerialName("always")
    ALWAYS,

    @SerialName("unhandled")
    UNHANDLED,

    @SerialName("userUnhandled")
    USER_UNHANDLED,
}

/**
 * A source breakpoint.
 */
@Serializable
public data class DapSourceBreakpoint(
    /** The line number for the breakpoint. */
    val line: Int,
    /** An optional column for the breakpoint. */
    val column: Int? = null,
    /** A condition that must evaluate to true for the breakpoint to be hit. */
    val condition: String? = null,
    /** Expression to log when the breakpoint is hit. */
    val hitCondition: String? = null,
    /** Expression to evaluate when the breakpoint is hit. */
    val logMessage: String? = null,
)

/**
 * A function breakpoint.
 */
@Serializable
public data class DapFunctionBreakpoint(
    /** The name of the function. */
    val name: String,
    /** An optional condition for the breakpoint. */
    val condition: String? = null,
    /** An optional hit condition for the breakpoint. */
    val hitCondition: String? = null,
)

/**
 * A data breakpoint.
 */
@Serializable
public data class DapDataBreakpoint(
    /** The data identifier. */
    val dataId: String,
    /** The access type. */
    val accessType: DataBreakpointAccessType? = null,
    /** An optional condition for the breakpoint. */
    val condition: String? = null,
    /** An optional hit condition for the breakpoint. */
    val hitCondition: String? = null,
)

/**
 * Data breakpoint access type.
 */
@Serializable
public enum class DataBreakpointAccessType {
    @SerialName("read")
    READ,

    @SerialName("write")
    WRITE,

    @SerialName("readWrite")
    READ_WRITE,
}

/**
 * An instruction breakpoint.
 */
@Serializable
public data class DapInstructionBreakpoint(
    /** The instruction reference. */
    val instructionReference: String,
    /** An optional offset. */
    val offset: Int? = null,
    /** An optional condition for the breakpoint. */
    val condition: String? = null,
    /** An optional hit condition for the breakpoint. */
    val hitCondition: String? = null,
)

/**
 * Stepping granularity.
 */
@Serializable
public enum class SteppingGranularity {
    @SerialName("statement")
    STATEMENT,

    @SerialName("line")
    LINE,

    @SerialName("instruction")
    INSTRUCTION,
}

/**
 * Value formatting options.
 */
@Serializable
public data class ValueFormat(
    /** Display the value in hexadecimal. */
    val hex: Boolean = false,
)

/**
 * Stack frame format options.
 */
@Serializable
public data class StackFrameFormat(
    /** Display parameters for the stack frame. */
    val parameters: Boolean? = null,
    /** Display parameter types. */
    val parameterTypes: Boolean? = null,
    /** Display parameter names. */
    val parameterNames: Boolean? = null,
    /** Display parameter values. */
    val parameterValues: Boolean? = null,
    /** Display line number. */
    val line: Boolean? = null,
    /** Display module. */
    val module: Boolean? = null,
    /** Include all stack frames (even those not relevant). */
    val includeAll: Boolean? = null,
)
