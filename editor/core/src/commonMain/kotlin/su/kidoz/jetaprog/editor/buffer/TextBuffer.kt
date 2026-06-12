package su.kidoz.jetaprog.editor.buffer

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Represents a text edit operation.
 */
public data class TextEdit(
    /**
     * The range to replace.
     */
    val range: TextRange,
    /**
     * The text to insert at the range.
     */
    val newText: String,
)

/**
 * Interface for a mutable text buffer.
 */
public interface TextBuffer {
    /**
     * The total number of characters in the buffer.
     */
    public val length: Int

    /**
     * The number of lines in the buffer.
     */
    public val lineCount: Int

    /**
     * Gets the full text content.
     */
    public fun getText(): String

    /**
     * Gets text within a range.
     */
    public fun getText(range: TextRange): String

    /**
     * Gets a specific line (0-based).
     */
    public fun getLine(lineNumber: Int): String

    /**
     * Gets the length of a specific line.
     */
    public fun getLineLength(lineNumber: Int): Int

    /**
     * Converts an offset to a position.
     */
    public fun offsetToPosition(offset: Int): TextPosition

    /**
     * Converts a position to an offset.
     */
    public fun positionToOffset(position: TextPosition): Int

    /**
     * Inserts text at a position.
     */
    public fun insert(
        position: TextPosition,
        text: String,
    )

    /**
     * Deletes text in a range.
     */
    public fun delete(range: TextRange)

    /**
     * Replaces text in a range with new text.
     */
    public fun replace(
        range: TextRange,
        newText: String,
    )

    /**
     * Applies a text edit.
     */
    public fun applyEdit(edit: TextEdit) {
        replace(edit.range, edit.newText)
    }

    /**
     * Applies multiple text edits (in reverse order to preserve positions).
     */
    public fun applyEdits(edits: List<TextEdit>) {
        edits.sortedByDescending { it.range.start }.forEach { applyEdit(it) }
    }
}

/**
 * A TextBuffer implementation backed by a PieceTable.
 */
public class PieceTableBuffer private constructor(
    private var pieceTable: PieceTable,
) : TextBuffer {
    private var cachedLineStarts: IntArray? = null

    override val length: Int get() = pieceTable.length

    override val lineCount: Int get() = getLineStarts().size

    override fun getText(): String = pieceTable.getText()

    override fun getText(range: TextRange): String {
        val start = positionToOffset(range.start)
        val end = positionToOffset(range.end)
        return pieceTable.getText(start, end)
    }

    override fun getLine(lineNumber: Int): String {
        val lineStarts = getLineStarts()
        if (lineNumber < 0 || lineNumber >= lineStarts.size) return ""

        val start = lineStarts[lineNumber]
        val end =
            if (lineNumber + 1 < lineStarts.size) {
                lineStarts[lineNumber + 1] - 1 // Exclude newline
            } else {
                length
            }
        return if (end > start) pieceTable.getText(start, end) else ""
    }

    override fun getLineLength(lineNumber: Int): Int {
        val lineStarts = getLineStarts()
        if (lineNumber < 0 || lineNumber >= lineStarts.size) return 0

        val start = lineStarts[lineNumber]
        val end =
            if (lineNumber + 1 < lineStarts.size) {
                lineStarts[lineNumber + 1] - 1
            } else {
                length
            }
        return maxOf(0, end - start)
    }

    override fun offsetToPosition(offset: Int): TextPosition {
        val lineStarts = getLineStarts()
        val clampedOffset = offset.coerceIn(0, length)

        var line = lineStarts.binarySearch(clampedOffset)
        if (line < 0) {
            line = -line - 2
        }
        line = line.coerceAtLeast(0)

        val column = clampedOffset - lineStarts[line]
        return TextPosition(line, column)
    }

    override fun positionToOffset(position: TextPosition): Int {
        val lineStarts = getLineStarts()
        val line = position.line.coerceIn(0, lineStarts.size - 1)
        val lineStart = lineStarts[line]
        val lineLen = getLineLength(line)
        val column = position.column.coerceIn(0, lineLen)
        return lineStart + column
    }

    override fun insert(
        position: TextPosition,
        text: String,
    ) {
        val offset = positionToOffset(position)
        pieceTable = pieceTable.insert(offset, text)
        invalidateLineStarts()
    }

    override fun delete(range: TextRange) {
        val start = positionToOffset(range.start)
        val end = positionToOffset(range.end)
        if (end > start) {
            pieceTable = pieceTable.delete(start, end - start)
            invalidateLineStarts()
        }
    }

    override fun replace(
        range: TextRange,
        newText: String,
    ) {
        delete(range)
        insert(range.start, newText)
    }

    private fun getLineStarts(): IntArray {
        cachedLineStarts?.let { return it }

        val text = pieceTable.getText()
        val starts = mutableListOf(0)

        var i = 0
        while (i < text.length) {
            when {
                text[i] == '\r' && i + 1 < text.length && text[i + 1] == '\n' -> {
                    starts.add(i + 2)
                    i += 2
                }

                text[i] == '\n' || text[i] == '\r' -> {
                    starts.add(i + 1)
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        return starts.toIntArray().also { cachedLineStarts = it }
    }

    private fun invalidateLineStarts() {
        cachedLineStarts = null
    }

    public companion object {
        /**
         * Creates an empty buffer.
         */
        public fun empty(): PieceTableBuffer = PieceTableBuffer(PieceTable.empty())

        /**
         * Creates a buffer from initial text.
         */
        public fun fromString(text: String): PieceTableBuffer = PieceTableBuffer(PieceTable.fromString(text))
    }
}
