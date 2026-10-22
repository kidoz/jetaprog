package su.kidoz.jetaprog.app.agent

/**
 * The state of a file immediately before an agent write.
 *
 * @property existed Whether the file existed before the write.
 * @property content The prior contents, or null when [existed] is false.
 */
internal data class FileSnapshot(
    val existed: Boolean,
    val content: String?,
)

/**
 * A per-path stack of pre-write snapshots used to revert agent edits.
 *
 * The ACP protocol supplies only diff hunks, not whole-file contents, so a
 * revert must come from an IDE-side snapshot. Snapshots are pushed before each
 * agent write and popped when the user accepts or rejects the matching diff;
 * the most recent write for a path is the one a decision applies to.
 */
internal class AgentFileSnapshots {
    private val snapshots = mutableMapOf<String, ArrayDeque<FileSnapshot>>()

    /** Records [snapshot] as the state before the next write to [path]. */
    fun push(
        path: String,
        snapshot: FileSnapshot,
    ) {
        snapshots.getOrPut(path) { ArrayDeque() }.addLast(snapshot)
    }

    /** Drops every snapshot for [path]. A checkpoint restore owns the file after this. */
    fun drop(path: String) {
        snapshots.remove(path)
    }

    /** Removes and returns the most recent snapshot for [path], if any. */
    fun pop(path: String): FileSnapshot? {
        val queue = snapshots[path] ?: return null
        val snapshot = queue.removeLastOrNull()
        if (queue.isEmpty()) snapshots.remove(path)
        return snapshot
    }

    /** Removes every snapshot. */
    fun clear() {
        snapshots.clear()
    }
}
