package su.kidoz.jetaprog.app.ui.agent

/** Slash name of the read-only review command. */
internal const val REVIEW_COMMAND: String = "review"

/** Menu text used when the agent does not advertise its own review command. */
internal const val REVIEW_COMMAND_DESCRIPTION: String = "Review the diff"

/** Shown when `/review` finds no file changes in this session. */
internal const val REVIEW_EMPTY_NOTICE: String = "No session changes to review."

/** Shown when Accept or Dismiss is used while a turn is still running. */
internal const val REVIEW_BUSY_NOTICE: String = "Finish the running task before accepting or dismissing changes."

/**
 * One file in the session review.
 *
 * Counts add every hunk. [pendingBlockIds] are the hunks the user can still accept or dismiss.
 *
 * @property key Stable identity, the absolute path when the diff has one.
 * @property fileName The file's display name.
 * @property pathLabel The parent path shown under the name.
 * @property added Added lines across the file's hunks.
 * @property removed Removed lines across the file's hunks.
 * @property pendingBlockIds Hunks that are still proposed, latest first so a revert undoes the last write first.
 * @property statusLabel Settled text such as Accepted, or null while a decision is still open.
 */
internal data class ReviewFile(
    val key: String,
    val fileName: String,
    val pathLabel: String,
    val added: Int,
    val removed: Int,
    val pendingBlockIds: List<String>,
    val statusLabel: String?,
)

/**
 * Reads a composer message as the review command.
 *
 * Only `/review` with no argument is the local command. `/review the diff` stays a prompt.
 */
internal fun parseReviewCommand(input: String): Boolean = exactSlashCommand(input, REVIEW_COMMAND)

/** Groups the session's diffs by file, in the order the files first appeared. */
internal fun reviewFiles(turns: List<Turn>): List<ReviewFile> {
    val grouped = linkedMapOf<String, MutableList<Block.Diff>>()
    turns.filterIsInstance<Turn.Agent>().forEach { turn ->
        turn.blocks.filterIsInstance<Block.Diff>().forEach { diff ->
            val key = diff.absolutePath.ifBlank { diff.fileName }
            if (key.isNotBlank()) grouped.getOrPut(key) { mutableListOf() }.add(diff)
        }
    }
    return grouped.map { (key, hunks) ->
        val pending = hunks.filter { it.decision == DiffDecision.PROPOSED }.map { it.id }.asReversed()
        val decisions = hunks.map { it.decision }.toSet()
        val first = hunks.first()
        ReviewFile(
            key = key,
            fileName = first.fileName.ifBlank { key.substringAfterLast('/').substringAfterLast('\\') },
            pathLabel = first.path,
            added = hunks.sumOf { it.added },
            removed = hunks.sumOf { it.removed },
            pendingBlockIds = pending,
            statusLabel =
                when {
                    pending.isNotEmpty() -> null
                    decisions == setOf(DiffDecision.ACCEPTED) -> "Accepted"
                    decisions == setOf(DiffDecision.REJECTED) -> "Dismissed"
                    else -> "Reviewed"
                },
        )
    }
}

private fun exactSlashCommand(
    input: String,
    name: String,
): Boolean {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return false
    return trimmed.removePrefix("/").trim().equals(name, ignoreCase = true)
}
