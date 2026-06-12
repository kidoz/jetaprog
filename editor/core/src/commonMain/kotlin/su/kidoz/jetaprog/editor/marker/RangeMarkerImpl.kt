package su.kidoz.jetaprog.editor.marker

import su.kidoz.jetaprog.common.text.OffsetRange
import su.kidoz.jetaprog.editor.buffer.DocumentEvent

/**
 * Implementation of [RangeMarker] that tracks a range in a document.
 *
 * This implementation automatically adjusts its offsets when the document
 * changes, following IntelliJ IDEA's behavior:
 *
 * - Text inserted before the marker shifts it right
 * - Text deleted before the marker shifts it left
 * - Text inserted within the marker expands it
 * - Text deleted within the marker shrinks it
 * - Deleting all text covered by the marker invalidates it
 * - Greedy settings control behavior at boundaries
 */
public class RangeMarkerImpl(
    initialStart: Int,
    initialEnd: Int,
    private val onDispose: ((RangeMarkerImpl) -> Unit)? = null,
) : RangeMarker {
    init {
        require(initialStart >= 0) { "Start offset must be non-negative: $initialStart" }
        require(initialEnd >= initialStart) { "End offset must be >= start: $initialEnd < $initialStart" }
    }

    private var _startOffset: Int = initialStart
    private var _endOffset: Int = initialEnd
    private var _isValid: Boolean = true

    override val startOffset: Int
        get() = if (_isValid) _startOffset else -1

    override val endOffset: Int
        get() = if (_isValid) _endOffset else -1

    override val isValid: Boolean
        get() = _isValid

    override var isGreedyToLeft: Boolean = false

    override var isGreedyToRight: Boolean = false

    override fun getRange(): OffsetRange =
        if (_isValid) {
            OffsetRange(_startOffset, _endOffset)
        } else {
            OffsetRange.Empty
        }

    override fun dispose() {
        if (_isValid) {
            _isValid = false
            onDispose?.invoke(this)
        }
    }

    /**
     * Updates this marker in response to a document change.
     *
     * @param event The document change event
     * @return true if the marker is still valid after the update
     */
    internal fun updateForChange(event: DocumentEvent): Boolean {
        if (!_isValid) return false

        val changeStart = event.offset
        val changeOldEnd = event.offset + event.oldLength
        val delta = event.lengthDelta

        // Case 1: Change is entirely after the marker - no effect
        if (changeStart >= _endOffset) {
            return true
        }

        // Case 2: Change is entirely before the marker - shift both offsets
        if (changeOldEnd <= _startOffset) {
            _startOffset += delta
            _endOffset += delta
            return true
        }

        // Case 3: Change encompasses the entire marker
        if (changeStart <= _startOffset && changeOldEnd >= _endOffset) {
            if (event.isDelete && event.newLength == 0) {
                // Entire marker deleted - invalidate
                _isValid = false
                return false
            }
            // Replacement - collapse to insertion point
            _startOffset = changeStart
            _endOffset = changeStart + event.newLength
            return true
        }

        // Case 4: Change starts before marker and ends within it
        if (changeStart < _startOffset && changeOldEnd > _startOffset && changeOldEnd < _endOffset) {
            val overlap = changeOldEnd - _startOffset
            _startOffset = changeStart + event.newLength
            _endOffset = _endOffset - overlap + delta
            return true
        }

        // Case 5: Change starts within marker and ends after it
        if (changeStart > _startOffset && changeStart < _endOffset && changeOldEnd > _endOffset) {
            val overlap = _endOffset - changeStart
            _endOffset = changeStart + event.newLength
            return true
        }

        // Case 6: Change is entirely within the marker - expand/shrink
        if (changeStart >= _startOffset && changeOldEnd <= _endOffset) {
            _endOffset += delta
            return true
        }

        // Case 7: Insertion at marker boundary
        if (event.isInsert) {
            if (changeStart == _startOffset && isGreedyToLeft) {
                // Expand left
                _endOffset += delta
                return true
            }
            if (changeStart == _endOffset && isGreedyToRight) {
                // Expand right
                _endOffset += delta
                return true
            }
            if (changeStart == _startOffset) {
                // Insert at start without greedy - shift marker
                _startOffset += delta
                _endOffset += delta
                return true
            }
            if (changeStart == _endOffset) {
                // Insert at end without greedy - no change
                return true
            }
        }

        return true
    }

    override fun toString(): String =
        if (_isValid) {
            "RangeMarker[$_startOffset, $_endOffset)" +
                (if (isGreedyToLeft) " <-" else "") +
                (if (isGreedyToRight) " ->" else "")
        } else {
            "RangeMarker[invalid]"
        }
}
