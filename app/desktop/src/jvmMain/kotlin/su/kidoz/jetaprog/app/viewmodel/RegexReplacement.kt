package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.editor.search.FindMatch
import su.kidoz.jetaprog.editor.search.FindOptions
import java.util.regex.Pattern

/** Expands replacements against the original document, retaining lookaround and capture context. */
internal fun regexReplacementTexts(
    content: String,
    query: String,
    options: FindOptions,
    replacement: String,
): Map<FindMatch, String> {
    val pattern = if (options.wholeWord) "\\b(?:$query)\\b" else query
    val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    val matcher = Pattern.compile(pattern, flags).matcher(content)
    val replacements = mutableMapOf<FindMatch, String>()
    var previousEnd = 0
    while (matcher.find()) {
        if (matcher.start() == matcher.end()) continue
        val buffer = StringBuilder()
        matcher.appendReplacement(buffer, replacement)
        replacements[FindMatch(matcher.start(), matcher.end())] = buffer.substring(matcher.start() - previousEnd)
        previousEnd = matcher.end()
    }
    return replacements
}
