package su.kidoz.jetaprog.editor.completion.smart

import su.kidoz.jetaprog.common.completion.CompletionItem
import su.kidoz.jetaprog.editor.navigation.index.IndexRange

/**
 * Controller for smart (type-aware) code completion.
 *
 * Orchestrates completion providers, applies type-based filtering,
 * and ranks results by relevance following IntelliJ IDEA patterns.
 */
public class SmartCompletionController(
    private val providerRegistry: SmartCompletionProviderRegistry = SmartCompletionProviderRegistry(),
    private val config: SmartCompletionConfig = SmartCompletionConfig(),
) {
    /**
     * Perform completion and return ranked results.
     */
    public suspend fun complete(request: SmartCompletionRequest): SmartCompletionResult {
        // Step 1: Resolve type context from providers
        val typeContext = resolveTypeContext(request)
        val requestWithContext = request.copy(typeContext = typeContext)

        // Step 2: Collect items from all providers
        val collector = SmartCompletionCollectorImpl(request.limit * 2)
        val providers = providerRegistry.getApplicableProviders(requestWithContext)

        for (provider in providers) {
            if (collector.shouldStop()) break
            provider.provideCompletions(requestWithContext, collector)
        }

        val collectedItems = collector.getItems()
        val totalBeforeFilter = collectedItems.size

        // Step 3: Filter and score items
        val scoredItems = scoreItems(collectedItems, requestWithContext)

        // Step 4: Apply mode-specific filtering
        val filteredItems =
            when (request.mode) {
                CompletionMode.Basic -> scoredItems
                CompletionMode.Smart -> filterByType(scoredItems, typeContext)
                CompletionMode.Statement -> scoredItems // Statement completion is handled differently
            }

        // Step 5: Sort by score and limit
        val sortedItems =
            filteredItems
                .sortedWith(
                    compareByDescending<ScoredCompletionItem> { it.item.preselect }
                        .thenByDescending { it.score }
                        .thenBy { it.item.sortText }
                        .thenBy { it.item.label.length },
                ).take(request.limit)

        // Step 6: Fallback to basic if smart yields no results
        val finalItems =
            if (sortedItems.isEmpty() &&
                request.mode == CompletionMode.Smart &&
                config.fallbackToBasic
            ) {
                scoredItems.take(request.limit)
            } else {
                sortedItems
            }

        return SmartCompletionResult(
            items = finalItems,
            isIncomplete = totalBeforeFilter > request.limit,
            mode = request.mode,
            typeContext = typeContext,
            totalBeforeFilter = totalBeforeFilter,
        )
    }

    /**
     * Resolve type context by consulting all providers.
     */
    private suspend fun resolveTypeContext(request: SmartCompletionRequest): ExpectedTypeContext {
        val providers = providerRegistry.getApplicableProviders(request)

        for (provider in providers) {
            val context = provider.resolveTypeContext(request)
            if (context != null && context.expectedType != null) {
                return context
            }
        }

        return request.typeContext
    }

    /**
     * Score all collected items.
     */
    private fun scoreItems(
        items: List<CollectedItem>,
        request: SmartCompletionRequest,
    ): List<ScoredCompletionItem> =
        items.map { collected ->
            val score = calculateScore(collected, request)
            val typeCompat = calculateTypeCompatibility(collected.returnType, request.typeContext)
            val matchRanges = calculateMatchRanges(collected.item.label, request.prefix)

            ScoredCompletionItem(
                item = collected.item,
                score = score,
                typeCompatibility = typeCompat,
                matchRanges = matchRanges,
            )
        }

    /**
     * Calculate relevance score for an item.
     */
    private fun calculateScore(
        collected: CollectedItem,
        request: SmartCompletionRequest,
    ): Int {
        var score = collected.priorityOverride ?: collected.item.priority

        // Type compatibility
        val typeContext = request.typeContext
        if (collected.returnType != null && typeContext.expectedType != null) {
            score += typeContext.compatibilityScore(collected.returnType)
            if (config.boostExactTypes &&
                collected.returnType.qualifiedName == typeContext.expectedType.qualifiedName
            ) {
                score += RelevanceFactors.TYPE_EXACT
            }
        }

        // Prefix matching
        val label = collected.item.label.lowercase()
        val prefix = request.prefix.lowercase()
        when {
            label == prefix -> score += RelevanceFactors.PREFIX_EXACT + 100
            label.startsWith(prefix) -> score += RelevanceFactors.PREFIX_MATCH
            matchesCamelCase(label, prefix) -> score += RelevanceFactors.CAMEL_CASE_MATCH
            label.contains(prefix) -> score += RelevanceFactors.CONTAINS_MATCH
        }

        // Deprecated penalty
        if ("deprecated" in collected.item.tags) {
            score += RelevanceFactors.DEPRECATED_PENALTY
        }

        // Preselect bonus
        if (collected.item.preselect) {
            score += RelevanceFactors.PRESELECT_BONUS
        }

        // Length penalty (prefer shorter names)
        score -= collected.item.label.length * RelevanceFactors.LENGTH_PENALTY_FACTOR

        return score
    }

    /**
     * Calculate type compatibility level.
     */
    private fun calculateTypeCompatibility(
        returnType: TypeInfo?,
        context: ExpectedTypeContext,
    ): TypeCompatibility {
        if (returnType == null || context.expectedType == null) {
            return TypeCompatibility.Incompatible
        }

        val expected = context.expectedType

        // Exact match
        if (returnType.qualifiedName == expected.qualifiedName) {
            return TypeCompatibility.Exact
        }

        // Subtype
        if (returnType.isAssignableTo(expected)) {
            return TypeCompatibility.Subtype
        }

        // Alternative match
        for (alt in context.alternativeTypes) {
            if (returnType.qualifiedName == alt.qualifiedName) {
                return TypeCompatibility.Alternative
            }
            if (returnType.isAssignableTo(alt)) {
                return TypeCompatibility.Related
            }
        }

        return TypeCompatibility.Incompatible
    }

    /**
     * Filter items by type compatibility for smart mode.
     */
    private fun filterByType(
        items: List<ScoredCompletionItem>,
        context: ExpectedTypeContext,
    ): List<ScoredCompletionItem> {
        if (context.expectedType == null) {
            return items
        }

        val compatible =
            items.filter { item ->
                item.typeCompatibility != TypeCompatibility.Incompatible
            }

        // If no compatible items, return items with minimum score
        if (compatible.isEmpty() && config.minTypeScore == 0) {
            return items
        }

        return compatible
    }

    /**
     * Calculate match ranges for highlighting.
     */
    private fun calculateMatchRanges(
        label: String,
        prefix: String,
    ): List<IndexRange> {
        if (prefix.isEmpty()) return emptyList()

        val ranges = mutableListOf<IndexRange>()
        val lowerLabel = label.lowercase()
        val lowerPrefix = prefix.lowercase()

        // Prefix match
        if (lowerLabel.startsWith(lowerPrefix)) {
            ranges.add(IndexRange(0, prefix.length))
            return ranges
        }

        // Contains match
        val idx = lowerLabel.indexOf(lowerPrefix)
        if (idx >= 0) {
            ranges.add(IndexRange(idx, idx + prefix.length))
            return ranges
        }

        // CamelCase match
        var prefixIdx = 0
        for (i in label.indices) {
            if (prefixIdx >= prefix.length) break
            if (label[i].equals(prefix[prefixIdx], ignoreCase = true)) {
                if (i == 0 || label[i].isUpperCase() || label[i - 1] == '_') {
                    ranges.add(IndexRange(i, i + 1))
                    prefixIdx++
                }
            }
        }

        return ranges
    }

    /**
     * Check if label matches prefix as CamelCase.
     */
    private fun matchesCamelCase(
        label: String,
        prefix: String,
    ): Boolean {
        if (prefix.isEmpty()) return true

        var prefixIdx = 0
        for (i in label.indices) {
            if (prefixIdx >= prefix.length) break
            val c = label[i]
            if (c.equals(prefix[prefixIdx], ignoreCase = true)) {
                if (i == 0 || c.isUpperCase() || (i > 0 && label[i - 1] == '_')) {
                    prefixIdx++
                }
            }
        }

        return prefixIdx == prefix.length
    }

    /**
     * Register a completion provider.
     */
    public fun registerProvider(provider: SmartCompletionProvider) {
        providerRegistry.register(provider)
    }

    /**
     * Get the provider registry for direct access.
     */
    public fun getProviderRegistry(): SmartCompletionProviderRegistry = providerRegistry
}

/**
 * Internal item with collection metadata.
 */
private data class CollectedItem(
    val item: CompletionItem,
    val returnType: TypeInfo?,
    val priorityOverride: Int?,
)

/**
 * Implementation of CompletionCollector.
 */
private class SmartCompletionCollectorImpl(
    private val limit: Int,
) : CompletionCollector {
    private val items = mutableListOf<CollectedItem>()
    private var stopped = false

    override fun add(
        item: CompletionItem,
        returnType: TypeInfo?,
    ) {
        if (stopped) return
        items.add(CollectedItem(item, returnType, null))
        if (items.size >= limit) {
            stopped = true
        }
    }

    override fun addWithPriority(
        item: CompletionItem,
        priority: Int,
        returnType: TypeInfo?,
    ) {
        if (stopped) return
        items.add(CollectedItem(item, returnType, priority))
        if (items.size >= limit) {
            stopped = true
        }
    }

    override fun shouldStop(): Boolean = stopped

    fun getItems(): List<CollectedItem> = items.toList()
}
