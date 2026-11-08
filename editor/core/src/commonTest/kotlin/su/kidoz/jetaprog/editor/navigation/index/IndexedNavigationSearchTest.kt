package su.kidoz.jetaprog.editor.navigation.index

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.editor.navigation.NavigationSearchResult
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.SearchCategory
import su.kidoz.jetaprog.editor.navigation.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals

/** Search combines language-provider and workspace-index results without losing declarations. */
class IndexedNavigationSearchTest {
    @Test
    fun classSearchIncludesWorkspaceMatchesWhenTheDelegateAlsoMatches() =
        runTest {
            val service = service(listOf(symbol("/w/Greeter.kt")), listOf(symbol("/w/Greeter.java")))

            assertEquals(
                setOf("/w/Greeter.kt", "/w/Greeter.java"),
                service.searchClasses("Greeter").map { it.target.filePath }.toSet(),
            )
        }

    @Test
    fun symbolSearchKeepsSameNamedDeclarationsAtDifferentLocations() =
        runTest {
            val service =
                service(
                    listOf(symbol("/w/Greeter.kt", kind = NavigationSymbolKind.FUNCTION)),
                    listOf(
                        symbol("/w/Greeter.java", kind = NavigationSymbolKind.METHOD),
                        symbol("/w/Greeter.java", kind = NavigationSymbolKind.METHOD, column = 20),
                    ),
                )

            assertEquals(3, service.searchSymbols("Greeter").size)
        }

    @Test
    fun duplicateLocationsKeepDelegateMetadata() =
        runTest {
            val indexed = symbol("/w/Greeter.java")
            val authoritative = indexed.copy(signature = "public class Greeter", qualifiedName = "sample.Greeter")
            val provider = service(listOf(authoritative), emptyList())
            val delegate =
                object : NavigationService by provider {
                    override suspend fun searchClasses(
                        query: String,
                        scope: SearchScope,
                        limit: Int,
                    ): List<NavigationSearchResult> =
                        provider.searchClasses(query, scope, limit).map { it.copy(score = -1) }

                    override suspend fun searchSymbols(
                        query: String,
                        scope: SearchScope,
                        limit: Int,
                    ): List<NavigationSearchResult> =
                        provider.searchSymbols(query, scope, limit).map { it.copy(score = -1) }
                }
            val index = InMemorySymbolIndex().apply { indexFile(indexed.filePath, listOf(indexed)) }
            val service = IndexedNavigationService(symbolIndex = index, lspDelegate = delegate)

            for (results in listOf(service.searchClasses("Greeter"), service.searchSymbols("Greeter"))) {
                assertEquals("public class Greeter", results.single().target.detail)
                assertEquals("sample.Greeter", results.single().target.qualifiedName)
            }
        }

    @Test
    fun mergedResultsAreRankedAndLimitedAfterCombiningSources() =
        runTest {
            val service =
                service(
                    listOf(symbol("/w/GreeterFactory.kt").copy(name = "GreeterFactory")),
                    listOf(symbol("/w/Greeter.java")),
                )

            assertEquals(
                "Greeter",
                service
                    .searchClasses("Greeter", limit = 1)
                    .single()
                    .target.name,
            )
            assertEquals(
                "Greeter",
                service
                    .searchSymbols("Greeter", limit = 1)
                    .single()
                    .target.name,
            )
            assertEquals(emptyList(), service.searchClasses("Greeter", limit = 0))
            assertEquals(emptyList(), service.searchSymbols("Greeter", limit = 0))
        }

    @Test
    fun searchEverywhereIncludesBothSourcesInClassAndSymbolCategories() =
        runTest {
            val service = service(listOf(symbol("/w/Greeter.kt")), listOf(symbol("/w/Greeter.java")))

            val results = service.searchEverywhere("Greeter")

            for (category in listOf(SearchCategory.CLASSES, SearchCategory.SYMBOLS)) {
                assertEquals(
                    setOf("/w/Greeter.kt", "/w/Greeter.java"),
                    results.getValue(category).map { it.target.filePath }.toSet(),
                )
            }
        }

    @Test
    fun workspaceMatchesRespectTheRequestedScope() =
        runTest {
            val openFile = "/w/Greeter.java"
            val index = InMemorySymbolIndex(openFilesProvider = { setOf(openFile) })
            index.indexFile(openFile, listOf(symbol(openFile)))
            index.indexFile("/w/Closed.java", listOf(symbol("/w/Closed.java")))
            val service =
                IndexedNavigationService(
                    symbolIndex = index,
                    lspDelegate = IndexedNavigationService(InMemorySymbolIndex()),
                )

            assertEquals(
                listOf(openFile),
                service.searchClasses("Greeter", SearchScope.OPEN_FILES).map { it.target.filePath },
            )
            assertEquals(
                listOf(openFile),
                service.searchSymbols("Greeter", SearchScope.OPEN_FILES).map { it.target.filePath },
            )
        }

    private fun service(
        delegated: List<IndexedSymbol>,
        indexed: List<IndexedSymbol>,
    ): IndexedNavigationService {
        fun index(symbols: List<IndexedSymbol>): InMemorySymbolIndex =
            InMemorySymbolIndex().apply {
                symbols.groupBy { it.filePath }.forEach { (path, entries) -> indexFile(path, entries) }
            }

        return IndexedNavigationService(
            symbolIndex = index(indexed),
            lspDelegate = IndexedNavigationService(index(delegated)),
        )
    }

    private fun symbol(
        path: String,
        kind: NavigationSymbolKind = NavigationSymbolKind.CLASS,
        column: Int = 0,
    ): IndexedSymbol =
        IndexedSymbol(
            name = "Greeter",
            qualifiedName = "Greeter",
            kind = kind,
            filePath = path,
            offset = column,
            column = column,
        )
}
