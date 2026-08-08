package su.kidoz.jetaprog.plugins.support.formatters

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Whole-document formatter backed by a command that reads source from stdin and
 * writes formatted source to stdout.
 *
 * @property languageId language handled by this formatter.
 * @property command executable and arguments used to format a document.
 * @property displayName tool name shown when formatting fails.
 * @property workingDirectory working directory used to resolve project configuration.
 */
public class ExternalStdioFormatter(
    override val languageId: LanguageId,
    private val command: List<String>,
    private val displayName: String,
    private val workingDirectory: String? = null,
) : CodeFormatter {
    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult {
        val formatted = runFormatter(content) ?: return FormattingResult.Failure("$displayName is unavailable")
        val finalContent = formatted.withRequestedFinalNewline(options)
        if (finalContent == content) return FormattingResult.Success(content, emptyList())

        return FormattingResult.Success(
            formattedText = finalContent,
            edits =
                listOf(
                    TextEdit(
                        range = TextRange(TextPosition.Zero, content.endPosition()),
                        newText = finalContent,
                    ),
                ),
        )
    }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult = format(content, options.copy(insertFinalNewline = false))

    private fun runFormatter(content: String): String? =
        try {
            require(command.isNotEmpty()) { "Formatter command must not be empty" }
            val process =
                ProcessBuilder(command)
                    .directory(workingDirectory?.let(::File)?.takeIf(File::isDirectory))
                    .start()
            val output = StringBuilder(content.length)
            val errorOutput = StringBuilder()
            val writer =
                Thread {
                    runCatching {
                        process.outputStream.bufferedWriter().use { stream -> stream.write(content) }
                    }
                }
            val reader =
                Thread {
                    runCatching {
                        process.inputStream.bufferedReader().use { stream -> output.append(stream.readText()) }
                    }
                }
            val errorReader =
                Thread {
                    runCatching {
                        process.errorStream.bufferedReader().use { stream -> errorOutput.append(stream.readText()) }
                    }
                }
            writer.isDaemon = true
            reader.isDaemon = true
            errorReader.isDaemon = true
            writer.start()
            reader.start()
            errorReader.start()

            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            writer.join(JOIN_TIMEOUT_MILLIS)
            reader.join(JOIN_TIMEOUT_MILLIS)
            errorReader.join(JOIN_TIMEOUT_MILLIS)

            if (completed && process.exitValue() == 0) {
                output.toString()
            } else {
                logger.warn { "$displayName failed: ${errorOutput.toString().trim()}" }
                null
            }
        } catch (error: Exception) {
            logger.debug { "$displayName unavailable: ${error.message}" }
            null
        }

    private fun String.withRequestedFinalNewline(options: FormattingOptions): String =
        when {
            options.insertFinalNewline && isNotEmpty() && !endsWith('\n') -> "$this\n"
            !options.insertFinalNewline -> trimEnd('\n', '\r')
            else -> this
        }

    private fun String.endPosition(): TextPosition {
        val lines = lines()
        return TextPosition(lines.lastIndex, lines.last().length)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val JOIN_TIMEOUT_MILLIS = 1_000L
    }
}
