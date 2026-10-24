package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.Immutable

/** Slash name of the read-only review command. */
internal const val REVIEW_COMMAND: String = "review"

/** Menu text used when the agent does not advertise its own review command. */
internal const val REVIEW_COMMAND_DESCRIPTION: String = "Review the diff"

/** Shown when Keep or Revert is used while a turn is still running. */
internal const val REVIEW_BUSY_NOTICE: String = "Finish the running task before keeping or reverting changes."

/**
 * One file in the session review.
 *
 * Counts describe the net recorded change when full-file baselines are available.
 *
 * @property key Stable identity, the absolute path when the diff has one.
 * @property fileName The file's display name.
 * @property pathLabel The parent path shown under the name.
 * @property added Net added lines, or null without a reliable baseline.
 * @property removed Net removed lines, or null without a reliable baseline.
 * @property pendingBlockIds Pending diff IDs; each action applies to all pending changes in this file.
 * @property statusLabel Settled text such as Kept, or null while a decision is still open.
 */
internal data class ReviewFile(
    val key: String,
    val fileName: String,
    val pathLabel: String,
    val added: Int?,
    val removed: Int?,
    val pendingBlockIds: List<String>,
    val statusLabel: String?,
    val conflict: String? = null,
)

/**
 * Reads a composer message as the review command.
 *
 * Only `/review` with no argument is the local command. `/review the diff` stays a prompt.
 */
internal fun parseReviewCommand(input: String): Boolean = exactSlashCommand(input, REVIEW_COMMAND)

/** Groups the session's diffs by file, in the order the files first appeared. */
internal fun reviewFiles(
    turns: List<Turn>,
    counts: Map<String, ReviewDelta> = emptyMap(),
    conflicts: Map<String, String> = emptyMap(),
): List<ReviewFile> {
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
            added = counts[key]?.added,
            removed = counts[key]?.removed,
            pendingBlockIds = pending,
            conflict = conflicts[key],
            statusLabel =
                when {
                    pending.isNotEmpty() -> null
                    decisions == setOf(DiffDecision.ACCEPTED) -> "Kept"
                    decisions == setOf(DiffDecision.REJECTED) -> "Reverted"
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

/** Net line totals from the first recorded file contents to the latest recorded contents. */
@Immutable
public data class ReviewDelta(
    /** Lines inserted relative to the recorded baseline. */
    val added: Int,
    /** Lines removed relative to the recorded baseline. */
    val removed: Int,
)

/** Exact line totals using bounded LCS; oversized comparisons remain explicitly unavailable. */
internal fun netReviewDelta(
    before: String?,
    after: String?,
): ReviewDelta? {
    val old = reviewLines(before)
    val new = reviewLines(after)
    var prefix = 0
    while (prefix < minOf(old.size, new.size) && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(old.size, new.size) - prefix &&
        old[old.lastIndex - suffix] == new[new.lastIndex - suffix]
    ) {
        suffix++
    }
    val a = old.subList(prefix, old.size - suffix)
    val b = new.subList(prefix, new.size - suffix)
    if (a.size.toLong() * b.size > MAX_REVIEW_COMPARISONS) return null
    val lengths = IntArray(b.size + 1)
    for (line in a) {
        var diagonal = 0
        b.forEachIndexed { index, other ->
            val previous = lengths[index + 1]
            lengths[index + 1] = if (line == other) diagonal + 1 else maxOf(lengths[index], previous)
            diagonal = previous
        }
    }
    val common = lengths.last()
    return ReviewDelta(added = b.size - common, removed = a.size - common)
}

private fun reviewLines(text: String?): List<String> {
    if (text.isNullOrEmpty()) return emptyList()
    val lines = text.split('\n')
    return lines.mapIndexedNotNull { index, line ->
        when {
            index < lines.lastIndex -> "$line\n"
            line.isNotEmpty() -> line
            else -> null
        }
    }
}

private const val MAX_REVIEW_COMPARISONS: Long = 1_000_000
