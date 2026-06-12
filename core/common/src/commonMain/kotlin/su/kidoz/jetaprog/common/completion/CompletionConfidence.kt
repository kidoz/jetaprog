package su.kidoz.jetaprog.common.completion

import su.kidoz.jetaprog.common.text.TextPosition

/**
 * Confidence level for auto-popup completion.
 *
 * Controls whether the completion popup should automatically appear
 * when the user types in certain contexts.
 */
public enum class ConfidenceLevel {
    /**
     * Definitely skip auto-popup in this context.
     * Used for contexts where completion is inappropriate (strings, comments).
     */
    SKIP_AUTOPOPUP,

    /**
     * Normal confidence -- let the default behavior decide.
     */
    NORMAL,

    /**
     * High confidence -- auto-popup should definitely appear.
     * Used for known trigger contexts (after '.', ':', etc.).
     */
    HIGH,
}

/**
 * Evaluates whether auto-popup completion should be triggered at a position.
 *
 * Implementations inspect the document context to determine if showing
 * the completion popup automatically is appropriate. For example,
 * completions should not auto-popup inside string literals or comments.
 *
 * Inspired by IntelliJ's `CompletionConfidence`.
 */
public interface CompletionConfidence {
    /**
     * Evaluates whether auto-popup should be triggered at the given position.
     *
     * @param content The document content
     * @param position The cursor position
     * @param languageId The language ID of the document
     * @return The confidence level for auto-popup
     */
    public fun shouldSkipAutoPopup(
        content: String,
        position: TextPosition,
        languageId: String,
    ): ConfidenceLevel
}

/**
 * Default [CompletionConfidence] that suppresses auto-popup in strings and comments.
 *
 * Uses simple heuristics to detect string/comment contexts:
 * - Counts unescaped quotes to detect string literals
 * - Detects line comments (// and #)
 * - Detects block comment regions
 */
public class DefaultCompletionConfidence : CompletionConfidence {
    override fun shouldSkipAutoPopup(
        content: String,
        position: TextPosition,
        languageId: String,
    ): ConfidenceLevel {
        val lines = content.lines()
        if (position.line >= lines.size) return ConfidenceLevel.NORMAL

        val line = lines[position.line]
        val col = position.column.coerceAtMost(line.length)

        // Check if inside a line comment
        if (isInLineComment(line, col, languageId)) return ConfidenceLevel.SKIP_AUTOPOPUP

        // Check if inside a string literal
        if (isInString(line, col)) return ConfidenceLevel.SKIP_AUTOPOPUP

        // Check if inside a block comment
        if (isInBlockComment(content, position)) return ConfidenceLevel.SKIP_AUTOPOPUP

        return ConfidenceLevel.NORMAL
    }

    private fun isInLineComment(
        line: String,
        col: Int,
        languageId: String,
    ): Boolean {
        val commentPrefixes =
            when (languageId) {
                "python" -> listOf("#")
                else -> listOf("//")
            }
        val textBeforeCursor = line.substring(0, col)
        return commentPrefixes.any { prefix ->
            val idx = textBeforeCursor.indexOf(prefix)
            idx >= 0 && !isInsideString(textBeforeCursor, idx)
        }
    }

    private fun isInString(
        line: String,
        col: Int,
    ): Boolean {
        val textBeforeCursor = line.substring(0, col)
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        while (i < textBeforeCursor.length) {
            val ch = textBeforeCursor[i]
            if (ch == '\\') {
                i += 2 // skip escaped character
                continue
            }
            when (ch) {
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            }
            i++
        }
        return inSingleQuote || inDoubleQuote
    }

    private fun isInsideString(
        text: String,
        upTo: Int,
    ): Boolean {
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        while (i < upTo) {
            val ch = text[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            when (ch) {
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            }
            i++
        }
        return inSingleQuote || inDoubleQuote
    }

    private fun isInBlockComment(
        content: String,
        position: TextPosition,
    ): Boolean {
        val lines = content.lines()
        var inBlock = false
        for (lineIdx in 0..position.line) {
            val line = lines.getOrNull(lineIdx) ?: break
            val endCol = if (lineIdx == position.line) position.column.coerceAtMost(line.length) else line.length
            var i = 0
            while (i < endCol) {
                if (!inBlock && i + 1 < line.length && line[i] == '/' && line[i + 1] == '*') {
                    inBlock = true
                    i += 2
                    continue
                }
                if (inBlock && i + 1 < line.length && line[i] == '*' && line[i + 1] == '/') {
                    inBlock = false
                    i += 2
                    continue
                }
                i++
            }
        }
        return inBlock
    }
}
