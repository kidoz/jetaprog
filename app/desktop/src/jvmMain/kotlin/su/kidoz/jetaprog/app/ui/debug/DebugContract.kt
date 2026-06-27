package su.kidoz.jetaprog.app.ui.debug

import androidx.compose.runtime.Immutable

/** High-level status of the debug session, as presented by the UI. */
public enum class DebugStatus {
    /** No session, or the session terminated. */
    TERMINATED,

    /** The debuggee is running. */
    RUNNING,

    /** The debuggee is paused at a breakpoint or step. */
    PAUSED,
}

/** The presentation kind of a variable value, driving its icon and color. */
public enum class VarKind {
    /** An object/reference value. */
    OBJECT,

    /** An array/collection value. */
    ARRAY,

    /** A string value. */
    STRING,

    /** A numeric value. */
    NUMBER,

    /** Any other primitive value. */
    PRIMITIVE,
}

/** A thread shown in the thread selector. */
@Immutable
public data class ThreadView(
    /** DAP thread id. */
    val id: Int,
    /** Display name. */
    val name: String,
)

/** A stack frame in the call-stack list. */
@Immutable
public data class FrameView(
    /** DAP frame id. */
    val id: Int,
    /** Method/function display name. */
    val method: String,
    /** `Class:line` location label. */
    val location: String,
    /** Whether this is the current (top/selected) frame. */
    val isCurrent: Boolean,
)

/** A node in the variables/watches tree. */
@Immutable
public data class VarView(
    /** DAP variablesReference for lazy children (0 if not expandable). */
    val reference: Int,
    /** Variable name. */
    val name: String,
    /** Rendered value. */
    val value: String,
    /** Nesting depth. */
    val depth: Int,
    /** Whether children can be fetched. */
    val expandable: Boolean,
    /** Whether currently expanded. */
    val expanded: Boolean,
    /** Presentation kind. */
    val kind: VarKind,
)

/** A watch expression and its evaluated value. */
@Immutable
public data class WatchView(
    /** Stable id (the expression). */
    val id: String,
    /** The watch expression. */
    val expr: String,
    /** The evaluated value or error text. */
    val value: String,
    /** Whether [value] is an error. */
    val error: Boolean,
)

/** A user breakpoint shown in the Breakpoints panel and editor gutter. */
@Immutable
public data class BreakpointView(
    /** Absolute file path. */
    val file: String,
    /** 1-based line number. */
    val line: Int,
    /** Whether the breakpoint is enabled. */
    val enabled: Boolean,
    /** Whether the adapter verified the breakpoint. */
    val verified: Boolean,
    /** Optional condition expression. */
    val condition: String?,
) {
    /** Stable id. */
    val id: String get() = "$file:$line"

    /** `File.kt:line` display label. */
    val where: String get() = "${file.substringAfterLast('/')}:$line"
}

/** A source location, used to drive the editor's current-line decoration. */
@Immutable
public data class SourceLocation(
    /** Absolute file path. */
    val path: String,
    /** 1-based line number. */
    val line: Int,
)

/**
 * Immutable state of the debugger.
 *
 * @property status The session status.
 * @property threads All debuggee threads.
 * @property selectedThreadId The focused thread, if any.
 * @property frames The selected thread's call stack.
 * @property selectedFrameId The selected frame, if any.
 * @property variables The (lazy, flattened) variable tree for the selected frame.
 * @property watches Watch expressions and their values.
 * @property breakpoints All user breakpoints (persisted; session-independent).
 * @property stoppedAt The current execution location while paused.
 * @property variableValues Top-level locals (name → value) for inline editor hints.
 * @property showInlineValues Whether inline value hints are enabled.
 * @property hasSession Whether a debug session currently exists.
 */
@Immutable
public data class DebugUiState(
    val status: DebugStatus = DebugStatus.TERMINATED,
    val threads: List<ThreadView> = emptyList(),
    val selectedThreadId: Int? = null,
    val frames: List<FrameView> = emptyList(),
    val selectedFrameId: Int? = null,
    val variables: List<VarView> = emptyList(),
    val watches: List<WatchView> = emptyList(),
    val breakpoints: List<BreakpointView> = emptyList(),
    val stoppedAt: SourceLocation? = null,
    val variableValues: Map<String, String> = emptyMap(),
    val showInlineValues: Boolean = true,
    val hasSession: Boolean = false,
)

/** User intents for the debugger. */
public sealed interface DebugIntent {
    /** Resume execution. */
    public data object Resume : DebugIntent

    /** Pause execution. */
    public data object Pause : DebugIntent

    /** Stop the session. */
    public data object Stop : DebugIntent

    /** Step over the current line. */
    public data object StepOver : DebugIntent

    /** Step into the call. */
    public data object StepInto : DebugIntent

    /** Step out of the current function. */
    public data object StepOut : DebugIntent

    /** Focus a thread. */
    public data class SelectThread(
        val threadId: Int,
    ) : DebugIntent

    /** Select a stack frame. */
    public data class SelectFrame(
        val frameId: Int,
    ) : DebugIntent

    /** Expand/collapse a variable node (lazily fetching children). */
    public data class ToggleVariable(
        val reference: Int,
    ) : DebugIntent

    /** Add a watch expression. */
    public data class AddWatch(
        val expr: String,
    ) : DebugIntent

    /** Remove a watch expression. */
    public data class RemoveWatch(
        val id: String,
    ) : DebugIntent

    /** Toggle a breakpoint at a file/line. */
    public data class ToggleBreakpoint(
        val file: String,
        val line: Int,
    ) : DebugIntent

    /** Enable/disable a breakpoint. */
    public data class SetBreakpointEnabled(
        val id: String,
        val enabled: Boolean,
    ) : DebugIntent

    /** Toggle the inline-values editor decoration. */
    public data object ToggleInlineValues : DebugIntent
}
