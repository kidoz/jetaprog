package su.kidoz.jetaprog.plugins.support.formatters

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit

/**
 * YAML formatter that normalizes indentation and formatting.
 */
public class YamlFormatter : CodeFormatter {
    override val languageId: LanguageId = LanguageId.YAML

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult =
        try {
            val formatted = formatYaml(content, options)
            val finalContent =
                if (options.insertFinalNewline && !formatted.endsWith("\n")) {
                    formatted + "\n"
                } else {
                    formatted
                }

            if (finalContent == content) {
                FormattingResult.Success(content, emptyList())
            } else {
                val edit =
                    TextEdit(
                        range = TextRange(TextPosition.Zero, positionAtEnd(content)),
                        newText = finalContent,
                    )
                FormattingResult.Success(finalContent, listOf(edit))
            }
        } catch (e: Exception) {
            FormattingResult.Failure("YAML formatting error: ${e.message}")
        }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult = format(content, options)

    private fun formatYaml(
        content: String,
        options: FormattingOptions,
    ): String {
        val indent =
            if (options.insertSpaces) {
                " ".repeat(options.tabSize)
            } else {
                "\t"
            }

        val lines = content.lines()
        val result = mutableListOf<String>()

        for (line in lines) {
            // Preserve empty lines
            if (line.isBlank()) {
                result.add("")
                continue
            }

            // Calculate current indentation
            val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
            val trimmedLine = line.trimStart()

            // Skip comments - preserve as-is with normalized indentation
            if (trimmedLine.startsWith("#")) {
                val indentLevel = calculateIndentLevel(leadingWhitespace, options.tabSize)
                result.add(indent.repeat(indentLevel) + trimmedLine)
                continue
            }

            // Handle document markers (---, ...)
            if (trimmedLine == "---" || trimmedLine == "...") {
                result.add(trimmedLine)
                continue
            }

            // Normalize indentation
            val indentLevel = calculateIndentLevel(leadingWhitespace, options.tabSize)
            var formattedLine = indent.repeat(indentLevel) + trimmedLine

            // Normalize spacing around colons in mappings (key: value)
            formattedLine = normalizeColonSpacing(formattedLine)

            // Trim trailing whitespace
            if (options.trimTrailingWhitespace) {
                formattedLine = formattedLine.trimEnd()
            }

            result.add(formattedLine)
        }

        // Remove trailing empty lines
        while (result.isNotEmpty() && result.last().isBlank()) {
            result.removeAt(result.lastIndex)
        }

        return result.joinToString("\n")
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
        // YAML typically uses 2-space indentation
        return spaces / 2
    }

    private fun normalizeColonSpacing(line: String): String {
        // Don't modify lines with quoted strings or that are entirely comments
        if (line.trimStart().startsWith("#") ||
            line.contains("\"") ||
            line.contains("'")
        ) {
            return line
        }

        // Find the first colon that's part of a key-value pair
        val colonIndex = line.indexOf(':')
        if (colonIndex == -1 || colonIndex == line.lastIndex) {
            return line
        }

        // Check if this is a key: value pair (colon followed by space or end of line)
        val afterColon = line.substring(colonIndex + 1)

        return if (afterColon.isEmpty()) {
            // Key with no value on this line
            line
        } else if (afterColon.startsWith(" ")) {
            // Already has space after colon, ensure only one space
            val value = afterColon.trimStart()
            if (value.isEmpty()) {
                line.substring(0, colonIndex + 1)
            } else {
                line.substring(0, colonIndex + 1) + " " + value
            }
        } else {
            // No space after colon, add one
            line.substring(0, colonIndex + 1) + " " + afterColon
        }
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
