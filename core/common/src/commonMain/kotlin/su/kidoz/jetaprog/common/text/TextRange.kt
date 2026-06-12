package su.kidoz.jetaprog.common.text

import kotlinx.serialization.Serializable

/**
 * Represents a range of text from a start position to an end position.
 * The start position is inclusive, and the end position is exclusive.
 */
@Serializable
public data class TextRange(
    /**
     * The start position of the range (inclusive).
     */
    val start: TextPosition,
    /**
     * The end position of the range (exclusive).
     */
    val end: TextPosition,
) {
    init {
        require(start <= end) { "Start position must be before or equal to end position: $start > $end" }
    }

    /**
     * Returns true if this range is empty (start equals end).
     */
    public val isEmpty: Boolean get() = start == end

    /**
     * Returns true if this range spans multiple lines.
     */
    public val isMultiLine: Boolean get() = start.line != end.line

    /**
     * Returns the number of lines this range spans.
     */
    public val lineCount: Int get() = end.line - start.line + 1

    /**
     * Returns true if this range contains the given position.
     */
    public operator fun contains(position: TextPosition): Boolean = position >= start && position < end

    /**
     * Returns true if this range contains the given range.
     */
    public operator fun contains(other: TextRange): Boolean = other.start >= start && other.end <= end

    /**
     * Returns true if this range overlaps with the given range.
     */
    public fun overlaps(other: TextRange): Boolean = start < other.end && end > other.start

    /**
     * Returns the intersection of this range with another, or null if they don't overlap.
     */
    public fun intersect(other: TextRange): TextRange? {
        if (!overlaps(other)) return null
        return TextRange(
            start = maxOf(start, other.start),
            end = minOf(end, other.end),
        )
    }

    /**
     * Returns the union of this range with another, covering both ranges.
     */
    public fun union(other: TextRange): TextRange =
        TextRange(
            start = minOf(start, other.start),
            end = maxOf(end, other.end),
        )

    override fun toString(): String = "[$start..$end)"

    public companion object {
        /**
         * An empty range at position (0, 0).
         */
        public val Empty: TextRange = TextRange(TextPosition.Zero, TextPosition.Zero)

        /**
         * Creates a range for a single position (empty range at that position).
         */
        public fun at(position: TextPosition): TextRange = TextRange(position, position)

        /**
         * Creates a range for a single line.
         */
        public fun line(lineNumber: Int): TextRange =
            TextRange(
                start = TextPosition(lineNumber, 0),
                end = TextPosition(lineNumber + 1, 0),
            )
    }
}
