package su.kidoz.jetaprog.editor.marker

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.text.OffsetRange

/**
 * A range marker tracks a text range within a document that automatically
 * adjusts as the document is modified.
 *
 * When text is inserted or deleted before the marker, the marker's offsets
 * shift accordingly. When text is deleted within the marker, the marker
 * shrinks. When the entire text spanning the marker is deleted, the marker
 * becomes invalid.
 *
 * Based on IntelliJ IDEA's RangeMarker design.
 *
 * @see <a href="https://github.com/JetBrains/intellij-community">IntelliJ Community</a>
 */
public interface RangeMarker : Disposable {
    /**
     * The start offset of the range in the document.
     * Returns -1 if the marker is invalid.
     */
    public val startOffset: Int

    /**
     * The end offset of the range in the document.
     * Returns -1 if the marker is invalid.
     */
    public val endOffset: Int

    /**
     * The length of the range (endOffset - startOffset).
     * Returns 0 if the marker is invalid.
     */
    public val length: Int
        get() = if (isValid) endOffset - startOffset else 0

    /**
     * Whether the marker is still valid.
     *
     * A marker becomes invalid when:
     * - The entire text spanning the marker is deleted
     * - The marker is explicitly disposed
     */
    public val isValid: Boolean

    /**
     * If true, text inserted at the marker's start position will extend
     * the marker to include the new text.
     *
     * Default is false (marker does not expand).
     */
    public var isGreedyToLeft: Boolean

    /**
     * If true, text inserted at the marker's end position will extend
     * the marker to include the new text.
     *
     * Default is false (marker does not expand).
     */
    public var isGreedyToRight: Boolean

    /**
     * Returns the range as an OffsetRange.
     * Returns an empty range at offset 0 if the marker is invalid.
     */
    public fun getRange(): OffsetRange

    /**
     * Disposes of this marker, making it invalid and removing it from
     * the document's marker list.
     *
     * This is optional but can help performance if you have many markers
     * that are no longer needed.
     */
    override fun dispose()
}

/**
 * Interface for objects that can create and manage range markers.
 */
public interface RangeMarkerProvider {
    /**
     * Creates a range marker for the given offset range.
     *
     * @param startOffset The start offset of the range
     * @param endOffset The end offset of the range
     * @return A new RangeMarker that will track this range
     */
    public fun createRangeMarker(
        startOffset: Int,
        endOffset: Int,
    ): RangeMarker

    /**
     * Creates a range marker from an OffsetRange.
     */
    public fun createRangeMarker(range: OffsetRange): RangeMarker =
        createRangeMarker(range.startOffset, range.endOffset)

    /**
     * Creates a range marker at a single position (zero-length range).
     */
    public fun createRangeMarker(offset: Int): RangeMarker = createRangeMarker(offset, offset)
}
