package su.kidoz.jetaprog.vcs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the pure-file `.git` reader: branch/detached HEAD resolution and
 * the packed-refs vs loose-refs precedence.
 */
class GitRepoInfoReaderTest {
    private val repoDir: File = Files.createTempDirectory("jetaprog-gitinfo").toFile()

    @AfterTest
    fun tearDown() {
        repoDir.deleteRecursively()
    }

    private fun write(
        path: String,
        content: String,
    ) {
        val file = File(repoDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun readsBranchAndLooseRefs() {
        write(".git/HEAD", "ref: refs/heads/main\n")
        write(".git/refs/heads/main", "abc123\n")
        write(".git/refs/heads/feature/x", "def456\n")

        val info = GitRepoInfoReader(repoDir.absolutePath).read()

        assertEquals("main", info?.currentBranch)
        assertFalse(info?.isDetached == true)
        assertEquals("abc123", info?.headSha)
        assertEquals(
            listOf(
                GitBranch("feature/x", isCurrent = false, sha = "def456"),
                GitBranch("main", isCurrent = true, sha = "abc123"),
            ),
            info?.branches?.sortedBy { it.name },
        )
    }

    @Test
    fun readsDetachedHead() {
        write(".git/HEAD", "abc123def456\n")
        write(".git/refs/heads/main", "abc123def456\n")

        val info = GitRepoInfoReader(repoDir.absolutePath).read()

        val detached = requireNotNull(info)
        assertNull(detached.currentBranch)
        assertTrue(detached.isDetached)
        assertEquals("abc123def456", detached.headSha)
        val onlyBranch = detached.branches.single()
        assertEquals("main", onlyBranch.name)
        assertFalse(onlyBranch.isCurrent)
    }

    @Test
    fun looseRefsOverridePackedRefs() {
        write(".git/HEAD", "ref: refs/heads/main\n")
        write(
            ".git/packed-refs",
            "# pack-refs with: peeled fully-peeled sorted \n" +
                "packed111 refs/heads/main\n" +
                "packed222 refs/heads/old\n",
        )
        write(".git/refs/heads/main", "loose333\n")

        val info = GitRepoInfoReader(repoDir.absolutePath).read()

        assertEquals("loose333", info?.headSha)
        assertEquals(
            mapOf("main" to "loose333", "old" to "packed222"),
            info?.branches?.associate { it.name to it.sha },
        )
    }

    @Test
    fun returnsNullOutsideRepository() {
        val empty = Files.createTempDirectory("jetaprog-nogit").toFile()
        try {
            assertNull(GitRepoInfoReader(empty.absolutePath).read())
        } finally {
            empty.deleteRecursively()
        }
    }

    @Test
    fun followsGitdirFileForm() {
        val realGitDir = File(repoDir, "real-git-dir")
        realGitDir.mkdirs()
        File(realGitDir, "HEAD").writeText("ref: refs/heads/main\n")
        File(realGitDir, "refs/heads").mkdirs()
        File(realGitDir, "refs/heads/main").writeText("abc123\n")
        write(".git", "gitdir: ${realGitDir.absolutePath}\n")

        val info = GitRepoInfoReader(repoDir.absolutePath).read()

        assertEquals("main", info?.currentBranch)
        assertEquals("abc123", info?.headSha)
    }

    @Test
    fun readsCommonRefsForLinkedWorktree() {
        val worktree = File(repoDir, "linked").apply { mkdirs() }
        val gitDir = File(repoDir, "main/.git/worktrees/linked").apply { mkdirs() }
        val commonGitDir = File(repoDir, "main/.git")
        File(worktree, ".git").writeText("gitdir: ../main/.git/worktrees/linked\n")
        File(gitDir, "HEAD").writeText("ref: refs/heads/linked-branch\n")
        File(gitDir, "commondir").writeText("../..\n")
        File(commonGitDir, "refs/heads").mkdirs()
        File(commonGitDir, "refs/heads/linked-branch").writeText("abc123\n")

        val info = GitRepoInfoReader(worktree.absolutePath).read()

        assertEquals("linked-branch", info?.currentBranch)
        assertEquals("abc123", info?.headSha)
        assertEquals(listOf(GitBranch("linked-branch", isCurrent = true, sha = "abc123")), info?.branches)
    }
}
