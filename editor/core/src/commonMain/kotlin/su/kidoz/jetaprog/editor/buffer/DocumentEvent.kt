package su.kidoz.jetaprog.editor.buffer

import su.kidoz.jetaprog.common.text.OffsetRange

/**
 * Represents a change event in a document.
 *
 * This event is fired before and after document modifications, allowing
 * listeners and range markers to react to changes.
 *
 * Based on IntelliJ IDEA's DocumentEvent design.
 */
public data class DocumentEvent(
    /**
     * The offset where the change occurred.
     */
    val offset: Int,
    /**
     * The text that was removed (empty string for pure insertions).
     */
    val oldFragment: CharSequence,
    /**
     * The text that was inserted (empty string for pure deletions).
     */
    val newFragment: CharSequence,
    /**
     * The document version before the change.
     */
    val oldVersion: Int,
    /**
     * The modification sequence number.
     */
    val modificationStamp: Long,
) {
    /**
     * The length of the removed text.
     */
    public val oldLength: Int get() = oldFragment.length

    /**
     * The length of the inserted text.
     */
    public val newLength: Int get() = newFragment.length

    /**
     * The net change in document length (positive for growth, negative for shrink).
     */
    public val lengthDelta: Int get() = newLength - oldLength

    /**
     * The range that was affected by this change (before the change).
     */
    public val oldRange: OffsetRange get() = OffsetRange(offset, offset + oldLength)

    /**
     * The range that was affected by this change (after the change).
     */
    public val newRange: OffsetRange get() = OffsetRange(offset, offset + newLength)

    /**
     * Whether this change is a pure insertion (no text removed).
     */
    public val isInsert: Boolean get() = oldLength == 0 && newLength > 0

    /**
     * Whether this change is a pure deletion (no text added).
     */
    public val isDelete: Boolean get() = oldLength > 0 && newLength == 0

    /**
     * Whether this change is a replacement (both removal and insertion).
     */
    public val isReplace: Boolean get() = oldLength > 0 && newLength > 0

    /**
     * Whether this event represents the entire document being replaced.
     */
    public var isWholeTextReplaced: Boolean = false
        internal set

    override fun toString(): String =
        "DocumentEvent(offset=$offset, old='${oldFragment.take(20)}...' ($oldLength), " +
            "new='${newFragment.take(20)}...' ($newLength))"
}

/**
 * Listener interface for document changes.
 */
public interface DocumentListener {
    /**
     * Called before a document modification occurs.
     *
     * Implementations should NOT modify the document during this callback.
     *
     * @param event The event describing the upcoming change
     */
    public fun beforeDocumentChange(event: DocumentEvent) {}

    /**
     * Called after a document modification has occurred.
     *
     * Implementations should NOT modify the document during this callback.
     *
     * @param event The event describing the change that occurred
     */
    public fun documentChanged(event: DocumentEvent) {}

    /**
     * Called when bulk update mode starts.
     *
     * During bulk updates, individual change events may not be fired.
     * Use this to temporarily disable expensive processing.
     */
    public fun bulkUpdateStarting() {}

    /**
     * Called when bulk update mode ends.
     *
     * Re-enable any processing that was disabled during bulk update.
     */
    public fun bulkUpdateFinished() {}
}

/**
 * Interface for objects that support document listeners.
 */
public interface DocumentListenerProvider {
    /**
     * Adds a document listener.
     *
     * @param listener The listener to add
     */
    public fun addDocumentListener(listener: DocumentListener)

    /**
     * Removes a document listener.
     *
     * @param listener The listener to remove
     */
    public fun removeDocumentListener(listener: DocumentListener)
}
