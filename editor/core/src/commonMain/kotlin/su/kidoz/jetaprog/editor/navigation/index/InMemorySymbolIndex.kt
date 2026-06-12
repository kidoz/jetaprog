package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.SearchScope

/**
 * In-memory implementation of [SymbolIndex].
 *
 * This implementation uses hash maps for O(1) exact lookups and
 * efficient prefix/pattern matching for incremental search.
 *
 * Thread safety: This implementation is NOT thread-safe. Access should
 * be synchronized externally or restricted to a single thread.
 */
public class InMemorySymbolIndex : SymbolIndex {
    // Primary storage: file path -> list of symbols
    private val fileToSymbols = mutableMapOf<String, MutableList<IndexedSymbol>>()

    // Secondary index: lowercase name -> list of symbols (for fast name lookup)
    private val nameIndex = mutableMapOf<String, MutableList<IndexedSymbol>>()

    // Listeners for index changes
    private val listeners = mutableListOf<SymbolIndexListener>()

    override val symbolCount: Int
        get() = fileToSymbols.values.sumOf { it.size }

    override val fileCount: Int
        get() = fileToSymbols.size

    override suspend fun findByName(
        name: String,
        scope: SearchScope,
    ): List<IndexedSymbol> {
        val lowerName = name.lowercase()
        return nameIndex[lowerName]
            ?.filter { it.name.equals(name, ignoreCase = true) }
            ?.filter { matchesScope(it, scope) }
            ?: emptyList()
    }

    override suspend fun findByPrefix(
        prefix: String,
        scope: SearchScope,
        limit: Int,
    ): List<IndexedSymbol> {
        if (prefix.isEmpty()) return emptyList()

        val lowerPrefix = prefix.lowercase()
        val results = mutableListOf<IndexedSymbol>()

        // Search through name index entries that start with the prefix
        for ((key, symbols) in nameIndex) {
            if (key.startsWith(lowerPrefix)) {
                for (symbol in symbols) {
                    if (matchesScope(symbol, scope)) {
                        results.add(symbol)
                        if (results.size >= limit) {
                            return results
                        }
                    }
                }
            }
        }

        return results
    }

    override suspend fun findByPattern(
        pattern: String,
        scope: SearchScope,
        limit: Int,
    ): List<IndexedSymbolMatch> {
        if (pattern.isEmpty()) return emptyList()

        val matcher = PatternMatcher(pattern)
        val results = mutableListOf<IndexedSymbolMatch>()

        for (symbols in fileToSymbols.values) {
            for (symbol in symbols) {
                if (!matchesScope(symbol, scope)) continue

                val match = matcher.match(symbol.name)
                if (match != null) {
                    results.add(
                        IndexedSymbolMatch(
                            symbol = symbol,
                            score = match.score,
                            matchRanges = match.ranges,
                        ),
                    )

                    // Early exit if we have enough high-quality matches
                    if (results.size >= limit * 2) {
                        break
                    }
                }
            }
        }

        // Sort by score and limit
        return results.sorted().take(limit)
    }

    override suspend fun findByKind(
        kind: NavigationSymbolKind,
        scope: SearchScope,
        limit: Int,
    ): List<IndexedSymbol> {
        val results = mutableListOf<IndexedSymbol>()

        for (symbols in fileToSymbols.values) {
            for (symbol in symbols) {
                if (symbol.kind == kind && matchesScope(symbol, scope)) {
                    results.add(symbol)
                    if (results.size >= limit) {
                        return results
                    }
                }
            }
        }

        return results
    }

    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbol> =
        fileToSymbols[filePath]
            ?.sortedBy { it.offset }
            ?: emptyList()

    override fun indexFile(
        filePath: String,
        symbols: List<IndexedSymbol>,
    ) {
        // Remove existing symbols for this file first
        removeFile(filePath)

        if (symbols.isEmpty()) return

        // Add to primary storage
        val symbolList = symbols.toMutableList()
        fileToSymbols[filePath] = symbolList

        // Add to name index
        for (symbol in symbols) {
            val lowerName = symbol.name.lowercase()
            nameIndex.getOrPut(lowerName) { mutableListOf() }.add(symbol)
        }

        // Notify listeners
        for (listener in listeners) {
            listener.onFileIndexed(filePath, symbols.size)
        }
    }

    override fun removeFile(filePath: String) {
        val symbols = fileToSymbols.remove(filePath) ?: return

        // Remove from name index
        for (symbol in symbols) {
            val lowerName = symbol.name.lowercase()
            nameIndex[lowerName]?.remove(symbol)
            if (nameIndex[lowerName]?.isEmpty() == true) {
                nameIndex.remove(lowerName)
            }
        }

        // Notify listeners
        for (listener in listeners) {
            listener.onFileRemoved(filePath)
        }
    }

    override fun clear() {
        fileToSymbols.clear()
        nameIndex.clear()

        // Notify listeners
        for (listener in listeners) {
            listener.onIndexCleared()
        }
    }

    /**
     * Add a listener for index changes.
     */
    public fun addListener(listener: SymbolIndexListener) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    public fun removeListener(listener: SymbolIndexListener) {
        listeners.remove(listener)
    }

    private fun matchesScope(
        symbol: IndexedSymbol,
        scope: SearchScope,
    ): Boolean =
        when (scope) {
            SearchScope.PROJECT -> true

            SearchScope.ALL_WITH_LIBRARIES -> true

            SearchScope.FILE -> false

            // Requires specific file context
            SearchScope.MODULE -> true

            // TODO: Implement module scoping
            SearchScope.OPEN_FILES -> false // Requires open file context
        }
}

/**
 * Pattern matching result.
 */
internal data class MatchResult(
    val score: Int,
    val ranges: List<IndexRange>,
)

/**
 * Pattern matcher for symbol search.
 *
 * Supports:
 * - Prefix matching: "MyC" -> "MyClass"
 * - CamelCase matching: "MC" -> "MyClass", "MainController"
 * - Subsequence matching: "mcs" -> "MyClassService"
 * - Case-insensitive matching
 */
internal class PatternMatcher(
    pattern: String,
) {
    private val lowerPattern = pattern.lowercase()
    private val patternChars = pattern.toCharArray()
    private val isUpperCase = pattern.all { it.isUpperCase() }

    /**
     * Match the pattern against a symbol name.
     *
     * @return MatchResult if matches, null otherwise
     */
    fun match(name: String): MatchResult? {
        if (name.isEmpty() || lowerPattern.isEmpty()) return null

        val lowerName = name.lowercase()

        // 1. Exact match (highest score)
        if (lowerName == lowerPattern) {
            return MatchResult(
                score = SCORE_EXACT,
                ranges = listOf(IndexRange(0, name.length)),
            )
        }

        // 2. Prefix match
        if (lowerName.startsWith(lowerPattern)) {
            return MatchResult(
                score = SCORE_PREFIX + (MAX_LENGTH_BONUS - name.length).coerceAtLeast(0),
                ranges = listOf(IndexRange(0, lowerPattern.length)),
            )
        }

        // 3. CamelCase matching (e.g., "MC" matches "MyClass")
        if (isUpperCase && patternChars.size <= countUpperCaseLetters(name)) {
            val camelMatch = matchCamelCase(name)
            if (camelMatch != null) {
                return camelMatch
            }
        }

        // 4. Subsequence matching
        val subseqMatch = matchSubsequence(lowerName)
        if (subseqMatch != null) {
            return subseqMatch
        }

        // 5. Contains match (lowest score)
        val containsIdx = lowerName.indexOf(lowerPattern)
        if (containsIdx >= 0) {
            return MatchResult(
                score = SCORE_CONTAINS - containsIdx,
                ranges = listOf(IndexRange(containsIdx, containsIdx + lowerPattern.length)),
            )
        }

        return null
    }

    private fun matchCamelCase(name: String): MatchResult? {
        val ranges = mutableListOf<IndexRange>()
        var patternIdx = 0

        for (i in name.indices) {
            if (patternIdx >= patternChars.size) break

            val c = name[i]
            if (c.isUpperCase() && c == patternChars[patternIdx]) {
                ranges.add(IndexRange(i, i + 1))
                patternIdx++
            }
        }

        return if (patternIdx == patternChars.size) {
            MatchResult(
                score = SCORE_CAMEL_CASE + (MAX_LENGTH_BONUS - name.length).coerceAtLeast(0),
                ranges = ranges,
            )
        } else {
            null
        }
    }

    private fun matchSubsequence(lowerName: String): MatchResult? {
        val ranges = mutableListOf<IndexRange>()
        var patternIdx = 0
        var rangeStart = -1
        var consecutiveBonus = 0

        for (i in lowerName.indices) {
            if (patternIdx >= lowerPattern.length) break

            if (lowerName[i] == lowerPattern[patternIdx]) {
                if (rangeStart == -1) {
                    rangeStart = i
                }
                patternIdx++
                consecutiveBonus += CONSECUTIVE_BONUS
            } else if (rangeStart != -1) {
                ranges.add(IndexRange(rangeStart, i))
                rangeStart = -1
            }
        }

        // Close final range
        if (rangeStart != -1) {
            val matched = ranges.sumOf { it.end - it.start }
            ranges.add(IndexRange(rangeStart, rangeStart + (patternIdx - matched)))
        }

        return if (patternIdx == lowerPattern.length) {
            MatchResult(
                score = SCORE_SUBSEQUENCE + consecutiveBonus + (MAX_LENGTH_BONUS - lowerName.length).coerceAtLeast(0),
                ranges = ranges,
            )
        } else {
            null
        }
    }

    private fun countUpperCaseLetters(s: String): Int = s.count { it.isUpperCase() }

    private companion object {
        const val SCORE_EXACT = 1000
        const val SCORE_PREFIX = 800
        const val SCORE_CAMEL_CASE = 600
        const val SCORE_SUBSEQUENCE = 400
        const val SCORE_CONTAINS = 200
        const val MAX_LENGTH_BONUS = 50
        const val CONSECUTIVE_BONUS = 5
    }
}
