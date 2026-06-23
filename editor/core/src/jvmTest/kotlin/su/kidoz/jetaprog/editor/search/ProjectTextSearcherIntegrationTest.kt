package su.kidoz.jetaprog.editor.search

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end test for [ProjectTextSearcher] over a real temporary directory,
 * verifying traversal, matching and exclusion of build directories.
 */
class ProjectTextSearcherIntegrationTest {
    private val root: File = Files.createTempDirectory("jetaprog-search-it").toFile()
    private val searcher = ProjectTextSearcher(JvmFileSystem())

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(
        relativePath: String,
        content: String,
    ) {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun findsMatchesAndSkipsExcludedDirectories() =
        runTest {
            write("src/a.kt", "fun target() = 1\n")
            write("src/sub/b.kt", "// nothing here\nval target = 2\n")
            write("build/generated.kt", "fun target() = 3\n")

            val results = searcher.search(root.absolutePath, TextSearchQuery("target"))
            val paths = results.map { it.filePath }

            assertTrue(paths.any { it.endsWith("a.kt") }, "expected a match in src/a.kt: $paths")
            assertTrue(paths.any { it.endsWith("b.kt") }, "expected a match in src/sub/b.kt: $paths")
            assertTrue(paths.none { it.contains("/build/") }, "build/ should be excluded: $paths")
        }
}
