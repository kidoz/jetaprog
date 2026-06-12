package su.kidoz.jetaprog.editor.cursor

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Represents a cursor in the editor.
 */
@Serializable
public data class Cursor(
    /**
     * The position of the cursor.
     */
    val position: TextPosition,
    /**
     * The anchor position for selections (null if no selection).
     */
    val anchor: TextPosition? = null,
) {
    /**
     * The selection start (smaller of position and anchor).
     */
    public val selectionStart: TextPosition
        get() = if (anchor != null && anchor < position) anchor else position

    /**
     * The selection end (larger of position and anchor).
     */
    public val selectionEnd: TextPosition
        get() = if (anchor != null && anchor > position) anchor else position

    /**
     * Whether this cursor has an active selection.
     */
    public val hasSelection: Boolean get() = anchor != null && anchor != position

    /**
     * Moves the cursor to a new position, clearing any selection.
     */
    public fun moveTo(newPosition: TextPosition): Cursor = copy(position = newPosition, anchor = null)

    /**
     * Moves the cursor to a new position while extending the selection.
     */
    public fun selectTo(newPosition: TextPosition): Cursor =
        copy(
            position = newPosition,
            anchor = anchor ?: position,
        )

    /**
     * Clears the selection while keeping the cursor at its current position.
     */
    public fun clearSelection(): Cursor = copy(anchor = null)

    /**
     * Creates a selection from the current position.
     */
    public fun startSelection(): Cursor = copy(anchor = position)

    public companion object {
        /**
         * A cursor at position (0, 0) with no selection.
         */
        public val Zero: Cursor = Cursor(TextPosition.Zero)
    }
}
