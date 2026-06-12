package su.kidoz.jetaprog.editor.buffer

/**
 * A piece table implementation for efficient text editing.
 *
 * The piece table maintains the original text and an "add buffer" for insertions.
 * Text is represented as a sequence of "pieces" that reference either the original
 * text or the add buffer. This allows O(1) insertions and efficient memory usage.
 *
 * Based on the data structure used in VS Code's editor.
 */
public class PieceTable private constructor(
    private val original: String,
    private val added: StringBuilder,
    private val pieces: List<Piece>,
) {
    /**
     * Represents a piece of text referencing either the original or added buffer.
     */
    public data class Piece(
        /**
         * The source buffer this piece references.
         */
        val source: Source,
        /**
         * The start offset in the source buffer.
         */
        val start: Int,
        /**
         * The length of this piece.
         */
        val length: Int,
    )

    /**
     * The source buffer type.
     */
    public enum class Source {
        ORIGINAL,
        ADDED,
    }

    /**
     * The total length of the text.
     */
    public val length: Int get() = pieces.sumOf { it.length }

    /**
     * Inserts text at the specified offset.
     * @param offset The offset at which to insert
     * @param text The text to insert
     * @return A new PieceTable with the text inserted
     */
    public fun insert(
        offset: Int,
        text: String,
    ): PieceTable {
        if (text.isEmpty()) return this
        require(offset in 0..length) { "Offset $offset out of bounds [0, $length]" }

        val newAdded = StringBuilder(added).append(text)
        val addedStart = added.length
        val newPiece = Piece(Source.ADDED, addedStart, text.length)

        val newPieces = mutableListOf<Piece>()
        var currentOffset = 0

        for (piece in pieces) {
            val pieceEnd = currentOffset + piece.length

            when {
                pieceEnd <= offset -> {
                    // Piece is entirely before insertion point
                    newPieces.add(piece)
                }

                currentOffset >= offset -> {
                    // Piece is entirely after insertion point
                    if (newPieces.lastOrNull()?.source != Source.ADDED ||
                        newPieces.last().start + newPieces.last().length != addedStart
                    ) {
                        newPieces.add(newPiece)
                    }
                    newPieces.add(piece)
                }

                else -> {
                    // Insertion point is within this piece - split it
                    val splitPoint = offset - currentOffset
                    if (splitPoint > 0) {
                        newPieces.add(Piece(piece.source, piece.start, splitPoint))
                    }
                    newPieces.add(newPiece)
                    val remaining = piece.length - splitPoint
                    if (remaining > 0) {
                        newPieces.add(Piece(piece.source, piece.start + splitPoint, remaining))
                    }
                }
            }
            currentOffset = pieceEnd
        }

        // Handle insertion at the end
        if (offset == length) {
            newPieces.add(newPiece)
        }

        return PieceTable(original, newAdded, mergePieces(newPieces))
    }

    /**
     * Deletes text at the specified range.
     * @param offset The start offset of the deletion
     * @param deleteLength The number of characters to delete
     * @return A new PieceTable with the text deleted
     */
    public fun delete(
        offset: Int,
        deleteLength: Int,
    ): PieceTable {
        if (deleteLength <= 0) return this
        require(offset >= 0 && offset + deleteLength <= length) {
            "Delete range [$offset, ${offset + deleteLength}] out of bounds [0, $length]"
        }

        val deleteEnd = offset + deleteLength
        val newPieces = mutableListOf<Piece>()
        var currentOffset = 0

        for (piece in pieces) {
            val pieceEnd = currentOffset + piece.length

            when {
                pieceEnd <= offset || currentOffset >= deleteEnd -> {
                    // Piece is entirely outside deletion range
                    newPieces.add(piece)
                }

                currentOffset >= offset && pieceEnd <= deleteEnd -> {
                    // Piece is entirely within deletion range - skip it
                }

                else -> {
                    // Piece partially overlaps with deletion range
                    if (currentOffset < offset) {
                        // Keep the part before the deletion
                        val keepLength = offset - currentOffset
                        newPieces.add(Piece(piece.source, piece.start, keepLength))
                    }
                    if (pieceEnd > deleteEnd) {
                        // Keep the part after the deletion
                        val skipLength = deleteEnd - currentOffset
                        val keepStart = piece.start + skipLength
                        val keepLength = pieceEnd - deleteEnd
                        newPieces.add(Piece(piece.source, keepStart, keepLength))
                    }
                }
            }
            currentOffset = pieceEnd
        }

        return PieceTable(original, added, mergePieces(newPieces))
    }

    /**
     * Gets the full text content.
     */
    public fun getText(): String {
        val builder = StringBuilder(length)
        for (piece in pieces) {
            val source =
                when (piece.source) {
                    Source.ORIGINAL -> original
                    Source.ADDED -> added
                }
            builder.append(source, piece.start, piece.start + piece.length)
        }
        return builder.toString()
    }

    /**
     * Gets a substring of the text.
     */
    public fun getText(
        start: Int,
        end: Int,
    ): String {
        require(start >= 0 && end <= length && start <= end) {
            "Range [$start, $end] invalid for length $length"
        }

        val builder = StringBuilder(end - start)
        var currentOffset = 0

        for (piece in pieces) {
            val pieceEnd = currentOffset + piece.length

            if (pieceEnd <= start) {
                currentOffset = pieceEnd
                continue
            }
            if (currentOffset >= end) break

            val source =
                when (piece.source) {
                    Source.ORIGINAL -> original
                    Source.ADDED -> added
                }

            val readStart = maxOf(0, start - currentOffset)
            val readEnd = minOf(piece.length, end - currentOffset)
            builder.append(source, piece.start + readStart, piece.start + readEnd)

            currentOffset = pieceEnd
        }

        return builder.toString()
    }

    /**
     * Gets a specific line (0-based).
     */
    public fun getLine(lineNumber: Int): String {
        val text = getText()
        val lines = text.lines()
        return if (lineNumber in lines.indices) lines[lineNumber] else ""
    }

    /**
     * Gets the number of lines.
     */
    public val lineCount: Int get() = getText().lines().size

    /**
     * Merges adjacent pieces from the same source.
     */
    private fun mergePieces(pieces: List<Piece>): List<Piece> {
        if (pieces.isEmpty()) return pieces

        val merged = mutableListOf<Piece>()
        var current = pieces.first()

        for (i in 1 until pieces.size) {
            val next = pieces[i]
            if (current.source == next.source && current.start + current.length == next.start) {
                current = Piece(current.source, current.start, current.length + next.length)
            } else {
                if (current.length > 0) merged.add(current)
                current = next
            }
        }
        if (current.length > 0) merged.add(current)

        return merged
    }

    public companion object {
        /**
         * Creates an empty PieceTable.
         */
        public fun empty(): PieceTable = PieceTable("", StringBuilder(), emptyList())

        /**
         * Creates a PieceTable from initial text.
         */
        public fun fromString(text: String): PieceTable =
            if (text.isEmpty()) {
                empty()
            } else {
                PieceTable(text, StringBuilder(), listOf(Piece(Source.ORIGINAL, 0, text.length)))
            }
    }
}
