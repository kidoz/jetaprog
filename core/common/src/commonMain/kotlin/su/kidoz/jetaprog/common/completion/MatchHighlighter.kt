package su.kidoz.jetaprog.common.completion

/**
 * Represents a range of characters in a completion item label that matched the prefix.
 *
 * Used to visually highlight matched characters in the completion popup.
 */
public data class MatchRange(
    /**
     * The start index (inclusive) in the label.
     */
    val start: Int,
    /**
     * The end index (exclusive) in the label.
     */
    val end: Int,
)

/**
 * Result of matching a prefix against a completion item label.
 *
 * Contains the match ranges and a quality score for sorting.
 */
public data class MatchResult(
    /**
     * The ranges in the label that matched the prefix.
     */
    val ranges: List<MatchRange>,
    /**
     * The match quality score (higher = better match).
     */
    val score: Int,
    /**
     * Whether the match was successful.
     */
    val isMatch: Boolean,
) {
    public companion object {
        /**
         * No match.
         */
        public val NO_MATCH: MatchResult = MatchResult(emptyList(), 0, false)
    }
}

/**
 * Computes match ranges for highlighting completion items.
 *
 * Supports multiple matching strategies:
 * - Exact prefix match
 * - Case-insensitive prefix match
 * - CamelCase matching (e.g., "gCE" matches "getCacheEntry")
 * - Substring/contains matching
 */
public object MatchHighlighter {
    private const val SCORE_EXACT = 1000
    private const val SCORE_PREFIX = 800
    private const val SCORE_CAMEL_CASE = 600
    private const val SCORE_CONTAINS = 400
    private const val SCORE_FUZZY = 200

    /**
     * Computes match ranges for a label against a prefix.
     *
     * @param label The completion item label
     * @param prefix The typed prefix to match against
     * @return The match result with highlight ranges and score
     */
    public fun match(
        label: String,
        prefix: String,
    ): MatchResult {
        if (prefix.isEmpty()) return MatchResult(emptyList(), SCORE_EXACT, true)
        if (label.isEmpty()) return MatchResult.NO_MATCH

        // Try exact prefix match
        if (label.startsWith(prefix)) {
            return MatchResult(
                listOf(MatchRange(0, prefix.length)),
                SCORE_EXACT + (100 - label.length),
                true,
            )
        }

        // Try case-insensitive prefix match
        if (label.startsWith(prefix, ignoreCase = true)) {
            return MatchResult(
                listOf(MatchRange(0, prefix.length)),
                SCORE_PREFIX + (100 - label.length),
                true,
            )
        }

        // Try CamelCase matching
        val camelRanges = matchCamelCase(label, prefix)
        if (camelRanges != null) {
            return MatchResult(
                camelRanges,
                SCORE_CAMEL_CASE + (100 - label.length),
                true,
            )
        }

        // Try substring matching
        val containsIdx = label.indexOf(prefix, ignoreCase = true)
        if (containsIdx >= 0) {
            return MatchResult(
                listOf(MatchRange(containsIdx, containsIdx + prefix.length)),
                SCORE_CONTAINS + (100 - containsIdx),
                true,
            )
        }

        // Try fuzzy matching
        val fuzzyRanges = matchFuzzy(label, prefix)
        if (fuzzyRanges != null) {
            return MatchResult(
                fuzzyRanges,
                SCORE_FUZZY + (100 - label.length),
                true,
            )
        }

        return MatchResult.NO_MATCH
    }

    /**
     * CamelCase matching: each character of the prefix matches a capital letter
     * or the start of a word in the label.
     *
     * E.g., "gCE" matches "getCacheEntry" -> ranges [(0,1), (3,4), (8,9)]
     */
    private fun matchCamelCase(
        label: String,
        prefix: String,
    ): List<MatchRange>? {
        val ranges = mutableListOf<MatchRange>()
        var pi = 0

        for (li in label.indices) {
            if (pi >= prefix.length) break

            val labelChar = label[li]
            val prefixChar = prefix[pi]

            if (labelChar.equals(prefixChar, ignoreCase = true)) {
                // Match at word boundary or uppercase letter
                if (li == 0 || label[li].isUpperCase() || label[li - 1] == '_' || label[li - 1] == '.') {
                    ranges.add(MatchRange(li, li + 1))
                    pi++
                } else if (ranges.isNotEmpty() && ranges.last().end == li) {
                    // Extend the last range (consecutive match)
                    ranges[ranges.lastIndex] = ranges.last().copy(end = li + 1)
                    pi++
                }
            }
        }

        return if (pi == prefix.length) ranges else null
    }

    /**
     * Fuzzy matching: each character of the prefix appears somewhere in the label
     * in order.
     */
    private fun matchFuzzy(
        label: String,
        prefix: String,
    ): List<MatchRange>? {
        val ranges = mutableListOf<MatchRange>()
        var pi = 0
        var lastMatchEnd = -1

        for (li in label.indices) {
            if (pi >= prefix.length) break

            if (label[li].equals(prefix[pi], ignoreCase = true)) {
                if (lastMatchEnd == li) {
                    // Extend the last range
                    ranges[ranges.lastIndex] = ranges.last().copy(end = li + 1)
                } else {
                    ranges.add(MatchRange(li, li + 1))
                }
                lastMatchEnd = li + 1
                pi++
            }
        }

        return if (pi == prefix.length) ranges else null
    }
}
