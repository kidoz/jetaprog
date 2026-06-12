package su.kidoz.jetaprog.common.completion

/**
 * Context provided to weighers for evaluating completion items.
 *
 * Contains information about the current completion session,
 * including the prefix, position, and document context.
 */
public data class WeighingContext(
    /**
     * The current prefix being typed by the user.
     */
    val prefix: String,
    /**
     * The language ID of the document.
     */
    val languageId: String,
    /**
     * The file path of the document.
     */
    val filePath: String,
    /**
     * The line number where completion was triggered (0-based).
     */
    val line: Int,
    /**
     * The column where completion was triggered (0-based).
     */
    val column: Int,
    /**
     * The mode of completion (basic, smart, statement).
     */
    val completionMode: CompletionMode = CompletionMode.Basic,
    /**
     * The expected type at the completion position, if known.
     */
    val expectedTypeName: String? = null,
)

/**
 * Completion modes, following IntelliJ's CompletionType.
 */
public enum class CompletionMode {
    /**
     * Basic completion -- all visible identifiers.
     */
    Basic,

    /**
     * Smart completion -- filtered by expected type.
     */
    Smart,

    /**
     * Statement completion -- completes the current statement.
     */
    Statement,
}

/**
 * A weigher that evaluates completion items and returns a comparable score.
 *
 * Weighers are organized in a pipeline (chain). Items are compared by the
 * first weigher; if equal, the second weigher breaks the tie, and so on.
 *
 * Lower values are "better" (sorted first) unless [isNegated] is true.
 *
 * Inspired by IntelliJ's `LookupElementWeigher`.
 */
public abstract class CompletionWeigher(
    /**
     * Unique identifier for this weigher, used for positioning in the pipeline.
     */
    public val id: String,
    /**
     * If true, the sort order is inverted (higher values sorted first).
     */
    public val isNegated: Boolean = false,
    /**
     * If true, the weigher is re-evaluated when the prefix changes.
     */
    public val isPrefixDependent: Boolean = false,
) {
    /**
     * Evaluates a completion item and returns a comparable weight.
     *
     * @param item The completion item to evaluate
     * @param context The weighing context
     * @return A comparable value representing the item's weight
     */
    public abstract fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*>
}

/**
 * A completion sorter that maintains an ordered pipeline of weighers.
 *
 * Items are compared by the first weigher in the pipeline. If equal,
 * the next weigher breaks the tie, and so on. This produces a total
 * ordering of completion items.
 *
 * The sorter is immutable -- modification methods return new instances.
 *
 * Inspired by IntelliJ's `CompletionSorterImpl`.
 */
public class CompletionSorter private constructor(
    private val weighers: List<CompletionWeigher>,
) {
    /**
     * Returns a new sorter with additional weighers inserted before the named anchor.
     *
     * @param anchorId The ID of the weigher to insert before
     * @param newWeighers The weighers to insert
     * @return A new sorter with the weighers inserted
     */
    public fun weighBefore(
        anchorId: String,
        vararg newWeighers: CompletionWeigher,
    ): CompletionSorter {
        val index = weighers.indexOfFirst { it.id == anchorId }
        if (index < 0) return CompletionSorter(weighers + newWeighers.toList())
        val result = weighers.toMutableList()
        result.addAll(index, newWeighers.toList())
        return CompletionSorter(result)
    }

    /**
     * Returns a new sorter with additional weighers inserted after the named anchor.
     *
     * @param anchorId The ID of the weigher to insert after
     * @param newWeighers The weighers to insert
     * @return A new sorter with the weighers inserted
     */
    public fun weighAfter(
        anchorId: String,
        vararg newWeighers: CompletionWeigher,
    ): CompletionSorter {
        val index = weighers.indexOfFirst { it.id == anchorId }
        if (index < 0) return CompletionSorter(weighers + newWeighers.toList())
        val result = weighers.toMutableList()
        result.addAll(index + 1, newWeighers.toList())
        return CompletionSorter(result)
    }

    /**
     * Returns a new sorter with an additional weigher appended at the end.
     *
     * @param weigher The weigher to append
     * @return A new sorter with the weigher appended
     */
    public fun weigh(weigher: CompletionWeigher): CompletionSorter = CompletionSorter(weighers + weigher)

    /**
     * Sorts completion items according to the weigher pipeline.
     *
     * @param items The items to sort
     * @param context The weighing context
     * @return A new list sorted by the pipeline
     */
    public fun sort(
        items: List<CompletionItem>,
        context: WeighingContext,
    ): List<CompletionItem> {
        if (items.size <= 1 || weighers.isEmpty()) return items

        // Cache weigher results for each item
        val cache =
            items.associateWith { item ->
                weighers.map { weigher -> weigher.weigh(item, context) }
            }

        return items.sortedWith(
            Comparator { a, b ->
                val weightsA = cache[a] ?: return@Comparator 0
                val weightsB = cache[b] ?: return@Comparator 0
                for (i in weighers.indices) {
                    val cmp = compareWeights(weightsA[i], weightsB[i], weighers[i].isNegated)
                    if (cmp != 0) return@Comparator cmp
                }
                0
            },
        )
    }

    /**
     * Returns the list of weighers in this pipeline.
     */
    public fun getWeighers(): List<CompletionWeigher> = weighers.toList()

    @Suppress("UNCHECKED_CAST")
    private fun compareWeights(
        a: Comparable<*>,
        b: Comparable<*>,
        negated: Boolean,
    ): Int {
        val cmp = (a as Comparable<Any>).compareTo(b as Any)
        return if (negated) -cmp else cmp
    }

    public companion object {
        /**
         * Creates an empty sorter with no weighers.
         */
        public fun empty(): CompletionSorter = CompletionSorter(emptyList())

        /**
         * Creates the default sorter pipeline with standard weighers.
         */
        public fun defaultSorter(): CompletionSorter =
            CompletionSorter(
                listOf(
                    PriorityWeigher(),
                    PreferStartMatchWeigher(),
                    PrefixMatchingWeigher(),
                    PreferExactTypeWeigher(),
                    ProximityWeigher(),
                    DeprecationWeigher(),
                    LengthWeigher(),
                ),
            )
    }
}

// ============================================================================
// Built-in Weighers
// ============================================================================

/**
 * Weighs by explicit priority (higher priority = sorted first).
 */
public class PriorityWeigher : CompletionWeigher("priority", isNegated = true) {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> = item.priority
}

/**
 * Prefers items where the match starts at the beginning of the label.
 */
public class PreferStartMatchWeigher : CompletionWeigher("startMatch") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> {
        if (context.prefix.isEmpty()) return 0
        val label = item.filterText.ifEmpty { item.label }
        return if (label.startsWith(context.prefix, ignoreCase = true)) 0 else 1
    }
}

/**
 * Weighs by prefix matching quality (exact > prefix > camelCase > contains).
 */
public class PrefixMatchingWeigher : CompletionWeigher("prefix") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> {
        if (context.prefix.isEmpty()) return 0
        val label = item.filterText.ifEmpty { item.label }
        val prefix = context.prefix
        return when {
            label.equals(prefix, ignoreCase = true) -> 0

            // exact match
            label.startsWith(prefix, ignoreCase = true) -> 1

            // prefix match
            matchesCamelCase(label, prefix) -> 2

            // camelCase match
            label.contains(prefix, ignoreCase = true) -> 3

            // contains match
            else -> 4 // fuzzy/no match
        }
    }

    private fun matchesCamelCase(
        label: String,
        prefix: String,
    ): Boolean {
        var pi = 0
        for (ch in label) {
            if (pi < prefix.length && ch.equals(prefix[pi], ignoreCase = true)) {
                pi++
            }
        }
        return pi == prefix.length
    }
}

/**
 * Prefers items matching the expected type (for smart completion).
 */
public class PreferExactTypeWeigher : CompletionWeigher("expectedType") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> {
        val expected = context.expectedTypeName ?: return 1
        val returnType = item.returnTypeName ?: return 2
        return when {
            returnType == expected -> 0

            // exact type match
            else -> 1 // not matching
        }
    }
}

/**
 * Prefers items from closer scopes (local > member > inherited > global).
 */
public class ProximityWeigher : CompletionWeigher("proximity") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> =
        when (item.source) {
            CompletionSource.LocalIndex -> 0
            CompletionSource.Plugin -> 1
            CompletionSource.Keywords -> 2
            CompletionSource.Lsp -> 3
            CompletionSource.Templates -> 4
            CompletionSource.PostfixTemplates -> 5
            CompletionSource.FilePaths -> 6
            CompletionSource.Unknown -> 7
        }
}

/**
 * Penalizes deprecated items.
 */
public class DeprecationWeigher : CompletionWeigher("deprecation") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> = if ("deprecated" in item.tags) 1 else 0
}

/**
 * Prefers shorter names when all else is equal.
 */
public class LengthWeigher : CompletionWeigher("length") {
    override fun weigh(
        item: CompletionItem,
        context: WeighingContext,
    ): Comparable<*> = item.label.length
}
