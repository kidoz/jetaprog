package su.kidoz.jetaprog.lsp.server

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages document state for an embedded LSP server.
 *
 * Tracks open documents with their current content, version, and language.
 * Provides thread-safe access to document state.
 */
public interface DocumentManager {
    /**
     * All open documents, keyed by URI.
     */
    public val documents: StateFlow<Map<String, OpenDocument>>

    /**
     * Open a document and start tracking it.
     *
     * @param uri Document URI
     * @param languageId Language identifier
     * @param version Initial version number
     * @param text Initial document content
     */
    public fun open(
        uri: String,
        languageId: String,
        version: Int,
        text: String,
    )

    /**
     * Update a document's content.
     *
     * @param uri Document URI
     * @param version New version number
     * @param text New document content
     */
    public fun update(
        uri: String,
        version: Int,
        text: String,
    )

    /**
     * Close a document and stop tracking it.
     *
     * @param uri Document URI
     */
    public fun close(uri: String)

    /**
     * Get a document by URI.
     *
     * @param uri Document URI
     * @return The document or null if not found
     */
    public fun get(uri: String): OpenDocument?

    /**
     * Get all documents for a specific language.
     *
     * @param languageId Language identifier
     * @return List of documents with the given language
     */
    public fun getByLanguage(languageId: String): List<OpenDocument>

    /**
     * Check if a document is open.
     *
     * @param uri Document URI
     * @return True if the document is open
     */
    public fun isOpen(uri: String): Boolean = get(uri) != null

    /**
     * Get all open document URIs.
     *
     * @return Set of open document URIs
     */
    public fun getOpenUris(): Set<String> = documents.value.keys
}

/**
 * An open document tracked by the document manager.
 *
 * @property uri Document URI (e.g., "file:///path/to/file.kt")
 * @property languageId Language identifier (e.g., "kotlin", "java")
 * @property version Document version number (incremented on each change)
 * @property text Current document content
 */
public data class OpenDocument(
    val uri: String,
    val languageId: String,
    val version: Int,
    val text: String,
) {
    /**
     * Get the file path from the URI.
     *
     * @return File path or null if not a file URI
     */
    public fun toPath(): String? = if (uri.startsWith("file://")) uri.removePrefix("file://") else null

    /**
     * Get lines of the document.
     */
    public val lines: List<String> by lazy { text.lines() }

    /**
     * Get line count.
     */
    public val lineCount: Int get() = lines.size

    /**
     * Get a specific line (0-based index).
     *
     * @param index Line index
     * @return Line content or null if out of bounds
     */
    public fun getLine(index: Int): String? = lines.getOrNull(index)

    /**
     * Get text at a specific position.
     *
     * @param line Line index (0-based)
     * @param startColumn Start column (0-based)
     * @param endColumn End column (0-based, exclusive)
     * @return Text in the range or null if out of bounds
     */
    public fun getText(
        line: Int,
        startColumn: Int,
        endColumn: Int,
    ): String? {
        val lineContent = getLine(line) ?: return null
        val start = startColumn.coerceIn(0, lineContent.length)
        val end = endColumn.coerceIn(start, lineContent.length)
        return lineContent.substring(start, end)
    }

    /**
     * Get the word at a specific position.
     *
     * @param line Line index (0-based)
     * @param column Column index (0-based)
     * @return Word at position or null if no word
     */
    public fun getWordAt(
        line: Int,
        column: Int,
    ): String? {
        val lineContent = getLine(line) ?: return null
        if (column < 0 || column > lineContent.length) return null

        var start = column
        while (start > 0 && lineContent[start - 1].isWordChar()) {
            start--
        }

        var end = column
        while (end < lineContent.length && lineContent[end].isWordChar()) {
            end++
        }

        return if (start < end) lineContent.substring(start, end) else null
    }

    private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'

    /**
     * Convert a line/column position to a character offset.
     *
     * @param line Line index (0-based)
     * @param column Column index (0-based)
     * @return Character offset from start of document
     */
    public fun positionToOffset(
        line: Int,
        column: Int,
    ): Int {
        var offset = 0
        for (i in 0 until line.coerceAtMost(lines.size - 1)) {
            offset += lines[i].length + 1 // +1 for newline
        }
        val lineContent = lines.getOrNull(line) ?: return offset
        return offset + column.coerceAtMost(lineContent.length)
    }

    /**
     * Convert a character offset to a line/column position.
     *
     * @param offset Character offset from start of document
     * @return Pair of (line, column)
     */
    public fun offsetToPosition(offset: Int): Pair<Int, Int> {
        var remaining = offset
        for ((lineIndex, lineContent) in lines.withIndex()) {
            val lineLength = lineContent.length + 1 // +1 for newline
            if (remaining < lineLength) {
                return lineIndex to remaining.coerceAtMost(lineContent.length)
            }
            remaining -= lineLength
        }
        // Past end of document
        return (lines.size - 1).coerceAtLeast(0) to (lines.lastOrNull()?.length ?: 0)
    }
}
