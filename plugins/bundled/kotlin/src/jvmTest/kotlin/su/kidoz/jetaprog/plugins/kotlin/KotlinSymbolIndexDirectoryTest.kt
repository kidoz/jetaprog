package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The index walked build output (duplicating every generated symbol) and skipped
 * Kotlin scripts such as build.gradle.kts entirely.
 */
class KotlinSymbolIndexDirectoryTest {
    private val root = Files.createTempDirectory("jetaprog-kotlin-index").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(
        path: String,
        text: String,
    ) = File(root, path).apply { parentFile.mkdirs() }.writeText(text)

    @Test
    fun indexesSourcesAndScriptsButNotBuildOutput() =
        runBlocking {
            write("src/Greeter.kt", "class Greeter\n")
            write("build.gradle.kts", "fun configure() {}\n")
            write("build/generated/Generated.kt", "class Generated\n")
            write(".git/hooks/Hook.kt", "class Hook\n")
            val index = KotlinSymbolIndex()

            index.indexDirectory(root.absolutePath)

            assertEquals(1, index.findByName("Greeter").size)
            assertEquals(1, index.findByName("configure").size)
            assertEquals(0, index.findByName("Generated").size)
            assertEquals(0, index.findByName("Hook").size)
        }
}
