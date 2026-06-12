package su.kidoz.jetaprog.common.completion

/**
 * Tracks completion item selection statistics for relevance-based ranking.
 *
 * Records how often and how recently each completion item was selected,
 * enabling the completion system to prioritize frequently and recently
 * used items. Similar to IntelliJ's `StatisticsManager`.
 */
public class CompletionStatistics {
    /**
     * Entry for a single completion item's statistics.
     */
    public data class StatEntry(
        /**
         * Number of times this item was selected.
         */
        val count: Int = 0,
        /**
         * Timestamp of the last selection (milliseconds since epoch).
         */
        val lastUsed: Long = 0,
    )

    private val stats = mutableMapOf<String, StatEntry>()
    private val contextStats = mutableMapOf<String, MutableMap<String, StatEntry>>()

    /**
     * Records that a completion item was selected.
     *
     * @param itemKey A unique key for the item (typically label + kind)
     * @param contextKey Optional context key (e.g., file path or language)
     */
    public fun recordSelection(
        itemKey: String,
        contextKey: String? = null,
    ) {
        val now = System.currentTimeMillis()

        // Global stats
        val existing = stats[itemKey] ?: StatEntry()
        stats[itemKey] = existing.copy(count = existing.count + 1, lastUsed = now)

        // Context-specific stats
        if (contextKey != null) {
            val contextMap = contextStats.getOrPut(contextKey) { mutableMapOf() }
            val contextExisting = contextMap[itemKey] ?: StatEntry()
            contextMap[itemKey] = contextExisting.copy(count = contextExisting.count + 1, lastUsed = now)
        }
    }

    /**
     * Gets the statistics for a completion item.
     *
     * @param itemKey The item key
     * @param contextKey Optional context key for context-specific stats
     * @return The statistics entry, or null if no data
     */
    public fun getStats(
        itemKey: String,
        contextKey: String? = null,
    ): StatEntry? {
        if (contextKey != null) {
            contextStats[contextKey]?.get(itemKey)?.let { return it }
        }
        return stats[itemKey]
    }

    /**
     * Computes a relevance score for an item based on usage statistics.
     *
     * The score combines frequency (how often selected) and recency
     * (how recently selected), with recency having a decay factor.
     *
     * @param itemKey The item key
     * @param contextKey Optional context key
     * @return A score from 0 (never used) to higher values (frequently/recently used)
     */
    public fun computeScore(
        itemKey: String,
        contextKey: String? = null,
    ): Int {
        val entry = getStats(itemKey, contextKey) ?: return 0
        val now = System.currentTimeMillis()

        // Frequency component (capped at 50)
        val frequencyScore = (entry.count * FREQUENCY_WEIGHT).coerceAtMost(MAX_FREQUENCY_SCORE)

        // Recency component: exponential decay over time
        val ageMs = now - entry.lastUsed
        val recencyScore =
            when {
                ageMs < RECENT_THRESHOLD_MS -> RECENT_SCORE

                // Used in last 5 minutes
                ageMs < MEDIUM_THRESHOLD_MS -> MEDIUM_SCORE

                // Used in last hour
                ageMs < OLD_THRESHOLD_MS -> OLD_SCORE

                // Used today
                else -> 0
            }

        return frequencyScore + recencyScore
    }

    /**
     * Creates a [CompletionWeigher] that uses these statistics for sorting.
     *
     * @param contextKey Optional context key for context-specific ranking
     * @return A weigher that scores items by usage statistics
     */
    public fun createWeigher(contextKey: String? = null): CompletionWeigher = StatisticsWeigher(this, contextKey)

    /**
     * Clears all statistics.
     */
    public fun clear() {
        stats.clear()
        contextStats.clear()
    }

    /**
     * Returns the total number of tracked items.
     */
    public val size: Int get() = stats.size

    private companion object {
        const val FREQUENCY_WEIGHT = 5
        const val MAX_FREQUENCY_SCORE = 50
        const val RECENT_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        const val MEDIUM_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour
        const val OLD_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val RECENT_SCORE = 30
        const val MEDIUM_SCORE = 15
        const val OLD_SCORE = 5
    }
}

/**
 * A [CompletionWeigher] that uses [CompletionStatistics] for sorting.
 *
 * Items that were recently and frequently selected are ranked higher.
 * Negated so higher scores sort first.
 */
public class StatisticsWeigher(
    private val statistics: CompletionStatistics,
    private val contextKey: String? = null,
) : CompletionWeigher("statistics", isNegated = true) {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> {
        val key = "${item.label}:${item.kind}"
        return statistics.computeScore(key, contextKey ?: context.filePath)
    }
}
