package su.kidoz.jetaprog.common.text

import kotlinx.serialization.Serializable

/**
 * Represents a position in a text document as line and column.
 * Both line and column are 0-based.
 *
 * This is implemented as a value class for memory efficiency,
 * packing both values into a single Long.
 */
@Serializable
@JvmInline
public value class TextPosition private constructor(
    private val packed: Long,
) : Comparable<TextPosition> {
    /**
     * The line number (0-based).
     */
    public val line: Int get() = (packed shr INT_BITS).toInt()

    /**
     * The column number (0-based).
     */
    public val column: Int get() = packed.toInt()

    override fun compareTo(other: TextPosition): Int {
        val lineCompare = line.compareTo(other.line)
        return if (lineCompare != 0) lineCompare else column.compareTo(other.column)
    }

    override fun toString(): String = "($line:$column)"

    /**
     * Returns a new position with the specified line.
     */
    public fun withLine(newLine: Int): TextPosition = invoke(newLine, column)

    /**
     * Returns a new position with the specified column.
     */
    public fun withColumn(newColumn: Int): TextPosition = invoke(line, newColumn)

    /**
     * Returns a new position offset by the specified amounts.
     */
    public fun offset(
        lineDelta: Int = 0,
        columnDelta: Int = 0,
    ): TextPosition = invoke(line + lineDelta, column + columnDelta)

    public companion object {
        private const val INT_BITS = 32

        /**
         * Position at line 0, column 0.
         */
        public val Zero: TextPosition = TextPosition(0L)

        /**
         * Creates a new TextPosition with the given line and column.
         * @param line The line number (0-based)
         * @param column The column number (0-based)
         */
        public operator fun invoke(
            line: Int,
            column: Int,
        ): TextPosition {
            require(line >= 0) { "Line must be non-negative: $line" }
            require(column >= 0) { "Column must be non-negative: $column" }
            return TextPosition((line.toLong() shl INT_BITS) or (column.toLong() and 0xFFFFFFFFL))
        }
    }
}
