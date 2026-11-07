package su.kidoz.jetaprog.platform.filesystem

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainedFileWalkerTest {
    @Test
    fun readsFilesInsideTheRootAndSkipsLinksThatLeaveIt() =
        runTest {
            val root = Files.createTempDirectory("contained-root").toFile().canonicalFile
            val outside = Files.createTempDirectory("contained-outside").toFile().canonicalFile
            try {
                val inside = File(root, "src/Inside.kt").apply { parentFile.mkdirs() }
                inside.writeText("inside")
                val secret = File(outside, "secret.txt").apply { writeText("secret") }
                val fileLink = File(root, "src/escape.txt")
                val directoryLink = File(root, "src/linked-dir")
                val fileLinked = runCatching { Files.createSymbolicLink(fileLink.toPath(), secret.toPath()) }.isSuccess
                val directoryLinked =
                    runCatching { Files.createSymbolicLink(directoryLink.toPath(), outside.toPath()) }.isSuccess
                assumeTrue(fileLinked && directoryLinked, "Symbolic links are unavailable on this platform")

                val visited = mutableListOf<String>()
                ContainedFileWalker(JvmFileSystem()).walk(root.path) { entry ->
                    visited += entry.name
                    true
                }

                assertTrue(visited.contains("Inside.kt"), "visited $visited")
                assertTrue("escape.txt" !in visited, "visited $visited")
                assertTrue("secret.txt" !in visited, "visited $visited")
            } finally {
                root.deleteRecursively()
                outside.deleteRecursively()
            }
        }

    @Test
    fun followsASymlinkWhoseTargetStaysInsideTheRoot() =
        runTest {
            val root = Files.createTempDirectory("contained-alias").toFile().canonicalFile
            try {
                val target = File(root, "src/Inside.kt").apply { parentFile.mkdirs() }
                target.writeText("inside")
                val link = File(root, "alias.kt")
                val linked = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
                assumeTrue(linked, "Symbolic links are unavailable on this platform")

                val visited = mutableListOf<String>()
                ContainedFileWalker(JvmFileSystem()).walk(root.path) { entry ->
                    visited += entry.name
                    true
                }

                assertEquals(setOf("Inside.kt", "alias.kt"), visited.toSet())
            } finally {
                root.deleteRecursively()
            }
        }
}
