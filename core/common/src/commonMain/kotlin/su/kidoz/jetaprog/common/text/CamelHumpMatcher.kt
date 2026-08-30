package su.kidoz.jetaprog.common.text

/**
 * CamelHump ("any hump") matcher in the spirit of IntelliJ's `MinusculeMatcher`:
 * a pattern like `FIF` or `findF` matches `findInFiles`, matching at word
 * ("hump") starts is strongly preferred, and matches carry a numeric score so
 * callers can rank candidates.
 *
 * `*` inside the pattern splits it into segments that must match in order
 * (middle matching), e.g. `f*Files` matches `findInFiles`.
 *
 * @param pattern The search pattern; `*` enables ordered middle matching.
 * @param caseSensitive When false (default), pattern characters match either
 *   case; case still affects scoring.
 */
public class CamelHumpMatcher(
    pattern: String,
    private val caseSensitive: Boolean = false,
) {
    private val plainPattern: String = pattern.replace(STAR, "")

    /** Segments between `*`s, matched in order against the name. */
    private val segments: List<String> =
        pattern.split(STAR).map { it.trim() }.filter { it.isNotEmpty() }

    /** Whether [name] matches the pattern at all. */
    public fun matches(name: String): Boolean = matchingFragments(name) != null

    /**
     * The matched character ranges inside [name] (coalesced runs, inclusive
     * bounds), or null when the name does not match.
     */
    public fun matchingFragments(name: String): List<IntRange>? {
        if (plainPattern.isEmpty()) return emptyList()
        val wordStarts = wordStartIndexes(name)
        var from = 0
        val positions = mutableListOf<Int>()
        var exactCase = true
        for (segment in segments) {
            val segmentMatch = matchSegment(name, wordStarts, segment, from) ?: return null
            positions += segmentMatch.positions
            exactCase = exactCase && segmentMatch.exactCase
            from = segmentMatch.positions.last() + 1
        }
        if (positions.isNotEmpty() && name[positions.first()] == plainPattern.first()) {
            this.exactCaseFirst = true
        } else {
            this.exactCaseFirst = caseSensitive
        }
        this.exactCaseMatch = exactCase
        return coalescePositions(positions)
    }

    /** Whether the last [matchingFragments] call matched with exact case throughout. */
    private var exactCaseMatch: Boolean = false

    /** Whether the first matched character equals the first pattern character exactly. */
    private var exactCaseFirst: Boolean = false

    /**
     * Match quality for [name]; null when it does not match. Higher is better.
     *
     * Scoring follows IntelliJ's scheme: +1000 when the match starts on a word
     * start, +150 when the first pattern letter's case matches, +50 for an
     * all-uppercase pattern matched with exact case, +2 when the match starts
     * at index 0, −1 per gap between fragments and −10 per skipped hump.
     */
    public fun matchingScore(name: String): Int? {
        val fragments = matchingFragments(name) ?: return null
        if (fragments.isEmpty()) return 0
        var score = 0
        val first = fragments.first().first
        val wordStarts = wordStartIndexes(name)

        if (first == 0) score += STARTS_AT_ZERO_BONUS
        if (first in wordStarts) score += WORD_START_BONUS
        if (exactCaseFirst) score += FIRST_LETTER_CASE_BONUS
        if (plainPattern.length > 1 && plainPattern.all { it.isUpperCase() } && exactCaseMatch) {
            score += UPPERCASE_PATTERN_BONUS
        }

        score -= GAP_PENALTY * (fragments.size - 1)
        score -= SKIPPED_HUMP_PENALTY * skippedHumps(name, fragments)
        return score
    }

    /**
     * Matches one pattern segment against [name] starting at [from] as an
     * ordered subsequence: each pattern character takes the next equal
     * character (word starts are rewarded through scoring, not matching).
     * Returns the matched positions plus whether every character matched with
     * exact case.
     */
    private fun matchSegment(
        name: String,
        wordStarts: Set<Int>,
        segment: String,
        from: Int,
    ): SegmentMatch? {
        val matched = mutableListOf<Int>()
        var exactCase = true
        var pos = from
        for (patternChar in segment) {
            val humpHit =
                if (patternChar.isUpperCase()) {
                    (pos until name.length).firstOrNull { it in wordStarts && charsEqual(name[it], patternChar) }
                } else {
                    null
                }
            val hit =
                humpHit
                    ?: (pos until name.length).firstOrNull { charsEqual(name[it], patternChar) }
                    ?: return null
            if (name[hit] != patternChar) exactCase = false
            matched += hit
            pos = hit + 1
        }
        if (matched.isEmpty()) return null
        return SegmentMatch(positions = matched, exactCase = exactCase)
    }

    private data class SegmentMatch(
        val positions: List<Int>,
        val exactCase: Boolean,
    )

    private fun charsEqual(
        a: Char,
        b: Char,
    ): Boolean = if (caseSensitive) a == b else a.lowercaseChar() == b.lowercaseChar()

    private fun skippedHumps(
        name: String,
        fragments: List<IntRange>,
    ): Int {
        val first = fragments.first().first
        val last = fragments.last().last
        val fragmentStarts = fragments.map { it.first }.toSet()
        return wordStartIndexes(name).count { it in first..last && it !in fragmentStarts }
    }

    private fun coalescePositions(positions: List<Int>): List<IntRange> {
        if (positions.isEmpty()) return emptyList()
        val result = mutableListOf(positions.first()..positions.first())
        for (index in positions.drop(1)) {
            val lastRange = result.last()
            if (index == lastRange.last + 1) {
                result[result.size - 1] = lastRange.first..index
            } else {
                result += index..index
            }
        }
        return result
    }

    public companion object {
        private const val STAR = "*"

        /** Match begins at a word ("hump") start — the preferred match shape. */
        public const val WORD_START_BONUS: Int = 1000

        /** The first pattern letter's case matches the name's character case. */
        public const val FIRST_LETTER_CASE_BONUS: Int = 150

        /** All-uppercase pattern matched with exact case (the user pressed Shift). */
        public const val UPPERCASE_PATTERN_BONUS: Int = 50

        /** The match begins at index 0 of the name. */
        public const val STARTS_AT_ZERO_BONUS: Int = 2

        /** Each gap between matched fragments (non-contiguous match). */
        public const val GAP_PENALTY: Int = 1

        /** Each word start inside the matched span that the pattern skipped. */
        public const val SKIPPED_HUMP_PENALTY: Int = 10

        /**
         * Indexes at which a new word starts: index 0, after separators
         * (`_`, `-`, `.`, digits), and on camel-hump boundaries including
         * acronym ends (`parseHTTPResponse` → `parse`, `HTTP`, `Response`).
         */
        public fun wordStartIndexes(name: String): Set<Int> {
            val starts = mutableSetOf(0)
            for (i in 1 until name.length) {
                val prev = name[i - 1]
                val current = name[i]
                val isStart =
                    when {
                        prev == '_' || prev == '-' || prev == '.' || prev == ' ' -> true

                        prev.isDigit() != current.isDigit() -> true

                        prev.isLowerCase() && current.isUpperCase() -> true

                        prev.isUpperCase() && current.isUpperCase() &&
                            i + 1 < name.length && name[i + 1].isLowerCase() -> true

                        else -> false
                    }
                if (isStart) starts += i
            }
            return starts
        }
    }
}
