package su.kidoz.jetaprog.dap.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import su.kidoz.jetaprog.dap.client.DapClient
import su.kidoz.jetaprog.dap.protocol.DapBreakpoint
import su.kidoz.jetaprog.dap.protocol.DapSource
import su.kidoz.jetaprog.dap.protocol.DapSourceBreakpoint

/**
 * A breakpoint set by the user.
 */
public data class UserBreakpoint(
    /** The file path. */
    val filePath: String,
    /** The line number (1-based). */
    val line: Int,
    /** Optional condition expression. */
    val condition: String? = null,
    /** Optional hit condition. */
    val hitCondition: String? = null,
    /** Optional log message (logpoint). */
    val logMessage: String? = null,
    /** Whether the breakpoint is enabled. */
    val enabled: Boolean = true,
) {
    /**
     * Unique identifier for this breakpoint.
     */
    val id: String get() = "$filePath:$line"
}

/**
 * State of a breakpoint after being set in the debug adapter.
 */
public data class BreakpointState(
    /** The user breakpoint. */
    val userBreakpoint: UserBreakpoint,
    /** The DAP breakpoint information (if set). */
    val dapBreakpoint: DapBreakpoint? = null,
    /** Whether the breakpoint has been verified by the adapter. */
    val verified: Boolean = false,
    /** Error message if the breakpoint could not be set. */
    val errorMessage: String? = null,
)

/**
 * Manages breakpoints for a debug session.
 */
public class BreakpointManager(
    private val client: DapClient,
) {
    private val _breakpoints = MutableStateFlow<Map<String, BreakpointState>>(emptyMap())

    /**
     * All breakpoints and their states.
     */
    public val breakpoints: StateFlow<Map<String, BreakpointState>> = _breakpoints.asStateFlow()

    /**
     * Get all breakpoints for a file.
     */
    public fun getBreakpointsForFile(filePath: String): List<BreakpointState> =
        _breakpoints.value.values.filter { it.userBreakpoint.filePath == filePath }

    /**
     * Add a breakpoint.
     *
     * @param filePath The file path.
     * @param line The line number (1-based).
     * @param condition Optional condition expression.
     * @param hitCondition Optional hit condition.
     * @param logMessage Optional log message (logpoint).
     * @return The created breakpoint.
     */
    public suspend fun addBreakpoint(
        filePath: String,
        line: Int,
        condition: String? = null,
        hitCondition: String? = null,
        logMessage: String? = null,
    ): BreakpointState {
        val breakpoint =
            UserBreakpoint(
                filePath = filePath,
                line = line,
                condition = condition,
                hitCondition = hitCondition,
                logMessage = logMessage,
            )

        val state = BreakpointState(userBreakpoint = breakpoint)
        _breakpoints.update { it + (breakpoint.id to state) }

        // Sync with debug adapter
        syncBreakpointsForFile(filePath)

        return _breakpoints.value[breakpoint.id] ?: state
    }

    /**
     * Remove a breakpoint.
     *
     * @param filePath The file path.
     * @param line The line number.
     */
    public suspend fun removeBreakpoint(
        filePath: String,
        line: Int,
    ) {
        val id = "$filePath:$line"
        _breakpoints.update { it - id }

        // Sync with debug adapter
        syncBreakpointsForFile(filePath)
    }

    /**
     * Toggle a breakpoint.
     *
     * @param filePath The file path.
     * @param line The line number.
     * @return The new state, or null if the breakpoint was removed.
     */
    public suspend fun toggleBreakpoint(
        filePath: String,
        line: Int,
    ): BreakpointState? {
        val id = "$filePath:$line"
        val existing = _breakpoints.value[id]

        return if (existing != null) {
            removeBreakpoint(filePath, line)
            null
        } else {
            addBreakpoint(filePath, line)
        }
    }

    /**
     * Enable or disable a breakpoint.
     *
     * @param filePath The file path.
     * @param line The line number.
     * @param enabled Whether to enable the breakpoint.
     */
    public suspend fun setBreakpointEnabled(
        filePath: String,
        line: Int,
        enabled: Boolean,
    ) {
        val id = "$filePath:$line"
        _breakpoints.update { current ->
            val state = current[id] ?: return@update current
            current + (
                id to
                    state.copy(
                        userBreakpoint = state.userBreakpoint.copy(enabled = enabled),
                    )
            )
        }

        syncBreakpointsForFile(filePath)
    }

    /**
     * Update breakpoint condition.
     *
     * @param filePath The file path.
     * @param line The line number.
     * @param condition The new condition (null to remove).
     */
    public suspend fun updateBreakpointCondition(
        filePath: String,
        line: Int,
        condition: String?,
    ) {
        val id = "$filePath:$line"
        _breakpoints.update { current ->
            val state = current[id] ?: return@update current
            current + (
                id to
                    state.copy(
                        userBreakpoint = state.userBreakpoint.copy(condition = condition),
                    )
            )
        }

        syncBreakpointsForFile(filePath)
    }

    /**
     * Clear all breakpoints.
     */
    public suspend fun clearAll() {
        val files =
            _breakpoints.value.values
                .map { it.userBreakpoint.filePath }
                .distinct()
        _breakpoints.value = emptyMap()

        // Sync each file
        files.forEach { filePath ->
            syncBreakpointsForFile(filePath)
        }
    }

    /**
     * Synchronize all breakpoints with the debug adapter.
     */
    public suspend fun syncAllBreakpoints() {
        val files =
            _breakpoints.value.values
                .map { it.userBreakpoint.filePath }
                .distinct()
        files.forEach { filePath ->
            syncBreakpointsForFile(filePath)
        }
    }

    /**
     * Sync breakpoints for a specific file with the debug adapter.
     */
    private suspend fun syncBreakpointsForFile(filePath: String) {
        val fileBreakpoints =
            getBreakpointsForFile(filePath)
                .filter { it.userBreakpoint.enabled }

        val source =
            DapSource(
                path = filePath,
                name = filePath.substringAfterLast('/'),
            )

        val sourceBreakpoints =
            fileBreakpoints.map { state ->
                DapSourceBreakpoint(
                    line = state.userBreakpoint.line,
                    condition = state.userBreakpoint.condition,
                    hitCondition = state.userBreakpoint.hitCondition,
                    logMessage = state.userBreakpoint.logMessage,
                )
            }

        val result = client.setBreakpoints(source, sourceBreakpoints)

        result
            .onSuccess { dapBreakpoints ->
                // Update states with DAP breakpoint info
                _breakpoints.update { current ->
                    val updated = current.toMutableMap()

                    // Match up DAP breakpoints with user breakpoints
                    fileBreakpoints.forEachIndexed { index, state ->
                        val dapBp = dapBreakpoints.getOrNull(index)
                        updated[state.userBreakpoint.id] =
                            state.copy(
                                dapBreakpoint = dapBp,
                                verified = dapBp?.verified ?: false,
                                errorMessage = dapBp?.message,
                            )
                    }

                    updated
                }
            }.onFailure { error ->
                // Mark all breakpoints as unverified with error
                _breakpoints.update { current ->
                    val updated = current.toMutableMap()

                    fileBreakpoints.forEach { state ->
                        updated[state.userBreakpoint.id] =
                            state.copy(
                                verified = false,
                                errorMessage = error.message,
                            )
                    }

                    updated
                }
            }
    }

    /**
     * Handle breakpoint event from the debug adapter.
     */
    internal fun handleBreakpointEvent(
        dapBreakpoint: DapBreakpoint,
        reason: String,
    ) {
        val source = dapBreakpoint.source ?: return
        val path = source.path ?: return
        val line = dapBreakpoint.line ?: return

        val id = "$path:$line"

        when (reason) {
            "changed", "new" -> {
                _breakpoints.update { current ->
                    val existing = current[id]
                    if (existing != null) {
                        current + (
                            id to
                                existing.copy(
                                    dapBreakpoint = dapBreakpoint,
                                    verified = dapBreakpoint.verified,
                                    errorMessage = dapBreakpoint.message,
                                )
                        )
                    } else {
                        // Breakpoint created by adapter (not user)
                        val userBp = UserBreakpoint(filePath = path, line = line)
                        current + (
                            id to
                                BreakpointState(
                                    userBreakpoint = userBp,
                                    dapBreakpoint = dapBreakpoint,
                                    verified = dapBreakpoint.verified,
                                    errorMessage = dapBreakpoint.message,
                                )
                        )
                    }
                }
            }

            "removed" -> {
                _breakpoints.update { current ->
                    val existing = current[id] ?: return@update current
                    current + (
                        id to
                            existing.copy(
                                dapBreakpoint = null,
                                verified = false,
                            )
                    )
                }
            }
        }
    }
}
