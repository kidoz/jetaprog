package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.app.debug.DebugPrefs
import su.kidoz.jetaprog.app.debug.DebugPrefsStore
import su.kidoz.jetaprog.app.ui.debug.BreakpointView
import su.kidoz.jetaprog.app.ui.debug.DebugIntent
import su.kidoz.jetaprog.app.ui.debug.DebugStatus
import su.kidoz.jetaprog.app.ui.debug.DebugUiState
import su.kidoz.jetaprog.app.ui.debug.FrameView
import su.kidoz.jetaprog.app.ui.debug.SourceLocation
import su.kidoz.jetaprog.app.ui.debug.ThreadView
import su.kidoz.jetaprog.app.ui.debug.VarKind
import su.kidoz.jetaprog.app.ui.debug.VarView
import su.kidoz.jetaprog.app.ui.debug.WatchView
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.dap.protocol.DapStackFrame
import su.kidoz.jetaprog.dap.protocol.DapVariable
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.dap.service.DebugSession
import su.kidoz.jetaprog.dap.service.DebugState
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Drives the debugger UI from the live [DebugService].
 *
 * Tracks the active [DebugSession], maps its threads / call stack / variables /
 * output into [DebugUiState], evaluates watch expressions, owns a project-level
 * (session-independent, persisted) breakpoint set that is replayed into each new
 * session, and lazily fetches variable children on expand.
 *
 * @param debugService the global debug service exposing active sessions.
 * @param projectPath the workspace root.
 * @param fileSystem the file system used for preference persistence.
 */
public class DebugViewModel(
    private val debugService: DebugService,
    projectPath: String,
    fileSystem: FileSystem,
) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefsStore = DebugPrefsStore(fileSystem, projectPath)

    private val _state = MutableStateFlow(DebugUiState())

    /** The observable debugger state. */
    public val state: StateFlow<DebugUiState> = _state.asStateFlow()

    private var active: DebugSession? = null
    private var sessionJob: Job? = null
    private var rawFrames: List<DapStackFrame> = emptyList()
    private var rootVars: List<DapVariable> = emptyList()
    private val expandedRefs = mutableSetOf<Int>()
    private val childrenCache = mutableMapOf<Int, List<DapVariable>>()

    init {
        scope.launch {
            val prefs = prefsStore.load()
            _state.update {
                it.copy(
                    breakpoints = prefs.breakpoints,
                    watches = prefs.watches.map { expr -> WatchView(expr, expr, "—", error = false) },
                    showInlineValues = prefs.showInlineValues,
                )
            }
        }
        scope.launch {
            debugService.sessions.collect { sessions ->
                val session = sessions.values.lastOrNull()
                if (session !== active) {
                    active = session
                    bindSession(session)
                }
            }
        }
    }

    /** Dispatches a user [intent]. */
    public fun dispatch(intent: DebugIntent) {
        scope.launch { handle(intent) }
    }

    private suspend fun handle(intent: DebugIntent) {
        val session = active
        when (intent) {
            DebugIntent.Resume -> {
                session?.continueExecution()
            }

            DebugIntent.Pause -> {
                session?.pause()
            }

            DebugIntent.Stop -> {
                session?.let { debugService.stopSession(it.id) }
            }

            DebugIntent.StepOver -> {
                session?.stepOver()
            }

            DebugIntent.StepInto -> {
                session?.stepInto()
            }

            DebugIntent.StepOut -> {
                session?.stepOut()
            }

            is DebugIntent.SelectThread -> {
                _state.update { it.copy(selectedThreadId = intent.threadId) }
            }

            is DebugIntent.SelectFrame -> {
                selectFrame(intent.frameId)
            }

            is DebugIntent.ToggleVariable -> {
                toggleVariable(intent.reference)
            }

            is DebugIntent.AddWatch -> {
                addWatch(intent.expr)
            }

            is DebugIntent.RemoveWatch -> {
                removeWatch(intent.id)
            }

            is DebugIntent.ToggleBreakpoint -> {
                toggleBreakpoint(intent.file, intent.line)
            }

            is DebugIntent.SetBreakpointEnabled -> {
                setBreakpointEnabled(intent.id, intent.enabled)
            }

            DebugIntent.ToggleInlineValues -> {
                _state.update { it.copy(showInlineValues = !it.showInlineValues) }
                persist()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Session binding
    // ------------------------------------------------------------------------

    private fun bindSession(session: DebugSession?) {
        sessionJob?.cancel()
        sessionJob = null
        rawFrames = emptyList()
        rootVars = emptyList()
        expandedRefs.clear()
        childrenCache.clear()

        if (session == null) {
            _state.update {
                it.copy(
                    status = DebugStatus.TERMINATED,
                    hasSession = false,
                    threads = emptyList(),
                    selectedThreadId = null,
                    frames = emptyList(),
                    selectedFrameId = null,
                    variables = emptyList(),
                    variableValues = emptyMap(),
                    stoppedAt = null,
                    watches = it.watches.map { w -> w.copy(value = "—", error = false) },
                )
            }
            return
        }

        _state.update { it.copy(hasSession = true) }
        scope.launch {
            _state.value.breakpoints.forEach { bp ->
                session.breakpointManager.addBreakpoint(bp.file, bp.line, bp.condition)
                if (!bp.enabled) session.breakpointManager.setBreakpointEnabled(bp.file, bp.line, false)
            }
        }

        sessionJob =
            scope.launch {
                launch { session.state.collect { onSessionState(it, session) } }
                launch {
                    session.threads.collect { ts ->
                        _state.update { st -> st.copy(threads = ts.map { ThreadView(it.id, it.name) }) }
                    }
                }
                launch { session.currentThread.collect { id -> _state.update { it.copy(selectedThreadId = id) } } }
                launch { session.stackFrames.collect { fs -> onFrames(fs) } }
                launch {
                    session.selectedFrame.collect { f ->
                        _state.update { it.copy(selectedFrameId = f?.id) }
                        evaluateWatches(session)
                    }
                }
                launch {
                    session.variables.collect { vs ->
                        rootVars = vs
                        rebuildVariables()
                        updateInlineValues()
                    }
                }
                launch {
                    session.breakpointManager.breakpoints.collect { managed ->
                        val verified =
                            managed.values
                                .filter { it.verified }
                                .map { it.userBreakpoint.id }
                                .toSet()
                        _state.update { st ->
                            st.copy(breakpoints = st.breakpoints.map { it.copy(verified = it.id in verified) })
                        }
                    }
                }
            }
    }

    private fun onSessionState(
        state: DebugState,
        session: DebugSession,
    ) {
        val status =
            when (state) {
                DebugState.PAUSED -> DebugStatus.PAUSED
                DebugState.STOPPED -> DebugStatus.TERMINATED
                else -> DebugStatus.RUNNING
            }
        _state.update { it.copy(status = status) }
        recomputeStoppedAt()
        updateInlineValues()
        if (status != DebugStatus.PAUSED) {
            _state.update { it.copy(variableValues = emptyMap()) }
        }
        scope.launch { evaluateWatches(session) }
    }

    private fun onFrames(frames: List<DapStackFrame>) {
        rawFrames = frames
        val selectedId = _state.value.selectedFrameId ?: frames.firstOrNull()?.id
        _state.update {
            it.copy(
                frames =
                    frames.map { frame ->
                        FrameView(
                            id = frame.id,
                            method = frame.name,
                            location = "${frame.source?.name ?: "?"}:${frame.line}",
                            isCurrent = frame.id == selectedId,
                        )
                    },
            )
        }
        recomputeStoppedAt()
    }

    private fun recomputeStoppedAt() {
        val paused = _state.value.status == DebugStatus.PAUSED
        val top = rawFrames.firstOrNull()
        val location = top?.source?.path?.let { SourceLocation(it, top.line) }
        _state.update { it.copy(stoppedAt = if (paused) location else null) }
    }

    // ------------------------------------------------------------------------
    // Variables (lazy tree)
    // ------------------------------------------------------------------------

    private suspend fun selectFrame(frameId: Int) {
        val frame = rawFrames.firstOrNull { it.id == frameId } ?: return
        expandedRefs.clear()
        childrenCache.clear()
        active?.selectFrame(frame)
    }

    private suspend fun toggleVariable(reference: Int) {
        if (reference <= 0) return
        if (reference in expandedRefs) {
            expandedRefs.remove(reference)
        } else {
            if (reference !in childrenCache) {
                active?.client?.variables(reference)?.onSuccess { childrenCache[reference] = it }
            }
            expandedRefs.add(reference)
        }
        rebuildVariables()
    }

    private fun rebuildVariables() {
        val out = mutableListOf<VarView>()
        appendVars(rootVars, depth = 0, out = out)
        _state.update { it.copy(variables = out) }
    }

    private fun appendVars(
        vars: List<DapVariable>,
        depth: Int,
        out: MutableList<VarView>,
    ) {
        for (v in vars) {
            val expandable = v.variablesReference > 0
            val expanded = expandable && v.variablesReference in expandedRefs
            out +=
                VarView(
                    reference = v.variablesReference,
                    name = v.name,
                    value = v.value,
                    depth = depth,
                    expandable = expandable,
                    expanded = expanded,
                    kind = kindOf(v),
                )
            if (expanded) appendVars(childrenCache[v.variablesReference].orEmpty(), depth + 1, out)
        }
    }

    private fun updateInlineValues() {
        val paused = _state.value.status == DebugStatus.PAUSED
        _state.update {
            it.copy(variableValues = if (paused) rootVars.associate { v -> v.name to v.value } else emptyMap())
        }
    }

    // ------------------------------------------------------------------------
    // Watches
    // ------------------------------------------------------------------------

    private suspend fun addWatch(expr: String) {
        val trimmed = expr.trim()
        if (trimmed.isEmpty() || _state.value.watches.any { it.expr == trimmed }) return
        _state.update { it.copy(watches = it.watches + WatchView(trimmed, trimmed, "—", error = false)) }
        persist()
        active?.let { evaluateWatches(it) }
    }

    private suspend fun removeWatch(id: String) {
        _state.update { it.copy(watches = it.watches.filterNot { w -> w.id == id }) }
        persist()
    }

    private suspend fun evaluateWatches(session: DebugSession) {
        if (_state.value.status != DebugStatus.PAUSED) {
            _state.update { it.copy(watches = it.watches.map { w -> w.copy(value = "—", error = false) }) }
            return
        }
        val evaluated =
            _state.value.watches.map { w ->
                session
                    .evaluate(w.expr)
                    .fold(
                        onSuccess = { w.copy(value = it, error = false) },
                        onFailure = { w.copy(value = it.message ?: "error", error = true) },
                    )
            }
        _state.update { it.copy(watches = evaluated) }
    }

    // ------------------------------------------------------------------------
    // Breakpoints (project-level, persisted)
    // ------------------------------------------------------------------------

    private suspend fun toggleBreakpoint(
        file: String,
        line: Int,
    ) {
        val id = "$file:$line"
        val existing = _state.value.breakpoints.firstOrNull { it.id == id }
        if (existing != null) {
            _state.update { it.copy(breakpoints = it.breakpoints.filterNot { b -> b.id == id }) }
            active?.breakpointManager?.removeBreakpoint(file, line)
        } else {
            _state.update {
                it.copy(
                    breakpoints =
                        it.breakpoints + BreakpointView(file, line, enabled = true, verified = false, condition = null),
                )
            }
            active?.breakpointManager?.addBreakpoint(file, line)
        }
        persist()
    }

    private suspend fun setBreakpointEnabled(
        id: String,
        enabled: Boolean,
    ) {
        val bp = _state.value.breakpoints.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(breakpoints = it.breakpoints.map { b -> if (b.id == id) b.copy(enabled = enabled) else b })
        }
        active?.breakpointManager?.setBreakpointEnabled(bp.file, bp.line, enabled)
        persist()
    }

    private suspend fun persist() {
        val st = _state.value
        prefsStore.save(DebugPrefs(st.breakpoints, st.watches.map { it.expr }, st.showInlineValues))
    }

    override fun dispose() {
        scope.cancel()
    }

    private companion object {
        fun kindOf(v: DapVariable): VarKind {
            if (v.variablesReference > 0) {
                val isArray =
                    v.value.startsWith("[") || (v.type?.contains("Array") == true) || (v.type?.contains("List") == true)
                return if (isArray) VarKind.ARRAY else VarKind.OBJECT
            }
            return when {
                v.value.startsWith("\"") -> VarKind.STRING
                v.value.toDoubleOrNull() != null -> VarKind.NUMBER
                else -> VarKind.PRIMITIVE
            }
        }
    }
}
