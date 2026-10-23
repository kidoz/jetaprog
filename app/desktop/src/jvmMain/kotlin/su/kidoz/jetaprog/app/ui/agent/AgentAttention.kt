package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentSessionStatus

/** Execution and user attention are separate: a running turn can be waiting for permission. */
internal data class AgentAttention(
    val execution: AgentExecution,
    val approval: Block.Approval?,
    val pendingFiles: Int,
    val failure: String?,
) {
    val status: AgentSessionStatus
        get() =
            when {
                approval != null -> AgentSessionStatus.WAITING_APPROVAL
                failure != null -> AgentSessionStatus.FAILED
                execution == AgentExecution.RUNNING -> AgentSessionStatus.RUNNING
                execution == AgentExecution.INTERRUPTED -> AgentSessionStatus.INTERRUPTED
                pendingFiles > 0 -> AgentSessionStatus.REVIEW_READY
                else -> AgentSessionStatus.IDLE
            }
}

/** Lifecycle of the current turn, independent of its pending file decisions. */
internal enum class AgentExecution { IDLE, RUNNING, INTERRUPTED, FAILED }

/** Reads explicit protocol/UI state; prose is never interpreted as a question or completion signal. */
internal fun agentAttention(
    turns: List<Turn>,
    isStreaming: Boolean,
    error: String?,
    interrupted: Boolean = false,
): AgentAttention {
    val failure = error?.takeIf { it.isNotBlank() }
    val blocks = turns.filterIsInstance<Turn.Agent>().flatMap { it.blocks }
    return AgentAttention(
        execution =
            when {
                isStreaming -> AgentExecution.RUNNING
                failure != null -> AgentExecution.FAILED
                interrupted -> AgentExecution.INTERRUPTED
                else -> AgentExecution.IDLE
            },
        approval =
            if (isStreaming) {
                blocks.filterIsInstance<Block.Approval>().firstOrNull {
                    !it.resolved
                }
            } else {
                null
            },
        pendingFiles =
            blocks
                .filterIsInstance<Block.Diff>()
                .filter { it.decision == DiffDecision.PROPOSED }
                .distinctBy { it.absolutePath.ifBlank { it.fileName } }
                .size,
        failure = failure,
    )
}
