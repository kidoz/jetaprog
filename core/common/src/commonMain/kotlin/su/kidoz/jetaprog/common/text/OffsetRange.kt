package su.kidoz.jetaprog.common.text

import kotlinx.serialization.Serializable

/**
 * Represents a range of text using character offsets.
 *
 * This is useful for operations that work with character positions rather than
 * line/column positions. The range is inclusive of startOffset and exclusive
 * of endOffset.
 */
@Serializable
public data class OffsetRange(
    /**
     * The start offset of the range (inclusive).
     */
    val startOffset: Int,
    /**
     * The end offset of the range (exclusive).
     */
    val endOffset: Int,
) {
    init {
        require(startOffset >= 0) { "Start offset must be non-negative: $startOffset" }
        require(endOffset >= startOffset) { "End offset must be >= start offset: $endOffset < $startOffset" }
    }

    /**
     * The length of the range.
     */
    public val length: Int get() = endOffset - startOffset

    /**
     * Returns true if this range is empty (zero length).
     */
    public val isEmpty: Boolean get() = startOffset == endOffset

    /**
     * Returns true if this range contains the given offset.
     */
    public operator fun contains(offset: Int): Boolean = offset >= startOffset && offset < endOffset

    /**
     * Returns true if this range fully contains another range.
     */
    public operator fun contains(other: OffsetRange): Boolean =
        other.startOffset >= startOffset && other.endOffset <= endOffset

    /**
     * Returns true if this range overlaps with another range.
     */
    public fun overlaps(other: OffsetRange): Boolean = startOffset < other.endOffset && endOffset > other.startOffset

    /**
     * Returns true if this range intersects with another range (touching counts as intersecting).
     */
    public fun intersects(other: OffsetRange): Boolean =
        startOffset <= other.endOffset && endOffset >= other.startOffset

    /**
     * Returns the intersection of this range with another, or null if they don't overlap.
     */
    public fun intersect(other: OffsetRange): OffsetRange? {
        if (!overlaps(other)) return null
        return OffsetRange(
            startOffset = maxOf(startOffset, other.startOffset),
            endOffset = minOf(endOffset, other.endOffset),
        )
    }

    /**
     * Returns the union of this range with another.
     */
    public fun union(other: OffsetRange): OffsetRange =
        OffsetRange(
            startOffset = minOf(startOffset, other.startOffset),
            endOffset = maxOf(endOffset, other.endOffset),
        )

    /**
     * Shifts this range by the given delta.
     */
    public fun shift(delta: Int): OffsetRange = OffsetRange(startOffset + delta, endOffset + delta)

    override fun toString(): String = "[$startOffset, $endOffset)"

    public companion object {
        /**
         * An empty range at offset 0.
         */
        public val Empty: OffsetRange = OffsetRange(0, 0)

        /**
         * Creates a range at a single offset (empty range).
         */
        public fun at(offset: Int): OffsetRange = OffsetRange(offset, offset)

        /**
         * Creates a range from offset with a given length.
         */
        public fun fromLength(
            startOffset: Int,
            length: Int,
        ): OffsetRange = OffsetRange(startOffset, startOffset + length)
    }
}
