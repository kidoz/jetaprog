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
}
