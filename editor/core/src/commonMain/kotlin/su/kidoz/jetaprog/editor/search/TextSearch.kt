package su.kidoz.jetaprog.editor.search

/**
 * A project-wide text search query.
 */
public data class TextSearchQuery(
    /** The text or regular expression to search for. */
    val query: String,
    /** Whether matching is case-sensitive. */
    val caseSensitive: Boolean = false,
    /** Whether [query] is a regular expression rather than literal text. */
    val regex: Boolean = false,
    /** Whether to match whole words only. */
    val wholeWord: Boolean = false,
)

/**
 * A single match within a line of a file.
 */
public data class TextSearchMatch(
    /** Zero-based line index. */
    val line: Int,
    /** Inclusive start column of the match. */
    val startColumn: Int,
    /** Exclusive end column of the match. */
    val endColumn: Int,
    /** The full text of the matching line. */
    val lineText: String,
)

/**
 * All matches found within a single file.
 */
public data class FileTextMatches(
    /** The file path. */
    val filePath: String,
    /** The matches, in document order. */
    val matches: List<TextSearchMatch>,
)

/**
 * The outcome of replacing every match within a single text.
 */
public data class TextReplaceResult(
    /** The text after all replacements. */
    val text: String,
    /** The number of occurrences that were replaced. */
    val occurrences: Int,
)

/**
 * Pure line-based text matcher shared by project-wide search.
 *
 * Compiles a [TextSearchQuery] into a regular expression (escaping literal
 * queries, honoring case sensitivity and whole-word matching) and reports every
 * match per line. Invalid regular expressions yield no matches rather than
 * throwing.
 */
public object TextSearchMatcher {
    /**
     * Builds the [Regex] for [query], or null when the query is empty or the
     * regular expression is invalid.
     */
    public fun compile(query: TextSearchQuery): Regex? {
        if (query.query.isEmpty()) return null
        val base = if (query.regex) query.query else Regex.escape(query.query)
        val pattern = if (query.wholeWord) "\\b(?:$base)\\b" else base
        val options = if (query.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(pattern, options) }.getOrNull()
    }

    /**
     * Returns all matches of [query] within [text], one entry per occurrence.
     */
    public fun matchesInText(
        text: String,
        query: TextSearchQuery,
    ): List<TextSearchMatch> {
        val regex = compile(query) ?: return emptyList()
        val results = mutableListOf<TextSearchMatch>()
        text.split('\n').forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            for (match in regex.findAll(line)) {
                if (match.range.isEmpty()) continue
                results +=
                    TextSearchMatch(
                        line = index,
                        startColumn = match.range.first,
                        endColumn = match.range.last + 1,
                        lineText = line,
                    )
            }
        }
        return results
    }

    /**
     * Replaces every match of [query] within [text] and returns the new text
     * together with the number of replaced occurrences.
     *
     * In regular-expression mode, `$1` and `${1}` references in [replacement]
     * expand to the corresponding capture group; a reference to a group that
     * did not participate (or does not exist) expands to the empty string, and
     * `\$` / `\\` escape to a literal character. In literal mode [replacement]
     * is used verbatim. Invalid regular expressions yield the text unchanged.
     */
    public fun replaceInText(
        text: String,
        query: TextSearchQuery,
        replacement: String,
    ): TextReplaceResult {
        val regex = compile(query) ?: return TextReplaceResult(text, 0)
        val builder = StringBuilder()
        var copiedUpTo = 0
        var occurrences = 0
        for (match in regex.findAll(text)) {
            if (match.range.isEmpty()) continue
            builder.append(text, copiedUpTo, match.range.first)
            builder.append(expandReplacement(match, replacement, query.regex))
            copiedUpTo = match.range.last + 1
            occurrences++
        }
        if (occurrences == 0) return TextReplaceResult(text, 0)
        builder.append(text, copiedUpTo, text.length)
        return TextReplaceResult(builder.toString(), occurrences)
    }

    private fun expandReplacement(
        match: MatchResult,
        replacement: String,
        interpretReferences: Boolean,
    ): String {
        if (!interpretReferences) return replacement
        val builder = StringBuilder()
        var index = 0
        while (index < replacement.length) {
            val char = replacement[index]
            when {
                char == '\\' && index + 1 < replacement.length -> {
                    builder.append(replacement[index + 1])
                    index += 2
                }

                char == '$' -> {
                    val reference = groupReference(replacement, index)
                    if (reference == null) {
                        builder.append(char)
                        index++
                    } else {
                        val group = if (reference in 0 until match.groups.size) match.groups[reference] else null
                        builder.append(group?.value.orEmpty())
                        index = referenceEnd(replacement, index)
                    }
                }

                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return builder.toString()
    }

    /** Parses the `$n` / `${n}` reference at [dollarIndex], or null when absent. */
    private fun groupReference(
        replacement: String,
        dollarIndex: Int,
    ): Int? {
        var cursor = dollarIndex + 1
        val braced = cursor < replacement.length && replacement[cursor] == '{'
        if (braced) cursor++
        val digitsStart = cursor
        while (cursor < replacement.length && replacement[cursor].isDigit()) cursor++
        if (cursor == digitsStart) return null
        if (braced && (cursor >= replacement.length || replacement[cursor] != '}')) return null
        return replacement.substring(digitsStart, cursor).toIntOrNull()
    }

    /** The index just past the reference that [groupReference] accepted. */
    private fun referenceEnd(
        replacement: String,
        dollarIndex: Int,
    ): Int {
        var cursor = dollarIndex + 1
        if (cursor < replacement.length && replacement[cursor] == '{') {
            while (cursor < replacement.length && replacement[cursor] != '}') cursor++
            return (cursor + 1).coerceAtMost(replacement.length)
        }
        while (cursor < replacement.length && replacement[cursor].isDigit()) cursor++
        return cursor
    }
}
