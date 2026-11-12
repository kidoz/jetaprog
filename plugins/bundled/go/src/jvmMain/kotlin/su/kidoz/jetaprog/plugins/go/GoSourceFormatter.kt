package su.kidoz.jetaprog.plugins.go

import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.support.formatters.CodeFormatter
import su.kidoz.jetaprog.plugins.support.formatters.FormattingResult

/**
 * Formats Go source with goimports when that command is installed, and with gofmt otherwise.
 *
 * @property preferred Formatter tried first. A failure means the tool is missing or rejected the file.
 * @property fallback Formatter used when [preferred] fails. gofmt ships with the Go toolchain.
 */
internal class GoSourceFormatter(
    private val preferred: CodeFormatter,
    private val fallback: CodeFormatter,
) : CodeFormatter {
    override val languageId: LanguageId = LanguageId.GO

    override fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult {
        val preferredResult = preferred.format(content, options)
        return if (preferredResult is FormattingResult.Failure) fallback.format(content, options) else preferredResult
    }

    override fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult = format(content, options.copy(insertFinalNewline = false))
}
