package su.kidoz.jetaprog.editor.navigation.index

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.SearchScope

/**
 * A symbol stored in the index.
 *
 * This is a lightweight representation optimized for fast lookup and storage.
 * It contains the minimal information needed for symbol search and navigation.
 */
@Serializable
public data class IndexedSymbol(
    /**
     * The simple name of the symbol (e.g., "MyClass", "doSomething").
     */
    val name: String,
    /**
     * The fully qualified name (e.g., "com.example.MyClass").
     */
    val qualifiedName: String,
    /**
     * The kind of symbol.
     */
    val kind: NavigationSymbolKind,
    /**
     * The file path where this symbol is defined.
     */
    val filePath: String,
    /**
     * The byte offset in the file where the symbol starts.
     */
    val offset: Int,
    /**
     * The length of the symbol name in the source.
     */
    val nameLength: Int = name.length,
    /**
     * The name of the containing symbol (e.g., class name for a method).
     */
    val containerName: String? = null,
    /**
     * Additional signature or type information.
     */
    val signature: String? = null,
    /**
     * Language identifier for this symbol.
     */
    val languageId: String = "unknown",
)

/**
 * Interface for a symbol index that supports fast symbol lookup.
 *
 * The index maintains a mapping of symbol names to their locations,
 * enabling efficient "Go to Symbol" functionality without requiring LSP.
 *
 * Inspired by IntelliJ IDEA's stub index and VS Code's workspace symbol provider.
 */
public interface SymbolIndex {
    /**
     * Find symbols by exact name match.
     *
     * @param name The exact symbol name to search for
     * @param scope The scope to search in
     * @return List of matching symbols
     */
    public suspend fun findByName(
        name: String,
        scope: SearchScope = SearchScope.PROJECT,
    ): List<IndexedSymbol>

    /**
     * Find symbols by name prefix.
     *
     * @param prefix The prefix to match
     * @param scope The scope to search in
     * @param limit Maximum number of results
     * @return List of matching symbols
     */
    public suspend fun findByPrefix(
        prefix: String,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 100,
    ): List<IndexedSymbol>

    /**
     * Find symbols matching a pattern with camelCase support.
     *
     * Supports patterns like:
     * - "MyC" matches "MyClass", "MyController"
     * - "myCl" matches "MyClass" (case-insensitive start)
     * - "MC" matches "MyClass", "MainController" (camelCase initials)
     *
     * @param pattern The pattern to match
     * @param scope The scope to search in
     * @param limit Maximum number of results
     * @return List of matching symbols with scores
     */
    public suspend fun findByPattern(
        pattern: String,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 100,
    ): List<IndexedSymbolMatch>

    /**
     * Find all symbols of a specific kind.
     *
     * @param kind The symbol kind to filter by
     * @param scope The scope to search in
     * @param limit Maximum number of results
     * @return List of matching symbols
     */
    public suspend fun findByKind(
        kind: NavigationSymbolKind,
        scope: SearchScope = SearchScope.PROJECT,
        limit: Int = 100,
    ): List<IndexedSymbol>

    /**
     * Get all symbols in a specific file.
     *
     * @param filePath The file path
     * @return List of symbols in the file, ordered by offset
     */
    public suspend fun getFileSymbols(filePath: String): List<IndexedSymbol>

    /**
     * Index symbols for a file.
     *
     * @param filePath The file path
     * @param symbols The symbols to index
     */
    public fun indexFile(
        filePath: String,
        symbols: List<IndexedSymbol>,
    )

    /**
     * Remove all symbols for a file from the index.
     *
     * @param filePath The file path
     */
    public fun removeFile(filePath: String)

    /**
     * Clear the entire index.
     */
    public fun clear()

    /**
     * Get the total number of indexed symbols.
     */
    public val symbolCount: Int

    /**
     * Get the number of indexed files.
     */
    public val fileCount: Int
}

/**
 * A range representing matched characters in a symbol name.
 */
@Serializable
public data class IndexRange(
    /**
     * Start index (inclusive).
     */
    val start: Int,
    /**
     * End index (exclusive).
     */
    val end: Int,
) {
    /**
     * Convert to IntRange.
     */
    public fun toIntRange(): IntRange = start until end

    public companion object {
        /**
         * Create from IntRange.
         */
        public fun fromIntRange(range: IntRange): IndexRange = IndexRange(range.first, range.last + 1)
    }
}

/**
 * A symbol match with scoring information for ranking results.
 */
@Serializable
public data class IndexedSymbolMatch(
    /**
     * The matched symbol.
     */
    val symbol: IndexedSymbol,
    /**
     * Match score (higher is better).
     */
    val score: Int,
    /**
     * Ranges in the name that matched (for highlighting).
     */
    val matchRanges: List<IndexRange> = emptyList(),
) : Comparable<IndexedSymbolMatch> {
    override fun compareTo(other: IndexedSymbolMatch): Int =
        // Higher score first, then alphabetically by name
        when {
            score != other.score -> other.score - score
            else -> symbol.name.compareTo(other.symbol.name)
        }
}

/**
 * Listener for index changes.
 */
public interface SymbolIndexListener {
    /**
     * Called when a file has been indexed.
     *
     * @param filePath The file path
     * @param symbolCount Number of symbols indexed
     */
    public fun onFileIndexed(
        filePath: String,
        symbolCount: Int,
    ) {
    }

    /**
     * Called when a file has been removed from the index.
     *
     * @param filePath The file path
     */
    public fun onFileRemoved(filePath: String) {}

    /**
     * Called when the index has been cleared.
     */
    public fun onIndexCleared() {}
}
