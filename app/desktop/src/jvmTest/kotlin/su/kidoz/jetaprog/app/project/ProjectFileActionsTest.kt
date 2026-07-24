package su.kidoz.jetaprog.app.project

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectFileActionsTest {
    private lateinit var projectDir: File
    private lateinit var actions: ProjectFileActions

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("project-file-actions").toFile()
        actions = ProjectFileActions(JvmFileSystem())
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `creates an empty file`() =
        runTest {
            val result = actions.createFile(projectDir.absolutePath, "Main.kt")

            val success = assertIs<FileActionResult.Success>(result)
            val created = File(success.path)
            assertTrue(created.isFile)
            assertEquals("", created.readText())
        }

    @Test
    fun `creates a directory`() =
        runTest {
            val result = actions.createDirectory(projectDir.absolutePath, "sources")

            val success = assertIs<FileActionResult.Success>(result)
            assertTrue(File(success.path).isDirectory)
        }

    @Test
    fun `refuses to overwrite an existing entry`() =
        runTest {
            File(projectDir, "Main.kt").writeText("fun main() {}")

            val result = actions.createFile(projectDir.absolutePath, "Main.kt")

            val failure = assertIs<FileActionResult.Failure>(result)
            assertTrue("already exists" in failure.reason, failure.reason)
            assertEquals("fun main() {}", File(projectDir, "Main.kt").readText(), "content must survive")
        }

    @Test
    fun `rejects names that are not a single path segment`() =
        runTest {
            val nested = actions.createFile(projectDir.absolutePath, "pkg/Main.kt")
            val parent = actions.createFile(projectDir.absolutePath, "..")
            val blank = actions.createFile(projectDir.absolutePath, "   ")

            assertIs<FileActionResult.Failure>(nested)
            assertIs<FileActionResult.Failure>(parent)
            assertIs<FileActionResult.Failure>(blank)
            assertEquals(emptyList(), projectDir.listFiles()?.toList().orEmpty(), "nothing may be created")
        }

    @Test
    fun `allows spaces in names`() =
        runTest {
            val result = actions.createFile(projectDir.absolutePath, "release notes.md")

            val success = assertIs<FileActionResult.Success>(result)
            assertTrue(File(success.path).isFile)
        }

    @Test
    fun `renames a file in place`() =
        runTest {
            val original = File(projectDir, "Old.kt").apply { writeText("content") }

            val result = actions.rename(original.absolutePath, "New.kt")

            val success = assertIs<FileActionResult.Success>(result)
            assertFalse(original.exists())
            assertEquals("content", File(success.path).readText())
            assertEquals(projectDir.absolutePath, File(success.path).parentFile.absolutePath)
        }

    @Test
    fun `refuses a rename that would overwrite another file`() =
        runTest {
            val source = File(projectDir, "Old.kt").apply { writeText("source") }
            File(projectDir, "Taken.kt").writeText("target")

            val result = actions.rename(source.absolutePath, "Taken.kt")

            assertIs<FileActionResult.Failure>(result)
            assertTrue(source.exists(), "source must be left alone")
            assertEquals("target", File(projectDir, "Taken.kt").readText(), "target must not be overwritten")
        }

    @Test
    fun `deletes a directory and its contents`() =
        runTest {
            val directory = File(projectDir, "pkg").apply { mkdirs() }
            File(directory, "Nested.kt").writeText("nested")

            val result = actions.delete(directory.absolutePath)

            assertIs<FileActionResult.Success>(result)
            assertFalse(directory.exists())
        }

    @Test
    fun `reports a missing target instead of failing silently`() =
        runTest {
            val result = actions.delete(File(projectDir, "Ghost.kt").absolutePath)

            val failure = assertIs<FileActionResult.Failure>(result)
            assertTrue("no longer exists" in failure.reason, failure.reason)
        }
}
