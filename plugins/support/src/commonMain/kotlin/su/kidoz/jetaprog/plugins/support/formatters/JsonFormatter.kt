package su.kidoz.jetaprog.plugins.support.formatters

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit

/**
 * JSON formatter that pretty-prints JSON with configurable indentation.
 */
public class JsonFormatter : CodeFormatter {
    override val languageId: LanguageId = LanguageId.JSON

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult =
        try {
            val formatted = prettyPrint(content.trim(), options)
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
            FormattingResult.Failure("JSON parsing error: ${e.message}")
        }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult {
        // JSON formatting typically requires the whole document
        return format(content, options)
    }

    private fun prettyPrint(
        json: String,
        options: FormattingOptions,
    ): String {
        val indent =
            if (options.insertSpaces) {
                " ".repeat(options.tabSize)
            } else {
                "\t"
            }

        val result = StringBuilder()
        var indentLevel = 0
        var inString = false
        var escaped = false
        var i = 0

        while (i < json.length) {
            val c = json[i]

            when {
                escaped -> {
                    result.append(c)
                    escaped = false
                }

                c == '\\' && inString -> {
                    result.append(c)
                    escaped = true
                }

                c == '"' -> {
                    result.append(c)
                    inString = !inString
                }

                inString -> {
                    result.append(c)
                }

                c == '{' || c == '[' -> {
                    result.append(c)
                    // Check if empty object/array
                    val closingChar = if (c == '{') '}' else ']'
                    val nextNonWhitespace = json.substring(i + 1).indexOfFirst { !it.isWhitespace() }
                    if (nextNonWhitespace != -1 && json[i + 1 + nextNonWhitespace] == closingChar) {
                        // Empty object/array - skip to closing bracket
                        i += nextNonWhitespace + 1
                        result.append(closingChar)
                    } else {
                        indentLevel++
                        result.append("\n")
                        result.append(indent.repeat(indentLevel))
                    }
                }

                c == '}' || c == ']' -> {
                    indentLevel--
                    result.append("\n")
                    result.append(indent.repeat(indentLevel))
                    result.append(c)
                }

                c == ',' -> {
                    result.append(c)
                    result.append("\n")
                    result.append(indent.repeat(indentLevel))
                }

                c == ':' -> {
                    result.append(": ")
                }

                c.isWhitespace() -> {
                    // Skip whitespace outside strings
                }

                else -> {
                    result.append(c)
                }
            }
            i++
        }

        return result.toString()
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
