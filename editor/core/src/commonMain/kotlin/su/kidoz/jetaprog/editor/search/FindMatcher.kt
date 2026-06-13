package su.kidoz.jetaprog.editor.search

import kotlinx.serialization.Serializable

/**
 * A single match found in document content, expressed as character offsets.
 */
@Serializable
public data class FindMatch(
    /**
     * The inclusive start offset of the match.
     */
    val start: Int,
    /**
     * The exclusive end offset of the match.
     */
    val end: Int,
)

/**
 * Options controlling how [FindMatcher] searches document content.
 */
@Serializable
public data class FindOptions(
    /**
     * Whether matching is case sensitive.
     */
    val caseSensitive: Boolean = false,
    /**
     * Whether matches must align with word boundaries.
     */
    val wholeWord: Boolean = false,
    /**
     * Whether the query is interpreted as a regular expression.
     */
    val regex: Boolean = false,
)

/**
 * Computes find-in-file matches for editor content.
 */
public object FindMatcher {
    /**
     * Find all matches of [query] in [content] according to [options].
     *
     * Returns an empty list when the query is empty or is an invalid regular expression.
     * Zero-length regex matches are dropped so navigation always makes progress.
     */
    public fun findMatches(
        content: String,
        query: String,
        options: FindOptions = FindOptions(),
    ): List<FindMatch> {
        if (query.isEmpty()) return emptyList()

        val base = if (options.regex) query else Regex.escape(query)
        val pattern = if (options.wholeWord) "\\b$base\\b" else base

        val regex =
            try {
                if (options.caseSensitive) {
                    Regex(pattern)
                } else {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                }
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }

        return regex
            .findAll(content)
            .filter { it.value.isNotEmpty() }
            .map { FindMatch(it.range.first, it.range.last + 1) }
            .toList()
    }

    /**
     * Index of the first match starting at or after [offset], wrapping to the first match.
     *
     * Returns -1 when [matches] is empty.
     */
    public fun matchIndexAtOrAfter(
        matches: List<FindMatch>,
        offset: Int,
    ): Int {
        if (matches.isEmpty()) return -1
        val index = matches.indexOfFirst { it.start >= offset }
        return if (index >= 0) index else 0
    }
}
