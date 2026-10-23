package su.kidoz.jetaprog.app.agent

/**
 * Full-file ownership for review. A decision applies to all pending writes to one file.
 * Agent-supplied diff fragments are never used as restoration contents.
 */
internal class AgentFileReview {
    private val files = linkedMapOf<String, ReviewSnapshot>()

    /** Records a successful IDE-observed write, preserving the session and pending baselines. */
    fun record(
        path: String,
        before: FileSnapshot,
        after: FileSnapshot,
    ) {
        val previous = files[path]
        val continuous = previous == null || previous.after == before
        files[path] =
            ReviewSnapshot(
                baseline = previous?.baseline ?: before,
                pending = previous?.pending ?: before,
                after = after,
                canRevert = previous?.pending == null || (previous.canRevert && continuous),
                netKnown = (previous?.netKnown ?: true) && continuous,
            )
    }

    /** Returns the pending baseline only for an uninterrupted chain ending in [current]. */
    fun revertTarget(
        path: String,
        current: FileSnapshot,
    ): FileSnapshot? = files[path]?.takeIf { it.canRevert && it.after == current }?.pending

    /** Whether an IDE-owned pending baseline exists for [path]. */
    fun hasPending(path: String): Boolean = files[path]?.pending != null

    /** Acknowledges pending writes without changing the file or the session baseline. */
    fun keep(path: String) {
        files[path]?.let { files[path] = it.copy(pending = null) }
    }

    /** Records a successful restoration without discarding the session baseline. */
    fun reverted(
        path: String,
        restored: FileSnapshot,
    ) {
        files[path]?.let { files[path] = it.copy(pending = null, after = restored, canRevert = true) }
    }

    /** Removes ownership after a checkpoint restoration or another baseline change. */
    fun drop(path: String) {
        files.remove(path)
    }

    /** Full before/after pairs whose session totals can be computed without attributing intervening edits. */
    fun netContents(): Map<String, Pair<String?, String?>> =
        files.filterValues { it.netKnown }.mapValues { (_, file) -> file.baseline.content to file.after.content }

    /** Clears all review data when the conversation changes. */
    fun clear() {
        files.clear()
    }

    private data class ReviewSnapshot(
        val baseline: FileSnapshot,
        val pending: FileSnapshot?,
        val after: FileSnapshot,
        val canRevert: Boolean,
        val netKnown: Boolean,
    )
}
