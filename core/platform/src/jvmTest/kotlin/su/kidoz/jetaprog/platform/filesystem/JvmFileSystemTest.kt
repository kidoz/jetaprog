package su.kidoz.jetaprog.platform.filesystem

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmFileSystemTest {
    private val fileSystem = JvmFileSystem()

    @Test
    fun writeTextReplacesExistingFileAndCleansTemporarySibling() =
        runTest {
            val root = Files.createTempDirectory("jvm-file-system-write")
            try {
                val target = root.resolve("document.txt")
                Files.writeString(target, "original")
                val content = "first\r\nsecond\r\n"

                fileSystem.writeText(target.toString(), content).getOrThrow()

                assertEquals(content, Files.readString(target))
                assertEquals(listOf("document.txt"), entryNames(root))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun writeTextCreatesParentsAndCleansTemporarySibling() =
        runTest {
            val root = Files.createTempDirectory("jvm-file-system-parents")
            try {
                val target = root.resolve("nested/docs/document.txt")

                fileSystem.writeText(target.toString(), "new content").getOrThrow()

                assertEquals("new content", Files.readString(target))
                assertEquals(listOf("document.txt"), entryNames(target.parent))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun writeTextFailurePreservesOriginalFileAndCleansTemporarySibling() =
        runTest {
            val root = Files.createTempDirectory("jvm-file-system-failure")
            try {
                val target = root.resolve("document.txt")
                Files.writeString(target, "original")

                val result = fileSystem.writeText(target.toString(), "replacement", "not-a-charset")

                assertTrue(result.isFailure)
                assertEquals("original", Files.readString(target))
                assertEquals(listOf("document.txt"), entryNames(root))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun failedReplacementCleansTemporarySiblingAndPreservesTarget() =
        runTest {
            val root = Files.createTempDirectory("jvm-file-system-replacement-failure")
            try {
                val target = Files.createDirectory(root.resolve("document.txt"))
                val original = target.resolve("keep.txt")
                Files.writeString(original, "original")

                val result = fileSystem.writeText(target.toString(), "replacement")

                assertTrue(result.isFailure)
                assertEquals("original", Files.readString(original))
                assertEquals(listOf("document.txt"), entryNames(root))
                assertEquals(listOf("keep.txt"), entryNames(target))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    private fun entryNames(directory: Path): List<String> =
        Files.list(directory).use { paths ->
            paths.map { it.fileName.toString() }.sorted().toList()
        }
}
