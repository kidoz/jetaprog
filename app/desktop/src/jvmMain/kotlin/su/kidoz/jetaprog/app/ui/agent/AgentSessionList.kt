package su.kidoz.jetaprog.app.ui.agent

import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentSessionStatus

/**
 * One row in the session list.
 *
 * @property id Saved record id, also used to restore or delete the session.
 * @property title Short title, usually the first user message.
 * @property savedAtEpochMillis Last save time, or the current time for a chat not saved yet.
 * @property status Live status for the open chat, otherwise the saved outcome.
 * @property addedLines Added lines in the session's diffs.
 * @property removedLines Removed lines in the session's diffs.
 * @property current Whether this row is the conversation open in the surface.
 */
internal data class SessionListRow(
    val id: String,
    val title: String,
    val savedAtEpochMillis: Long,
    val status: AgentSessionStatus,
    val addedLines: Int,
    val removedLines: Int,
    val current: Boolean,
)

/** Line counts and status derived from the turns currently on the surface. */
internal data class SessionSummary(
    val status: AgentSessionStatus,
    val addedLines: Int,
    val removedLines: Int,
)

/**
 * Summarizes the diffs and status of a conversation.
 *
 * [live] permits running permissions. Saved sessions retain review and failures,
 * and an unfinished turn becomes interrupted instead of appearing to keep running.
 */
internal fun summarizeSession(
    turns: List<Turn>,
    isStreaming: Boolean,
    error: String?,
    live: Boolean,
    interrupted: Boolean = false,
): SessionSummary {
    val blocks = turns.filterIsInstance<Turn.Agent>().flatMap { it.blocks }
    val diffs = blocks.filterIsInstance<Block.Diff>()
    val attention = agentAttention(turns, isStreaming, error, interrupted)
    val status =
        if (live) {
            attention.status
        } else {
            when {
                !error.isNullOrBlank() -> AgentSessionStatus.FAILED
                isStreaming || interrupted -> AgentSessionStatus.INTERRUPTED
                attention.pendingFiles > 0 -> AgentSessionStatus.REVIEW_READY
                else -> AgentSessionStatus.IDLE
            }
        }
    return SessionSummary(
        status = status,
        addedLines = diffs.sumOf { it.added },
        removedLines = diffs.sumOf { it.removed },
    )
}

/**
 * Title for a session, from the first user message.
 *
 * @return null when the conversation has no user text yet.
 */
internal fun sessionTitle(
    turns: List<Turn>,
    maxChars: Int = 40,
): String? {
    val text =
        turns
            .filterIsInstance<Turn.User>()
            .firstOrNull()
            ?.text
            ?.replace('\n', ' ')
            ?.trim()
            .orEmpty()
    if (text.isEmpty()) return null
    return if (text.length > maxChars) text.take(maxChars) + "…" else text
}

/** Short age label for a session row. */
internal fun relativeSessionTime(
    savedAtEpochMillis: Long,
    now: Long,
): String {
    val minutes = (now - savedAtEpochMillis).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}

/** Status word shown under the session title. */
internal fun sessionStatusLabel(status: AgentSessionStatus): String =
    when (status) {
        AgentSessionStatus.IDLE -> "Idle"
        AgentSessionStatus.RUNNING -> "Running"
        AgentSessionStatus.NEEDS_YOU -> "Needs you"
        AgentSessionStatus.WAITING_APPROVAL -> "Waiting for approval"
        AgentSessionStatus.REVIEW_READY -> "Review ready"
        AgentSessionStatus.INTERRUPTED -> "Interrupted"
        AgentSessionStatus.FAILED -> "Failed"
    }

/**
 * `+N −N` for a session that changed lines.
 *
 * @return null when both counts are zero.
 */
internal fun sessionDiffCountLabel(
    addedLines: Int,
    removedLines: Int,
): String? {
    if (addedLines == 0 && removedLines == 0) return null
    return buildString {
        if (addedLines > 0) append("+$addedLines")
        if (addedLines > 0 && removedLines > 0) append(' ')
        if (removedLines > 0) append("−$removedLines")
    }
}

/**
 * History rows for the popup.
 *
 * The open chat is listed first. Its status and counts come from the live turns.
 * A chat that has not been saved yet is still included.
 */
internal fun sessionListRows(
    history: List<AgentSessionRecord>,
    activeRecordId: String?,
    turns: List<Turn>,
    isStreaming: Boolean,
    error: String?,
    now: Long,
    interrupted: Boolean = false,
): List<SessionListRow> {
    val liveTitle = sessionTitle(turns)
    val live = summarizeSession(turns, isStreaming, error, live = true, interrupted = interrupted)
    val listed =
        history.map { record ->
            val current = liveTitle != null && record.id == activeRecordId
            if (current) {
                record.asRow(
                    status = live.status,
                    addedLines = live.addedLines,
                    removedLines = live.removedLines,
                    current = true,
                )
            } else {
                record.asRow(current = false)
            }
        }
    val withCurrent =
        if (liveTitle != null && activeRecordId != null && listed.none { it.current }) {
            listOf(
                SessionListRow(
                    id = activeRecordId,
                    title = liveTitle,
                    savedAtEpochMillis = now,
                    status = live.status,
                    addedLines = live.addedLines,
                    removedLines = live.removedLines,
                    current = true,
                ),
            ) + listed
        } else {
            listed
        }
    val currentRow = withCurrent.firstOrNull { it.current } ?: return withCurrent
    return listOf(currentRow) + withCurrent.filter { it.id != currentRow.id }
}

private fun AgentSessionRecord.asRow(
    status: AgentSessionStatus = this.status,
    addedLines: Int = this.addedLines,
    removedLines: Int = this.removedLines,
    current: Boolean,
): SessionListRow =
    SessionListRow(
        id = id,
        title = title,
        savedAtEpochMillis = savedAtEpochMillis,
        status = status,
        addedLines = addedLines,
        removedLines = removedLines,
        current = current,
    )
