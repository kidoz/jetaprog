package su.kidoz.jetaprog.editor.undo

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * A document snapshot captured for undo/redo.
 */
public data class EditSnapshot(
    /**
     * The full document content.
     */
    val content: String,
    /**
     * The cursor position associated with the snapshot.
     */
    val cursor: TextPosition = TextPosition.Zero,
)

/**
 * Snapshot-based undo/redo stack for a single document.
 *
 * Rapid sequential edits are coalesced into a single undo step when they fall
 * within [coalescingWindowMs] of the previously recorded edit, so a burst of
 * typing undoes as one unit. Programmatic edits (format, replace all) should
 * pass `coalesce = false` to [recordBeforeEdit] so they form their own step.
 */
public class UndoManager(
    private val maxStackSize: Int = DEFAULT_MAX_STACK_SIZE,
    private val coalescingWindowMs: Long = DEFAULT_COALESCING_WINDOW_MS,
) {
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var lastRecordTimeMs = NO_RECORD_TIME

    /**
     * Whether an undo step is available.
     */
    public val canUndo: Boolean get() = undoStack.isNotEmpty()

    /**
     * Whether a redo step is available.
     */
    public val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Record the document state [before] an edit is applied.
     *
     * Any pending redo history is discarded.
     *
     * @param before The snapshot prior to the edit.
     * @param nowMs Current time in milliseconds, used for coalescing.
     * @param coalesce Whether this edit may be merged with a recent previous edit.
     */
    public fun recordBeforeEdit(
        before: EditSnapshot,
        nowMs: Long,
        coalesce: Boolean = true,
    ) {
        redoStack.clear()
        val withinWindow =
            coalesce &&
                lastRecordTimeMs != NO_RECORD_TIME &&
                nowMs - lastRecordTimeMs <= coalescingWindowMs
        lastRecordTimeMs = if (coalesce) nowMs else NO_RECORD_TIME
        if (withinWindow && undoStack.isNotEmpty()) return
        undoStack.addLast(before)
        if (undoStack.size > maxStackSize) {
            undoStack.removeFirst()
        }
    }

    /**
     * Undo the most recent edit.
     *
     * @param current The current document snapshot, pushed onto the redo stack.
     * @return The snapshot to restore, or null when there is nothing to undo.
     */
    public fun undo(current: EditSnapshot): EditSnapshot? {
        val snapshot = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastRecordTimeMs = NO_RECORD_TIME
        return snapshot
    }

    /**
     * Redo the most recently undone edit.
     *
     * @param current The current document snapshot, pushed onto the undo stack.
     * @return The snapshot to restore, or null when there is nothing to redo.
     */
    public fun redo(current: EditSnapshot): EditSnapshot? {
        val snapshot = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastRecordTimeMs = NO_RECORD_TIME
        return snapshot
    }

    /**
     * Clear both stacks.
     */
    public fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastRecordTimeMs = NO_RECORD_TIME
    }

    private companion object {
        const val DEFAULT_MAX_STACK_SIZE = 200
        const val DEFAULT_COALESCING_WINDOW_MS = 500L
        const val NO_RECORD_TIME = Long.MIN_VALUE
    }
}
