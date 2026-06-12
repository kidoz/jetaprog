package su.kidoz.jetaprog.plugins.support.formatters

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit

/**
 * Default formatter that handles basic whitespace formatting.
 * Applies to any language without a specific formatter.
 */
public class DefaultFormatter : CodeFormatter {
    override val languageId: LanguageId = LanguageId.PLAIN_TEXT

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult {
        val formattedLines = mutableListOf<String>()
        val lines = content.lines()

        for (line in lines) {
            var formattedLine = line

            // Trim trailing whitespace if enabled
            if (options.trimTrailingWhitespace) {
                formattedLine = formattedLine.trimEnd()
            }

            // Convert tabs to spaces or vice versa
            formattedLine = convertIndentation(formattedLine, options)

            formattedLines.add(formattedLine)
        }

        var result = formattedLines.joinToString("\n")

        // Ensure final newline if enabled
        if (options.insertFinalNewline && result.isNotEmpty() && !result.endsWith("\n")) {
            result += "\n"
        }

        // Remove consecutive blank lines (keep at most 2)
        result = normalizeBlankLines(result)

        if (result == content) {
            return FormattingResult.Success(content, emptyList())
        }

        val edit =
            TextEdit(
                range = TextRange(TextPosition.Zero, positionAtEnd(content)),
                newText = result,
            )

        return FormattingResult.Success(result, listOf(edit))
    }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult {
        // For default formatter, format the whole document
        // More sophisticated formatters can implement range-specific formatting
        return format(content, options)
    }

    private fun convertIndentation(
        line: String,
        options: FormattingOptions,
    ): String {
        val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
        val rest = line.drop(leadingWhitespace.length)

        if (leadingWhitespace.isEmpty()) return line

        val indentLevel = calculateIndentLevel(leadingWhitespace, options.tabSize)
        val newIndent =
            if (options.insertSpaces) {
                " ".repeat(indentLevel * options.tabSize)
            } else {
                "\t".repeat(indentLevel)
            }

        return newIndent + rest
    }

    private fun calculateIndentLevel(
        whitespace: String,
        tabSize: Int,
    ): Int {
        var spaces = 0
        for (c in whitespace) {
            when (c) {
                ' ' -> spaces++
                '\t' -> spaces += tabSize
            }
        }
        return spaces / tabSize
    }

    private fun normalizeBlankLines(content: String): String {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var consecutiveBlankLines = 0

        for (line in lines) {
            if (line.isBlank()) {
                consecutiveBlankLines++
                if (consecutiveBlankLines <= 2) {
                    result.add(line)
                }
            } else {
                consecutiveBlankLines = 0
                result.add(line)
            }
        }

        return result.joinToString("\n")
    }

    private fun positionAtEnd(content: String): TextPosition {
        val lines = content.lines()
        return if (lines.isEmpty()) {
            TextPosition.Zero
        } else {
            TextPosition(lines.size - 1, lines.last().length)
        }
    }
}
