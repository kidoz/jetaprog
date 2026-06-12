package su.kidoz.jetaprog.editor.selection

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Represents a text selection in the editor.
 */
@Serializable
public data class Selection(
    /**
     * The anchor position (where the selection started).
     */
    val anchor: TextPosition,
    /**
     * The active position (where the cursor is, end of selection).
     */
    val active: TextPosition,
) {
    /**
     * Whether the selection is reversed (active before anchor).
     */
    public val isReversed: Boolean get() = active < anchor

    /**
     * Whether this selection is empty (anchor equals active).
     */
    public val isEmpty: Boolean get() = anchor == active

    /**
     * The start of the selection (smaller position).
     */
    public val start: TextPosition get() = if (isReversed) active else anchor

    /**
     * The end of the selection (larger position).
     */
    public val end: TextPosition get() = if (isReversed) anchor else active

    /**
     * Converts this selection to a TextRange.
     */
    public fun toRange(): TextRange = TextRange(start, end)

    /**
     * Returns a new selection with the active position moved.
     */
    public fun withActive(newActive: TextPosition): Selection = copy(active = newActive)

    /**
     * Expands the selection to include another position.
     */
    public fun expandTo(position: TextPosition): Selection = copy(active = position)

    /**
     * Returns a selection that spans from the start of this to the end of other.
     */
    public fun union(other: Selection): Selection =
        Selection(
            anchor = minOf(start, other.start),
            active = maxOf(end, other.end),
        )

    public companion object {
        /**
         * Creates a selection from a TextRange.
         */
        public fun fromRange(range: TextRange): Selection = Selection(range.start, range.end)

        /**
         * Creates a collapsed selection (cursor) at a position.
         */
        public fun at(position: TextPosition): Selection = Selection(position, position)

        /**
         * An empty selection at (0, 0).
         */
        public val Empty: Selection = at(TextPosition.Zero)
    }
}
