package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinContextNominatorTest {
    private val root = Files.createTempDirectory("jetaprog-nominator").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun nominatesFilesDeclaringReferencedNamesMostRelevantFirst() =
        runBlocking {
            val helpers = File(root, "Helpers.kt").apply { writeText("fun helper() = 1\nfun other() = 2\n") }
            val unrelated = File(root, "Unrelated.kt").apply { writeText("fun nobody() = 3\n") }
            val self = File(root, "Main.kt").apply { writeText("fun main() { helper(); other() }\n") }
            val index = KotlinSymbolIndex().apply { indexDirectory(root.absolutePath) }
            val nominator = KotlinContextNominator(index)

            val nominated = nominator.nominate(self.readText(), self.absolutePath, limit = 5)

            assertEquals(listOf(helpers.absolutePath), nominated)
            assertTrue(unrelated.absolutePath !in nominated)
            assertTrue(nominator.declares("helper"))
            assertTrue(!nominator.declares("missing"))
        }
}
