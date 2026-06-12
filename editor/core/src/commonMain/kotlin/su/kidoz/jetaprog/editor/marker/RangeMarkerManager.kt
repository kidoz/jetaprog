package su.kidoz.jetaprog.editor.marker

import su.kidoz.jetaprog.common.text.OffsetRange
import su.kidoz.jetaprog.editor.buffer.DocumentEvent
import su.kidoz.jetaprog.editor.buffer.DocumentListener

/**
 * Manages a collection of range markers for a document.
 *
 * This class efficiently updates all markers when the document changes
 * and automatically removes invalid markers.
 *
 * Thread safety: This class is NOT thread-safe. All access should be
 * from a single thread (typically the EDT/main thread).
 */
public class RangeMarkerManager :
    RangeMarkerProvider,
    DocumentListener {
    private val markers = mutableListOf<RangeMarkerImpl>()
    private var documentLength: Int = 0

    /**
     * The number of active markers.
     */
    public val markerCount: Int get() = markers.size

    /**
     * Sets the current document length.
     * This should be called when the buffer is initialized or replaced.
     */
    public fun setDocumentLength(length: Int) {
        documentLength = length
    }

    override fun createRangeMarker(
        startOffset: Int,
        endOffset: Int,
    ): RangeMarker {
        require(startOffset >= 0) { "Start offset must be non-negative: $startOffset" }
        require(endOffset >= startOffset) { "End offset must be >= start: $endOffset < $startOffset" }
        require(endOffset <= documentLength) {
            "End offset ($endOffset) exceeds document length ($documentLength)"
        }

        val marker =
            RangeMarkerImpl(
                initialStart = startOffset,
                initialEnd = endOffset,
                onDispose = { m -> markers.remove(m) },
            )
        markers.add(marker)
        return marker
    }

    /**
     * Creates a range marker for the entire document.
     */
    public fun createWholeDocumentMarker(): RangeMarker = createRangeMarker(0, documentLength)

    /**
     * Gets all markers that intersect with the given range.
     */
    public fun getMarkersInRange(range: OffsetRange): List<RangeMarker> =
        markers.filter { marker ->
            marker.isValid && marker.getRange().overlaps(range)
        }

    /**
     * Gets all markers that contain the given offset.
     */
    public fun getMarkersAtOffset(offset: Int): List<RangeMarker> =
        markers.filter { marker ->
            marker.isValid && offset >= marker.startOffset && offset < marker.endOffset
        }

    /**
     * Gets all valid markers.
     */
    public fun getAllMarkers(): List<RangeMarker> = markers.filter { it.isValid }

    /**
     * Removes all markers.
     */
    public fun clear() {
        markers.forEach { it.dispose() }
        markers.clear()
    }

    /**
     * Removes all invalid markers from the internal list.
     * This is called automatically but can be invoked manually for cleanup.
     */
    public fun pruneInvalidMarkers() {
        markers.removeAll { !it.isValid }
    }

    // DocumentListener implementation

    override fun beforeDocumentChange(event: DocumentEvent) {
        // Markers are updated after the change, not before
    }

    override fun documentChanged(event: DocumentEvent) {
        updateMarkers(event)
        documentLength += event.lengthDelta
    }

    /**
     * Updates all markers in response to a document change.
     */
    private fun updateMarkers(event: DocumentEvent) {
        // Update all markers
        val invalidMarkers = mutableListOf<RangeMarkerImpl>()

        for (marker in markers) {
            val stillValid = marker.updateForChange(event)
            if (!stillValid) {
                invalidMarkers.add(marker)
            }
        }

        // Remove invalid markers
        markers.removeAll(invalidMarkers)
    }

    /**
     * Processes a batch of document changes.
     * This is more efficient than processing changes one at a time.
     */
    public fun processChanges(events: List<DocumentEvent>) {
        for (event in events) {
            documentChanged(event)
        }
        pruneInvalidMarkers()
    }
}
