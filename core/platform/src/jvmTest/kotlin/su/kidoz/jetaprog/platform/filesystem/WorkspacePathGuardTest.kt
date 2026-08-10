package su.kidoz.jetaprog.platform.filesystem

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspacePathGuardTest {
    private val workspace: File = Files.createTempDirectory("workspace").toFile().canonicalFile
    private val guard = WorkspacePathGuard { workspace.path }

    @Test
    fun resolvesRelativePathsAgainstTheWorkspace() {
        val resolved = guard.resolve("src/Main.kt")
        assertEquals(File(workspace, "src/Main.kt").path, resolved)
    }

    @Test
    fun acceptsAbsolutePathsInsideTheWorkspace() {
        val inside = File(workspace, "docs/readme.md")
        assertEquals(inside.path, guard.resolve(inside.path))
    }

    @Test
    fun rejectsParentTraversal() {
        assertFailsWith<WorkspacePathException> { guard.resolve("../outside.txt") }
        assertFailsWith<WorkspacePathException> { guard.resolve("src/../../outside.txt") }
    }

    @Test
    fun rejectsAbsolutePathsOutsideTheWorkspace() {
        assertFailsWith<WorkspacePathException> { guard.resolve("/etc/passwd") }
    }

    @Test
    fun rejectsSymlinksEscapingTheWorkspace() {
        val outside = Files.createTempDirectory("outside").toFile().canonicalFile
        val secret = File(outside, "secret.txt").apply { writeText("secret") }
        val link = File(workspace, "escape")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertFailsWith<WorkspacePathException> { guard.resolve("escape/secret.txt") }
        assertTrue(secret.exists())
    }

    @Test
    fun acceptsTheWorkspaceRootItself() {
        assertEquals(workspace.path, guard.resolve(workspace.path))
    }

    @Test
    fun reportsMissingWorkspace() {
        val closed = WorkspacePathGuard { null }
        assertFailsWith<WorkspacePathException> { closed.resolve("any.txt") }
        assertFalse(closed.isInsideWorkspace("any.txt"))
    }

    @Test
    fun isInsideWorkspaceMatchesResolve() {
        assertTrue(guard.isInsideWorkspace("src/Main.kt"))
        assertFalse(guard.isInsideWorkspace("../outside.txt"))
    }
}
