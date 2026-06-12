package su.kidoz.jetaprog.plugins.python

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.plugins.support.formatters.CodeFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Python code formatter that delegates to ruff format or falls back to basic PEP8 formatting.
 */
public class PythonFormatter(
    private val ruffPath: String = "ruff",
    private val useRuff: Boolean = true,
) : CodeFormatter {
    override val languageId: LanguageId = LanguageId.PYTHON

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult =
        try {
            val formatted =
                if (useRuff) {
                    formatWithRuff(content) ?: formatBasic(content, options)
                } else {
                    formatBasic(content, options)
                }

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
            FormattingResult.Failure("Python formatting error: ${e.message}")
        }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult {
        // For now, format the whole document
        return format(content, options)
    }

    /**
     * Format using ruff format command.
     * Returns null if ruff is not available or fails.
     */
    private fun formatWithRuff(content: String): String? =
        try {
            // Create a temp file for ruff to process
            val tempFile = File.createTempFile("jetaprog_python_", ".py")
            try {
                tempFile.writeText(content)

                val process =
                    ProcessBuilder(ruffPath, "format", tempFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()

                val completed = process.waitFor(10, TimeUnit.SECONDS)
                if (completed && process.exitValue() == 0) {
                    tempFile.readText()
                } else {
                    null
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            null // Fall back to basic formatting
        }

    /**
     * Basic PEP8 formatting without external tools.
     */
    private fun formatBasic(
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
        var consecutiveBlankLines = 0

        for (originalLine in lines) {
            val trimmedLine = originalLine.trim()

            // Handle blank lines (PEP8: max 2 consecutive)
            if (trimmedLine.isEmpty()) {
                consecutiveBlankLines++
                if (consecutiveBlankLines <= 2) {
                    result.add("")
                }
                continue
            }

            consecutiveBlankLines = 0

            // Calculate indentation level from original line
            val leadingSpaces = originalLine.takeWhile { it == ' ' || it == '\t' }
            val indentLevel = countIndentLevel(leadingSpaces, options.tabSize)

            // Build formatted line
            var formattedLine = indent.repeat(indentLevel) + formatLineContent(trimmedLine)

            // Trim trailing whitespace
            if (options.trimTrailingWhitespace) {
                formattedLine = formattedLine.trimEnd()
            }

            result.add(formattedLine)
        }

        // Remove trailing blank lines (keep max 1)
        while (result.size > 1 && result.last().isBlank() && result[result.lastIndex - 1].isBlank()) {
            result.removeAt(result.lastIndex)
        }

        return result.joinToString("\n")
    }

    /**
     * Format individual line content.
     */
    private fun formatLineContent(line: String): String {
        var result = line

        // Normalize spacing around operators (basic)
        result = normalizeOperatorSpacing(result)

        // Normalize spacing after commas and colons
        result = normalizeCommaSpacing(result)

        return result
    }

    /**
     * Normalize spacing around common operators.
     */
    private fun normalizeOperatorSpacing(line: String): String {
        // Don't process if line contains string literals (basic check)
        if (line.contains('"') || line.contains('\'')) {
            return line
        }

        var result = line

        // Binary operators
        val operators = listOf("==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "//=", "**=")
        for (op in operators) {
            result = result.replace(Regex("\\s*${Regex.escape(op)}\\s*"), " $op ")
        }

        // Clean up multiple spaces
        result = result.replace(Regex("\\s+"), " ")

        return result
    }

    /**
     * Normalize spacing after commas.
     */
    private fun normalizeCommaSpacing(line: String): String {
        if (line.contains('"') || line.contains('\'')) {
            return line
        }
        return line.replace(Regex(",\\s*"), ", ")
    }

    /**
     * Count indentation level from leading whitespace.
     */
    private fun countIndentLevel(
        leadingWhitespace: String,
        tabSize: Int,
    ): Int {
        var spaces = 0
        for (c in leadingWhitespace) {
            when (c) {
                ' ' -> spaces++
                '\t' -> spaces += tabSize
            }
        }
        return spaces / tabSize
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
