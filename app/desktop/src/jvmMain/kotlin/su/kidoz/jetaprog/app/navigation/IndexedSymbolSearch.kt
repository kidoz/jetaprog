package su.kidoz.jetaprog.app.navigation

import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.editor.navigation.index.IndexedSymbol
import su.kidoz.jetaprog.editor.navigation.index.SymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbol
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex

/**
 * One symbol found in a local index.
 *
 * @property name Symbol name.
 * @property kind Symbol kind, as the index recorded it.
 * @property filePath Absolute file path.
 * @property line Zero-based line of the symbol name.
 */
public data class IndexedSymbolLookup(
    val name: String,
    val kind: String,
    val filePath: String,
    val line: Int,
)

/**
 * Looks up names in the symbol indexes Go to Symbol already fills.
 *
 * The workspace index covers languages with a registered extractor. The Kotlin
 * index covers Kotlin. A blank query, or a name neither index extracted, returns
 * an empty list. This does not call a language server.
 *
 * @param workspaceIndex Generic workspace symbol index.
 * @param kotlinIndex Kotlin symbol index.
 */
public class IndexedSymbolSearch(
    private val workspaceIndex: SymbolIndex,
    private val kotlinIndex: KotlinSymbolIndex,
) {
    /**
     * Returns matches for [query], best workspace matches first, then Kotlin matches.
     *
     * @param limit Maximum number of matches.
     */
    public suspend fun search(
        query: String,
        limit: Int,
    ): List<IndexedSymbolLookup> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        val workspace =
            workspaceIndex
                .findByPattern(trimmed, SearchScope.PROJECT, limit)
                .map { it.symbol.toLookup() }
        val kotlin = kotlinIndex.search(trimmed, limit).map { it.toLookup() }
        return (workspace + kotlin)
            .distinctBy { listOf(it.name, it.kind, it.filePath, it.line) }
            .take(limit)
    }
}

private fun IndexedSymbol.toLookup(): IndexedSymbolLookup =
    IndexedSymbolLookup(
        name = name,
        kind = kind.name,
        filePath = filePath,
        line = line,
    )

private fun KotlinSymbol.toLookup(): IndexedSymbolLookup =
    IndexedSymbolLookup(
        name = name,
        kind = kind.name,
        filePath = filePath,
        line = nameRange.start.line,
    )
