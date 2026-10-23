package su.kidoz.jetaprog.app.navigation

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.mcp.formatIndexedSymbols
import su.kidoz.jetaprog.editor.navigation.index.InMemorySymbolIndex
import su.kidoz.jetaprog.editor.navigation.index.JavaSymbolExtractor
import su.kidoz.jetaprog.editor.navigation.index.SymbolIndexer
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * search_symbol reads the indexes Go to Symbol already fills. A language with no
 * extractor contributes nothing, and the report uses a project-relative path with
 * a 1-based line.
 */
class IndexedSymbolSearchTest {
    @Test
    fun findsJavaAndKotlinSymbolsAndSkipsALanguageTheIndexDoesNotExtract() =
        runTest {
            val root = tempRoot()
            try {
                val javaPath = File(root, "src/Greeter.java").absolutePath
                val workspace = InMemorySymbolIndex()
                val indexer = SymbolIndexer(workspace).apply { registerExtractor(JavaSymbolExtractor()) }
                indexer.indexFile(
                    javaPath,
                    listOf(
                        "package sample;",
                        "",
                        "public class Greeter {",
                        "    public String greet(String name) { return name; }",
                        "}",
                    ).joinToString("\n"),
                )
                indexer.indexFile(File(root, "readme.md").absolutePath, "class Ghost {}")

                val kotlinIndex = KotlinSymbolIndex()
                val kotlinFile = File(root, "src/Widget.kt")
                kotlinFile.parentFile.mkdirs()
                kotlinFile.writeText(
                    listOf(
                        "class Widget {",
                        "    fun paint() {}",
                        "}",
                    ).joinToString("\n"),
                )
                kotlinIndex.indexFile(kotlinFile.absolutePath)

                val search = IndexedSymbolSearch(workspace, kotlinIndex)

                val greeter = search.search("Greeter", limit = 20).single()
                assertEquals("Greeter", greeter.name)
                assertEquals("CLASS", greeter.kind)
                assertEquals(2, greeter.line)
                assertEquals(
                    "Greeter CLASS src/Greeter.java:3",
                    formatIndexedSymbols(root.absolutePath, listOf(greeter)),
                )

                val paint = search.search("paint", limit = 20).single()
                assertEquals("FUNCTION", paint.kind)
                assertEquals("paint FUNCTION src/Widget.kt:2", formatIndexedSymbols(root.absolutePath, listOf(paint)))

                assertTrue(search.search("Ghost", limit = 20).isEmpty())
                assertTrue(search.search("   ", limit = 20).isEmpty())
                assertTrue(search.search("Greeter", limit = 0).isEmpty())
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun stopsAtTheRequestedLimit() =
        runTest {
            val root = tempRoot()
            try {
                val workspace = InMemorySymbolIndex()
                val indexer = SymbolIndexer(workspace).apply { registerExtractor(JavaSymbolExtractor()) }
                val source = (1..5).joinToString("\n\n") { "public class Item$it {}" }
                indexer.indexFile(File(root, "src/Items.java").absolutePath, source)

                val hits = IndexedSymbolSearch(workspace, KotlinSymbolIndex()).search("Item", limit = 2)

                assertEquals(listOf("Item1", "Item2"), hits.map { it.name })
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun keepsAPathOutsideTheProjectAbsolute() {
        val hit =
            IndexedSymbolLookup(
                name = "Outside",
                kind = "CLASS",
                filePath = "/other/Outside.java",
                line = 0,
            )

        assertEquals(
            "Outside CLASS /other/Outside.java:1",
            formatIndexedSymbols("/proj", listOf(hit)),
        )
        assertEquals("No matches.", formatIndexedSymbols("/proj", emptyList()))
    }

    private fun tempRoot(): File = File(System.getProperty("java.io.tmpdir"), "symbol-search-${System.nanoTime()}")
}
