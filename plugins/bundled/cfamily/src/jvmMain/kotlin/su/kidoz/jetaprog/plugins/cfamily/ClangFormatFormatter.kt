package su.kidoz.jetaprog.plugins.cfamily

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit
import su.kidoz.jetaprog.plugins.support.formatters.CodeFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Formats C and C++ sources with `clang-format`.
 *
 * The document is piped through clang-format's stdin with `--assume-filename` pointing
 * inside the workspace, so a project's own `.clang-format` is picked up. When clang-format
 * is unavailable the document is returned unchanged, which lets the editor fall through to
 * clangd's LSP formatting or the default formatter.
 *
 * @property languageId The language this instance is registered for.
 * @property workspacePath Workspace root, used to resolve the project's `.clang-format`.
 * @property clangFormatPath Path or name of the clang-format executable.
 */
public class ClangFormatFormatter(
    override val languageId: LanguageId,
    private val workspacePath: String,
    private val clangFormatPath: String = DEFAULT_EXECUTABLE,
) : CodeFormatter {
    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult {
        val formatted =
            runClangFormat(content, options, ranges = emptyList())
                ?: return FormattingResult.Success(content, emptyList())

        return toResult(content, formatted, options)
    }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult {
        val startOffset = offsetOf(content, range.start)
        val endOffset = offsetOf(content, range.end)
        val length = (endOffset - startOffset).coerceAtLeast(0)

        val formatted =
            runClangFormat(content, options, ranges = listOf(startOffset to length))
                ?: return FormattingResult.Success(content, emptyList())

        return toResult(content, formatted, options)
    }

    private fun toResult(
        original: String,
        formatted: String,
        options: FormattingOptions,
    ): FormattingResult {
        val finalContent =
            if (options.insertFinalNewline && formatted.isNotEmpty() && !formatted.endsWith("\n")) {
                formatted + "\n"
            } else {
                formatted
            }

        if (finalContent == original) {
            return FormattingResult.Success(original, emptyList())
        }

        val edit =
            TextEdit(
                range = TextRange(TextPosition.Zero, positionAtEnd(original)),
                newText = finalContent,
            )
        return FormattingResult.Success(finalContent, listOf(edit))
    }

    /**
     * Runs clang-format over [content], returning null when the tool is unavailable or fails.
     *
     * @param ranges Byte offset/length pairs to reformat; empty formats the whole document.
     */
    private fun runClangFormat(
        content: String,
        options: FormattingOptions,
        ranges: List<Pair<Int, Int>>,
    ): String? =
        try {
            val command =
                buildList {
                    add(clangFormatPath)
                    add("--assume-filename=${assumedFileName()}")
                    add(styleArgument(options))
                    add("--fallback-style=$FALLBACK_STYLE")
                    ranges.forEach { (offset, length) ->
                        add("--offset=$offset")
                        add("--length=$length")
                    }
                }

            val process =
                ProcessBuilder(command)
                    .directory(File(workspacePath).takeIf { it.isDirectory })
                    .start()

            // Feed stdin from a separate thread: clang-format streams its result while
            // reading, so writing and reading on one thread deadlocks on large files.
            val writer =
                Thread {
                    runCatching { process.outputStream.use { it.write(content.toByteArray()) } }
                }
            writer.isDaemon = true
            writer.start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writer.join(TIMEOUT_SECONDS * MILLIS_PER_SECOND)

            if (completed && process.exitValue() == 0 && output.isNotEmpty()) {
                output
            } else {
                // Surface the reason: a silent fallback makes a misconfigured
                // clang-format look like a document that simply needed no changes.
                val error =
                    process.errorStream
                        .bufferedReader()
                        .use { it.readText() }
                        .trim()
                logger.warn { "clang-format failed (exit ${process.exitValue()}): $error" }
                null
            }
        } catch (e: Exception) {
            logger.debug { "clang-format unavailable at '$clangFormatPath': ${e.message}" }
            null
        }

    /**
     * Selects the style argument.
     *
     * A project `.clang-format` must win, so it is used when one exists anywhere above
     * the source. Otherwise the editor's indentation settings are passed inline —
     * `--fallback-style` only accepts a named style, not inline YAML.
     */
    private fun styleArgument(options: FormattingOptions): String =
        if (hasProjectStyleFile()) "--style=file" else "--style=${inlineStyle(options)}"

    private fun hasProjectStyleFile(): Boolean =
        generateSequence(File(workspacePath).absoluteFile) { it.parentFile }
            .any { dir -> File(dir, ".clang-format").isFile || File(dir, "_clang-format").isFile }

    /**
     * A filename inside the workspace with an extension matching [languageId], so that
     * clang-format selects the right language and locates the project's `.clang-format`.
     */
    private fun assumedFileName(): String {
        val extension = if (languageId == LanguageId.C) "c" else "cpp"
        return File(workspacePath, "jetaprog-format-probe.$extension").absolutePath
    }

    /**
     * An inline style derived from the editor's indentation settings, used when the
     * project has no `.clang-format` of its own.
     */
    private fun inlineStyle(options: FormattingOptions): String =
        "{BasedOnStyle: LLVM, IndentWidth: ${options.tabSize}, " +
            "UseTab: ${if (options.insertSpaces) "Never" else "Always"}}"

    private fun offsetOf(
        content: String,
        position: TextPosition,
    ): Int {
        var offset = 0
        val lines = content.lines()
        for (index in 0 until position.line.coerceAtMost(lines.size - 1)) {
            offset += lines[index].length + 1
        }
        val lineLength = lines.getOrNull(position.line)?.length ?: 0
        return offset + position.column.coerceAtMost(lineLength)
    }

    private fun positionAtEnd(content: String): TextPosition {
        val lines = content.lines()
        return TextPosition(lines.size - 1, lines.last().length)
    }

    private companion object {
        const val DEFAULT_EXECUTABLE = "clang-format"
        const val TIMEOUT_SECONDS = 10L
        const val MILLIS_PER_SECOND = 1000L

        /** Named style used when `--style=file` finds no `.clang-format`. */
        const val FALLBACK_STYLE = "LLVM"
    }
}
