package su.kidoz.jetaprog.app.agent

/**
 * Why a checkpoint left a file untouched.
 */
internal enum class CheckpointSkip {
    /** The file no longer matches a state the agent or a restore left on disk. */
    CHANGED,

    /** The current contents could not be read, so a restore would be a guess. */
    UNREADABLE,

    /** The restore write or delete failed. */
    FAILED,
}

/**
 * The files a checkpoint can roll back, and the ones it must leave alone.
 *
 * @property restore Absolute paths paired with the contents to write back.
 * @property skipped Absolute paths that were not restored, with the reason.
 * @property commandsRan Whether a command ran at or after this checkpoint.
 */
internal data class CheckpointPlan(
    val restore: List<Pair<String, FileSnapshot>>,
    val skipped: List<Pair<String, CheckpointSkip>>,
    val commandsRan: Boolean,
)

/**
 * Per-user-message file baselines.
 *
 * Each user message starts a checkpoint. The first agent write to a path after
 * that message records the file as it was then. Restoring a checkpoint writes
 * those baselines back and leaves the transcript alone. A file is skipped when
 * its current contents are not a state the agent wrote or a previous restore
 * put back, which covers hand edits and files a command changed afterwards.
 * Baselines stay in memory for the live conversation and are separate from the
 * per-write stack used by Accept and Reject.
 */
internal class AgentMessageCheckpoints {
    private val baselines = LinkedHashMap<String, MutableMap<String, FileSnapshot>>()
    private val known = mutableMapOf<String, MutableSet<DiskState>>()
    private val commands = mutableSetOf<String>()

    /** Starts a checkpoint for [turnId]. A repeat id is ignored. */
    fun begin(turnId: String) {
        if (turnId in baselines) return
        baselines[turnId] = mutableMapOf()
    }

    /** Whether [turnId] has a checkpoint in this conversation. */
    fun has(turnId: String): Boolean = turnId in baselines

    /** Drops every checkpoint. */
    fun clear() {
        baselines.clear()
        known.clear()
        commands.clear()
    }

    /**
     * Records [before] as the baseline for [path] on the active checkpoint, and
     * remembers both [before] and [afterContent] as disk states the agent produced.
     *
     * A second write to the same path during the same message keeps the first baseline.
     */
    fun noteWrite(
        path: String,
        before: FileSnapshot,
        afterContent: String,
    ) {
        val turnId = baselines.keys.lastOrNull() ?: return
        baselines.getValue(turnId).putIfAbsent(path, before)
        remember(path, before)
        remember(path, FileSnapshot(existed = true, content = afterContent))
    }

    /** Remembers [snapshot] as the contents now on disk for [path]. */
    fun noteDisk(
        path: String,
        snapshot: FileSnapshot,
    ) {
        remember(path, snapshot)
    }

    /** Records that the active checkpoint ran a command. */
    fun noteCommand() {
        baselines.keys.lastOrNull()?.let { commands += it }
    }

    /**
     * User turns whose Restore action can roll a file back or report a command.
     */
    fun restorableTurnIds(): Set<String> {
        val ids = baselines.keys.toList()
        return ids
            .filterIndexed { index, _ ->
                ids.drop(index).any { id -> baselines.getValue(id).isNotEmpty() || id in commands }
            }.toSet()
    }

    /**
     * Absolute paths a restore of [turnId] might touch, or null when that message has no checkpoint.
     */
    fun candidatePaths(turnId: String): Set<String>? {
        val later = turnsSince(turnId) ?: return null
        return later.flatMap { baselines.getValue(it).keys }.toSet()
    }

    /**
     * Plans a restore of [turnId].
     *
     * [current] maps each candidate path to the file as it is now, or null when it cannot be read.
     * [dirty] is true when an editor buffer has unsaved edits for that path.
     * Returns null when [turnId] has no checkpoint.
     */
    fun plan(
        turnId: String,
        current: Map<String, FileSnapshot?>,
        dirty: (String) -> Boolean,
    ): CheckpointPlan? {
        val later = turnsSince(turnId) ?: return null
        val chosen = LinkedHashMap<String, FileSnapshot>()
        later.forEach { id ->
            baselines.getValue(id).forEach { (path, snapshot) -> chosen.putIfAbsent(path, snapshot) }
        }
        val restore = mutableListOf<Pair<String, FileSnapshot>>()
        val skipped = mutableListOf<Pair<String, CheckpointSkip>>()
        chosen.forEach { (path, snapshot) ->
            when {
                dirty(path) -> {
                    skipped += path to CheckpointSkip.CHANGED
                }

                else -> {
                    val now = current[path]
                    when {
                        now == null -> skipped += path to CheckpointSkip.UNREADABLE
                        !matches(path, now) -> skipped += path to CheckpointSkip.CHANGED
                        else -> restore += path to snapshot
                    }
                }
            }
        }
        return CheckpointPlan(
            restore = restore,
            skipped = skipped,
            commandsRan = later.any { it in commands },
        )
    }

    private fun turnsSince(turnId: String): List<String>? {
        val ids = baselines.keys.toList()
        val index = ids.indexOf(turnId)
        if (index < 0) return null
        return ids.drop(index)
    }

    private fun matches(
        path: String,
        snapshot: FileSnapshot,
    ): Boolean = known[path]?.contains(DiskState(snapshot.existed, snapshot.content)) == true

    private fun remember(
        path: String,
        snapshot: FileSnapshot,
    ) {
        known.getOrPut(path) { mutableSetOf() }.add(DiskState(snapshot.existed, snapshot.content))
    }

    private data class DiskState(
        val existed: Boolean,
        val content: String?,
    )
}

/**
 * One sentence list of what a checkpoint restore did.
 *
 * [restored] and the names in [skipped] are display names, not paths.
 */
internal fun checkpointNotice(
    restored: List<String>,
    skipped: List<Pair<String, CheckpointSkip>>,
    commandsRan: Boolean,
): String {
    val sentences = mutableListOf<String>()
    if (restored.isNotEmpty()) sentences += "Restored ${restored.joinToString(", ")}."
    skipped.forEach { (name, reason) ->
        sentences +=
            when (reason) {
                CheckpointSkip.CHANGED -> "Skipped $name because it changed after the agent wrote it."
                CheckpointSkip.UNREADABLE -> "Skipped $name because its current contents could not be read."
                CheckpointSkip.FAILED -> "Skipped $name because the file could not be restored."
            }
    }
    if (commandsRan) {
        sentences += "Commands ran after this message. Files they changed were left in place."
    }
    return sentences.joinToString(" ")
}
