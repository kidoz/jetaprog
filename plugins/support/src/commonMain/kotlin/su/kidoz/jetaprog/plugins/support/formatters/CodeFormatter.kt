package su.kidoz.jetaprog.plugins.support.formatters

import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.FormattingOptions
import su.kidoz.jetaprog.plugins.api.services.TextEdit

/**
 * Result of a formatting operation.
 */
public sealed interface FormattingResult {
    /**
     * Formatting succeeded.
     */
    public data class Success(
        val formattedText: String,
        val edits: List<TextEdit>,
    ) : FormattingResult

    /**
     * Formatting failed.
     */
    public data class Failure(
        val error: String,
    ) : FormattingResult
}

/**
 * Interface for code formatters.
 */
public interface CodeFormatter {
    /**
     * The language ID this formatter supports.
     */
    public val languageId: LanguageId

    /**
     * Formats the entire document.
     * @param content The document content
     * @param options Formatting options
     * @return The formatting result
     */
    public fun format(
        content: String,
        options: FormattingOptions,
    ): FormattingResult

    /**
     * Formats a range within the document.
     * @param content The document content
     * @param range The range to format
     * @param options Formatting options
     * @return The formatting result
     */
    public fun formatRange(
        content: String,
        range: TextRange,
        options: FormattingOptions,
    ): FormattingResult
}

/**
 * Registry for code formatters.
 */
public object FormatterRegistry {
    private val formatters = mutableMapOf<LanguageId, CodeFormatter>()
    private var defaultFormatter: CodeFormatter? = null

    /**
     * Registers a formatter for a language.
     * @param formatter The formatter to register
     */
    public fun register(formatter: CodeFormatter) {
        formatters[formatter.languageId] = formatter
    }

    /**
     * Sets the default formatter for unsupported languages.
     * @param formatter The default formatter
     */
    public fun setDefaultFormatter(formatter: CodeFormatter) {
        defaultFormatter = formatter
    }

    /**
     * Gets the formatter for a language.
     * @param languageId The language ID
     * @return The formatter, or the default formatter if none is registered
     */
    public fun getFormatter(languageId: LanguageId): CodeFormatter? = formatters[languageId] ?: defaultFormatter

    /**
     * Checks if a formatter is registered for a language.
     * @param languageId The language ID
     * @return True if a formatter is available
     */
    public fun hasFormatter(languageId: LanguageId): Boolean =
        formatters.containsKey(languageId) || defaultFormatter != null

    /**
     * Gets all registered formatters.
     * @return Map of language ID to formatter
     */
    public fun getAllFormatters(): Map<LanguageId, CodeFormatter> = formatters.toMap()

    /**
     * Clears all registered formatters.
     */
    public fun clear() {
        formatters.clear()
        defaultFormatter = null
    }
}
