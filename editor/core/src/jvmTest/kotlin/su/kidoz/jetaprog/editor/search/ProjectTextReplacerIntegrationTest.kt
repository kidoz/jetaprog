package su.kidoz.jetaprog.editor.search

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test for [ProjectTextReplacer] over a real temporary directory,
 * verifying writes, traversal exclusions, and skip handling.
 */
class ProjectTextReplacerIntegrationTest {
    private val root: File = Files.createTempDirectory("jetaprog-replace-it").toFile()
    private val replacer = ProjectTextReplacer(JvmFileSystem())

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(
        relativePath: String,
        content: String,
    ): File {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun rewritesMatchingFilesAndSkipsExcludedDirectories() =
        runTest {
            val a = write("src/a.kt", "fun target() = 1\n")
            val b = write("src/sub/b.kt", "val target = 2\n")
            val excluded = write("build/generated.kt", "fun target() = 3\n")

            val results =
                replacer.replaceAll(
                    root.absolutePath,
                    TextSearchQuery("target"),
                    "goal",
                )

            assertEquals("fun goal() = 1\n", a.readText())
            assertEquals("val goal = 2\n", b.readText())
            assertEquals("fun target() = 3\n", excluded.readText(), "build/ must be excluded")
            assertEquals(
                listOf(
                    FileReplaceStatus.REPLACED,
                    FileReplaceStatus.REPLACED,
                ),
                results.map { it.status },
            )
            assertEquals(listOf(1, 1), results.map { it.replaced })
        }

    @Test
    fun leavesFilesWithoutMatchesUnreportedAndUntouched() =
        runTest {
            val untouched = write("src/keep.kt", "nothing to see\n")
            val changed = write("src/change.kt", "old old\n")

            val results =
                replacer.replaceAll(
                    root.absolutePath,
                    TextSearchQuery("old"),
                    "new",
                )

            assertEquals("nothing to see\n", untouched.readText())
            assertEquals("new new\n", changed.readText())
            assertTrue(results.all { it.filePath.endsWith("change.kt") })
        }

    @Test
    fun skippedPathsAreReportedAndNotWritten() =
        runTest {
            val dirty = write("src/dirty.kt", "old\n")
            val clean = write("src/clean.kt", "old\n")

            val results =
                replacer.replaceAll(
                    root.absolutePath,
                    TextSearchQuery("old"),
                    "new",
                    skipPaths = setOf(dirty.absolutePath),
                )

            assertEquals("old\n", dirty.readText(), "skipped file must not be rewritten")
            assertEquals("new\n", clean.readText())
            val statuses = results.associate { File(it.filePath).name to it.status }
            assertEquals(FileReplaceStatus.SKIPPED, statuses["dirty.kt"])
            assertEquals(FileReplaceStatus.REPLACED, statuses["clean.kt"])
        }

    @Test
    fun replacingIsIdempotentBecauseContentIsReRead() =
        runTest {
            val file = write("src/changing.kt", "old old\n")

            val first = replacer.replaceAll(root.absolutePath, TextSearchQuery("old"), "new")
            val second = replacer.replaceAll(root.absolutePath, TextSearchQuery("old"), "new")

            assertEquals(listOf(FileReplaceStatus.REPLACED), first.map { it.status })
            assertEquals("new new\n", file.readText())
            assertTrue(second.isEmpty(), "second run must see the rewritten content")
        }

    @Test
    fun invalidRegexQueryReplacesNothing() =
        runTest {
            val file = write("src/a.kt", "old\n")

            val results =
                replacer.replaceAll(
                    root.absolutePath,
                    TextSearchQuery("(", regex = true),
                    "new",
                )

            assertTrue(results.isEmpty())
            assertEquals("old\n", file.readText())
        }

    @Test
    fun regexGroupReferencesAreExpandedInWrittenFiles() =
        runTest {
            val file = write("src/pairs.kt", "firstName, lastName\n")

            replacer.replaceAll(
                root.absolutePath,
                TextSearchQuery("(\\w+), (\\w+)", regex = true),
                "$2 $1",
            )

            assertEquals("lastName firstName\n", file.readText())
        }
}
