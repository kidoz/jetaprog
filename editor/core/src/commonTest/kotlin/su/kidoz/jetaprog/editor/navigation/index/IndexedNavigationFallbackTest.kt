package su.kidoz.jetaprog.editor.navigation.index

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.UsageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The index-backed fallbacks used to be decorative: Go to File matched symbol
 * names, Go to Definition only resolved with the caret already on a declaration,
 * and Find Usages listed same-named declarations instead of occurrences.
 */
class IndexedNavigationFallbackTest {
    private val greeter =
        """
        public class Greeter {
            public String greet(String name) { return "hi " + name; }
        }
        """.trimIndent()

    private val main =
        """
        public class Main {
            public void run() { new Greeter().greet("x"); }
        }
        """.trimIndent()

    private val files = mapOf("/w/Greeter.java" to greeter, "/w/Main.java" to main)

    private val contentProvider =
        object : FileContentProvider {
            override fun getOffset(
                filePath: String,
                position: TextPosition,
            ): Int? {
                val lines = files[filePath]?.split('\n') ?: return null
                return lines.take(position.line).sumOf { it.length + 1 } + position.column
            }

            override fun getPosition(
                filePath: String,
                offset: Int,
            ): TextPosition? = null

            override fun getContent(filePath: String): String? = files[filePath]
        }

    private fun service(): IndexedNavigationService {
        val index = InMemorySymbolIndex()
        val indexer = SymbolIndexer(index).apply { registerExtractor(JavaSymbolExtractor()) }
        files.forEach { (path, content) -> indexer.indexFile(path, content) }
        return IndexedNavigationService(symbolIndex = index, fileContentProvider = contentProvider)
    }

    @Test
    fun goToFileMatchesFileNames() =
        runTest {
            val results = service().searchFiles("Greet", limit = 10)

            assertEquals(listOf("/w/Greeter.java"), results.map { it.target.filePath })
        }

    @Test
    fun goToDefinitionResolvesAUseByName() =
        runTest {
            val column = main.split('\n')[1].indexOf("greet(")

            val target = service().getDefinition("/w/Main.java", TextPosition(1, column))

            assertNotNull(target)
            assertEquals("greet", target.name)
            assertEquals("/w/Greeter.java", target.filePath)
        }

    @Test
    fun findUsagesListsOccurrencesAcrossIndexedFiles() =
        runTest {
            val column = greeter.split('\n')[1].indexOf("greet(")

            val result = service().findUsages("/w/Greeter.java", TextPosition(1, column))

            assertNotNull(result)
            assertEquals(2, result.totalCount)
            val byFile = result.groups.associate { it.filePath to it.usages.single().usageKind }
            assertEquals(UsageKind.DEFINITION, byFile["/w/Greeter.java"])
            assertEquals(UsageKind.UNKNOWN, byFile["/w/Main.java"])
        }
}
