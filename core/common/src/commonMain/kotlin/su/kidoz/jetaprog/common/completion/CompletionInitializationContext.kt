package su.kidoz.jetaprog.common.completion

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Context created during completion initialization.
 *
 * When completion is triggered, a copy of the document is created with a
 * **dummy identifier** inserted at the caret position. This ensures the
 * parser always has a complete token to work with, enabling accurate
 * context analysis even in incomplete/broken code.
 *
 * Inspired by IntelliJ's `CompletionInitializationContext`.
 *
 * @param originalContent The original document content
 * @param position The cursor position where completion was triggered
 * @param languageId The language ID of the document
 */
public class CompletionInitializationContext(
    public val originalContent: String,
    public val position: TextPosition,
    public val languageId: String,
) {
    /**
     * The dummy identifier string inserted at the caret.
     *
     * Default is "JetaProgCompletionPlaceholder" but can be customized
     * per language (e.g., for languages where identifiers have different rules).
     */
    public var dummyIdentifier: String = DUMMY_IDENTIFIER

    /**
     * The offset of the start of the replaced range.
     */
    public var startOffset: Int = computeOffset(originalContent, position)

    /**
     * The offset of the end of the selection (usually same as startOffset).
     */
    public var selectionEndOffset: Int = startOffset

    /**
     * The offset of the end of the identifier at the caret.
     * When the caret is in the middle of a word, this is the end of that word.
     */
    public var identifierEndOffset: Int = computeIdentifierEnd(originalContent, startOffset)

    /**
     * The modified document content with the dummy identifier inserted.
     */
    public val modifiedContent: String
        get() {
            val before = originalContent.substring(0, startOffset)
            val after = originalContent.substring(identifierEndOffset)
            return before + dummyIdentifier + after
        }

    /**
     * The offset where the dummy identifier starts in the modified content.
     */
    public val dummyOffset: Int get() = startOffset

    /**
     * The line content at the cursor position in the original document.
     */
    public val currentLine: String
        get() {
            val lines = originalContent.lines()
            return if (position.line < lines.size) lines[position.line] else ""
        }

    /**
     * The text before the cursor on the current line.
     */
    public val textBeforeCursor: String
        get() {
            val line = currentLine
            return line.substring(0, position.column.coerceAtMost(line.length))
        }

    /**
     * Extracts the identifier prefix before the cursor.
     */
    public val identifierPrefix: String
        get() {
            val line = currentLine
            val col = position.column.coerceAtMost(line.length)
            var start = col - 1
            while (start >= 0 && isIdentifierPart(line[start])) {
                start--
            }
            return line.substring(start + 1, col)
        }

    public companion object {
        /**
         * The default dummy identifier string.
         */
        public const val DUMMY_IDENTIFIER: String = "JetaProgCompletionPlaceholder"

        private fun computeOffset(
            content: String,
            position: TextPosition,
        ): Int {
            var offset = 0
            val lines = content.lines()
            for (i in 0 until position.line.coerceAtMost(lines.size)) {
                offset += lines[i].length + 1 // +1 for newline
            }
            offset +=
                position.column.coerceAtMost(
                    if (position.line < lines.size) lines[position.line].length else 0,
                )
            return offset
        }

        private fun computeIdentifierEnd(
            content: String,
            startOffset: Int,
        ): Int {
            var end = startOffset
            while (end < content.length && isIdentifierPart(content[end])) {
                end++
            }
            return end
        }

        private fun isIdentifierPart(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
    }
}
