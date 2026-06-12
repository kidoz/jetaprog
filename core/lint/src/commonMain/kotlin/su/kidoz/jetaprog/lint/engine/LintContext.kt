package su.kidoz.jetaprog.lint.engine

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lint.config.LintConfiguration

/**
 * Context provided to lint rules during execution.
 *
 * Provides access to the code being analyzed and utility methods
 * for common operations.
 */
public interface LintContext {
    /**
     * The URI of the file being linted.
     */
    public val uri: String

    /**
     * The language ID of the file (e.g., "kotlin", "java").
     */
    public val languageId: String

    /**
     * The full content of the file.
     */
    public val content: String

    /**
     * The lint configuration in effect.
     */
    public val configuration: LintConfiguration

    /**
     * Whether the lint was triggered by a file save.
     */
    public val isOnSave: Boolean

    /**
     * The lines of the file content.
     */
    public val lines: List<String>

    /**
     * Gets the text at the specified range.
     */
    public fun getText(range: TextRange): String

    /**
     * Gets the line at the specified index (0-based).
     */
    public fun getLine(lineIndex: Int): String?

    /**
     * Converts an offset to a text position.
     */
    public fun offsetToPosition(offset: Int): TextPosition

    /**
     * Converts a text position to an offset.
     */
    public fun positionToOffset(position: TextPosition): Int

    /**
     * Creates a range from start to end offsets.
     */
    public fun rangeFromOffsets(
        startOffset: Int,
        endOffset: Int,
    ): TextRange

    /**
     * Finds all occurrences of a pattern in the content.
     */
    public fun findAll(regex: Regex): Sequence<MatchResult>

    /**
     * Checks if a position is within a comment.
     */
    public fun isInComment(position: TextPosition): Boolean

    /**
     * Checks if a position is within a string literal.
     */
    public fun isInString(position: TextPosition): Boolean
}

/**
 * Default implementation of [LintContext].
 */
public class DefaultLintContext(
    override val uri: String,
    override val languageId: String,
    override val content: String,
    override val configuration: LintConfiguration,
    override val isOnSave: Boolean = false,
) : LintContext {
    override val lines: List<String> by lazy {
        content.lines()
    }

    private val lineOffsets: List<Int> by lazy {
        buildList {
            add(0)
            var offset = 0
            for (line in lines) {
                offset += line.length + 1 // +1 for newline
                add(offset)
            }
        }
    }

    override fun getText(range: TextRange): String {
        val startOffset = positionToOffset(range.start)
        val endOffset = positionToOffset(range.end)
        return content.substring(startOffset.coerceAtLeast(0), endOffset.coerceAtMost(content.length))
    }

    override fun getLine(lineIndex: Int): String? = lines.getOrNull(lineIndex)

    override fun offsetToPosition(offset: Int): TextPosition {
        if (offset <= 0) return TextPosition.Zero
        if (offset >= content.length) {
            return TextPosition(lines.size - 1, lines.lastOrNull()?.length ?: 0)
        }

        val lineIndex = lineOffsets.indexOfLast { it <= offset }.coerceAtLeast(0)
        val column = offset - lineOffsets[lineIndex]
        return TextPosition(lineIndex, column)
    }

    override fun positionToOffset(position: TextPosition): Int {
        if (position.line < 0) return 0
        if (position.line >= lines.size) return content.length

        val lineStart = lineOffsets.getOrElse(position.line) { 0 }
        val lineLength = lines.getOrNull(position.line)?.length ?: 0
        return lineStart + position.column.coerceIn(0, lineLength)
    }

    override fun rangeFromOffsets(
        startOffset: Int,
        endOffset: Int,
    ): TextRange =
        TextRange(
            start = offsetToPosition(startOffset),
            end = offsetToPosition(endOffset),
        )

    override fun findAll(regex: Regex): Sequence<MatchResult> = regex.findAll(content)

    override fun isInComment(position: TextPosition): Boolean {
        // Basic implementation - language-specific rules should override
        val line = getLine(position.line) ?: return false
        val prefix = line.substring(0, position.column.coerceAtMost(line.length))

        // Check for line comments
        return when (languageId) {
            "kotlin", "java", "javascript", "typescript", "c", "cpp", "go", "rust", "swift" -> {
                prefix.contains("//")
            }

            "python", "ruby", "shell", "bash" -> {
                prefix.contains("#")
            }

            else -> {
                false
            }
        }
    }

    override fun isInString(position: TextPosition): Boolean {
        // Basic implementation - counts quotes before position
        val line = getLine(position.line) ?: return false
        val prefix = line.substring(0, position.column.coerceAtMost(line.length))

        var inString = false
        var i = 0
        while (i < prefix.length) {
            val char = prefix[i]
            if (char == '"' || char == '\'') {
                // Check for escape
                if (i > 0 && prefix[i - 1] == '\\') {
                    i++
                    continue
                }
                inString = !inString
            }
            i++
        }
        return inString
    }
}
