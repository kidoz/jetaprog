package su.kidoz.jetaprog.app

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpProjectConfigTest {
    @Test
    fun writesAnOwnerOnlyConfigAndExcludesItLocally() {
        val root = Files.createTempDirectory("mcp-config").toFile()
        try {
            assumeTrue(supportsPosix(root), "POSIX file permissions are unavailable")
            File(root, ".git/info").mkdirs()

            writeProjectMcpConfig(root.path, "http://127.0.0.1:9/mcp", "token-value")
            writeProjectMcpConfig(root.path, "http://127.0.0.1:9/mcp", "token-value")

            val config = File(root, ".mcp.json")
            assertTrue(config.isFile)
            assertTrue(config.readText().contains("Bearer token-value"))
            assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(config.toPath()),
            )
            assertEquals(
                listOf(".mcp.json"),
                File(root, ".git/info/exclude").readLines().filter { it.trim() == ".mcp.json" },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun excludeFollowsAGitdirPointer() {
        val root = Files.createTempDirectory("mcp-worktree").toFile()
        val gitDir = Files.createTempDirectory("mcp-gitdir").toFile()
        try {
            File(gitDir, "info").mkdirs()
            File(root, ".git").writeText("gitdir: ${gitDir.absolutePath}\n")

            writeProjectMcpConfig(root.path, "http://127.0.0.1:9/mcp", "token-value")

            assertTrue(File(gitDir, "info/exclude").readText().contains(".mcp.json"))
        } finally {
            root.deleteRecursively()
            gitDir.deleteRecursively()
        }
    }

    private fun supportsPosix(directory: File): Boolean =
        runCatching { Files.getPosixFilePermissions(directory.toPath()) }.isSuccess
}
