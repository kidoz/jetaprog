package su.kidoz.jetaprog.editor.completion

import su.kidoz.jetaprog.common.completion.CompletionItem

/**
 * Controller for managing code completion logic.
 *
 * Handles filtering, fuzzy matching, and ranking of completion items.
 */
public class CompletionController(
    private val config: CompletionConfig = CompletionConfig(),
) {
    /**
     * Check if a character should trigger completion.
     */
    public fun shouldTrigger(char: Char): Boolean = config.enabled && char in config.triggerCharacters

    /**
     * Check if the current prefix should auto-trigger completion.
     */
    public fun shouldAutoTrigger(prefix: String): Boolean =
        config.enabled &&
            prefix.length >= config.minTriggerLength &&
            prefix.all { it.isLetterOrDigit() || it == '_' }

    /**
     * Filter and rank completion items based on typed text.
     *
     * @param items The full list of completion items
     * @param filterText The text typed after triggering completion
     * @return Filtered and sorted list of matching items
     */
    public fun filterItems(
        items: List<CompletionItem>,
        filterText: String,
    ): List<CompletionItem> {
        if (filterText.isEmpty()) {
            return items.take(config.maxDisplayItems)
        }

        val lowerFilter = filterText.lowercase()

        return items
            .mapNotNull { item ->
                val score = calculateMatchScore(item, lowerFilter)
                if (score > 0) item to score else null
            }.sortedWith(
                compareByDescending<Pair<CompletionItem, Int>> { it.first.preselect }
                    .thenByDescending { it.second } // Higher score first
                    .thenBy { it.first.sortText }
                    .thenBy { it.first.label },
            ).take(config.maxDisplayItems)
            .map { it.first }
    }

    /**
     * Calculate a match score for an item against the filter text.
     *
     * @return Score > 0 if matches, 0 if no match. Higher is better.
     */
    private fun calculateMatchScore(
        item: CompletionItem,
        lowerFilter: String,
    ): Int {
        val lowerLabel = item.filterText.lowercase()

        // Exact prefix match - highest score
        if (lowerLabel.startsWith(lowerFilter)) {
            return 1000 + (100 - lowerLabel.length)
        }

        // Contains match
        if (lowerLabel.contains(lowerFilter)) {
            return 500 + (100 - lowerLabel.indexOf(lowerFilter))
        }

        // Fuzzy match (camelCase/snake_case aware)
        if (matchesFuzzy(lowerLabel, lowerFilter)) {
            return 100
        }

        // Fuzzy match on label (not filter text)
        if (matchesFuzzy(item.label.lowercase(), lowerFilter)) {
            return 50
        }

        return 0
    }

    /**
     * Simple fuzzy matching that supports camelCase and snake_case patterns.
     *
     * For example: "fmw" matches "fillMaxWidth", "gp" matches "get_property"
     */
    private fun matchesFuzzy(
        text: String,
        pattern: String,
    ): Boolean {
        var patternIdx = 0
        var prevWasSeparator = true

        for (char in text) {
            if (patternIdx >= pattern.length) break

            val isMatchingChar = char.equals(pattern[patternIdx], ignoreCase = true)
            val isCamelCaseBoundary = char.isUpperCase() && prevWasSeparator.not()
            val isWordBoundary = char == '_' || char == '-'

            if (isMatchingChar && (prevWasSeparator || isCamelCaseBoundary || patternIdx == 0)) {
                patternIdx++
            } else if (isMatchingChar) {
                patternIdx++
            }

            prevWasSeparator = isWordBoundary || char.isWhitespace()
        }

        return patternIdx == pattern.length
    }

    /**
     * Get the replacement range for applying a completion.
     *
     * @param content The document content
     * @param cursorOffset The cursor offset in the content
     * @return The start and end offsets of the word to replace
     */
    public fun getReplacementRange(
        content: String,
        cursorOffset: Int,
    ): Pair<Int, Int> {
        // Find word start
        var start = cursorOffset
        while (start > 0 && isIdentifierChar(content[start - 1])) {
            start--
        }

        // Find word end
        var end = cursorOffset
        while (end < content.length && isIdentifierChar(content[end])) {
            end++
        }

        return start to end
    }

    private fun isIdentifierChar(char: Char): Boolean = char.isLetterOrDigit() || char == '_'

    /**
     * Extract the prefix at cursor position for filtering.
     *
     * @param content The document content
     * @param cursorOffset The cursor offset in the content
     * @return The identifier prefix at cursor
     */
    public fun extractPrefix(
        content: String,
        cursorOffset: Int,
    ): String {
        var start = cursorOffset
        while (start > 0 && isIdentifierChar(content[start - 1])) {
            start--
        }
        return content.substring(start, cursorOffset)
    }
}
