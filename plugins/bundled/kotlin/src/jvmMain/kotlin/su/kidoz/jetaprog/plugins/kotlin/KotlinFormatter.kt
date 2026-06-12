package su.kidoz.jetaprog.plugins.kotlin

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.plugins.support.formatters.CodeFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult

/**
 * Kotlin code formatter that handles indentation, spacing, and code style.
 */
public class KotlinFormatter : CodeFormatter {
    override val languageId: LanguageId = LanguageId.KOTLIN

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult =
        try {
            val formatted = formatKotlin(content, options)
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
            FormattingResult.Failure("Kotlin formatting error: ${e.message}")
        }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult {
        // For now, format the whole document
        return format(content, options)
    }

    private fun formatKotlin(
        content: String,
        options: FormattingOptions,
    ): String {
        val indent =
            if (options.insertSpaces) {
                " ".repeat(options.tabSize)
            } else {
                "\t"
            }

        val lines = content.lines().toMutableList()
        val result = mutableListOf<String>()
        var indentLevel = 0
        var inMultiLineString = false
        var inMultiLineComment = false
        var previousLineBlank = false
        var consecutiveBlankLines = 0

        for ((index, originalLine) in lines.withIndex()) {
            val trimmedLine = originalLine.trim()

            // Track multi-line string literals (raw strings)
            if (!inMultiLineComment) {
                val tripleQuoteCount = trimmedLine.count("\"\"\"")
                if (tripleQuoteCount % 2 == 1) {
                    inMultiLineString = !inMultiLineString
                }
            }

            // Track multi-line comments
            if (!inMultiLineString) {
                if (trimmedLine.contains("/*") && !trimmedLine.contains("*/")) {
                    inMultiLineComment = true
                }
                if (trimmedLine.contains("*/")) {
                    inMultiLineComment = false
                }
            }

            // Handle blank lines
            if (trimmedLine.isEmpty()) {
                consecutiveBlankLines++
                // Allow max 1 blank line between declarations
                if (consecutiveBlankLines <= 1) {
                    result.add("")
                }
                previousLineBlank = true
                continue
            }

            consecutiveBlankLines = 0

            // Decrease indent before closing braces
            if (!inMultiLineString && !inMultiLineComment) {
                if (trimmedLine.startsWith("}") ||
                    trimmedLine.startsWith(")") ||
                    trimmedLine.startsWith("]")
                ) {
                    indentLevel = maxOf(0, indentLevel - 1)
                }
            }

            // Build formatted line
            var formattedLine =
                if (inMultiLineString) {
                    // Preserve original indentation in raw strings
                    originalLine
                } else {
                    indent.repeat(indentLevel) + formatLineContent(trimmedLine, options)
                }

            // Trim trailing whitespace
            if (options.trimTrailingWhitespace) {
                formattedLine = formattedLine.trimEnd()
            }

            result.add(formattedLine)
            previousLineBlank = false

            // Increase indent after opening braces
            if (!inMultiLineString && !inMultiLineComment) {
                val netOpenBraces = countNetOpenBraces(trimmedLine)
                indentLevel = maxOf(0, indentLevel + netOpenBraces)
            }
        }

        // Remove trailing blank lines
        while (result.isNotEmpty() && result.last().isBlank()) {
            result.removeAt(result.lastIndex)
        }

        return result.joinToString("\n")
    }

    private fun formatLineContent(
        line: String,
        options: FormattingOptions,
    ): String {
        var result = line

        // Normalize spacing around operators
        result = normalizeOperatorSpacing(result)

        // Normalize spacing after commas
        result = normalizeCommaSpacing(result)

        // Normalize spacing around colons in type declarations
        result = normalizeColonSpacing(result)

        // Normalize spacing in function calls
        result = normalizeFunctionCallSpacing(result)

        return result
    }

    private fun normalizeOperatorSpacing(line: String): String {
        var result = line
        val operators = listOf("==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "->", "::")

        // Don't process if line contains string literals
        if (line.contains('"') || line.contains('\'')) {
            return result
        }

        for (op in operators) {
            result = result.replace(Regex("\\s*${Regex.escape(op)}\\s*"), " $op ")
        }

        // Single operators (but not when part of compound operators)
        result = result.replace(Regex("(?<![=!<>+\\-*/])=(?![=>])"), " = ")
        result = result.replace(Regex("(?<![<>:])>(?![>=])"), " > ")
        result = result.replace(Regex("(?<![<>-])<(?![<=])"), " < ")
        result = result.replace(Regex("(?<![+])\\+(?![+=])"), " + ")
        result = result.replace(Regex("(?<![->])\\-(?![->=])"), " - ")
        result = result.replace(Regex("(?<![*/])\\*(?![*=])"), " * ")
        result = result.replace(Regex("(?<![/])\\b/\\b(?![/=])"), " / ")

        // Clean up multiple spaces
        result = result.replace(Regex("\\s+"), " ")

        return result
    }

    private fun normalizeCommaSpacing(line: String): String {
        if (line.contains('"') || line.contains('\'')) {
            return line
        }
        return line.replace(Regex(",\\s*"), ", ")
    }

    private fun normalizeColonSpacing(line: String): String {
        if (line.contains('"') || line.contains('\'')) {
            return line
        }
        // Type annotations: "val x: String" - space after colon only
        return line.replace(Regex(":\\s+"), ": ").replace(Regex("(?<=[a-zA-Z_]):\\s*(?=[A-Z])"), ": ")
    }

    private fun normalizeFunctionCallSpacing(line: String): String {
        // No space before opening parenthesis in function calls
        if (line.contains('"') || line.contains('\'')) {
            return line
        }
        return line.replace(Regex("([a-zA-Z_][a-zA-Z0-9_]*)\\s+\\("), "$1(")
    }

    private fun countNetOpenBraces(line: String): Int {
        var inString = false
        var inChar = false
        var escaped = false
        var net = 0

        for (c in line) {
            when {
                escaped -> {
                    escaped = false
                }

                c == '\\' -> {
                    escaped = true
                }

                c == '"' && !inChar -> {
                    inString = !inString
                }

                c == '\'' && !inString -> {
                    inChar = !inChar
                }

                !inString && !inChar -> {
                    when (c) {
                        '{', '(', '[' -> net++
                        '}', ')', ']' -> net--
                    }
                }
            }
        }

        return net
    }

    private fun String.count(substring: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = this.indexOf(substring, index)
            if (index == -1) break
            count++
            index += substring.length
        }
        return count
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
