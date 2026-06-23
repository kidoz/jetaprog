package su.kidoz.jetaprog.vcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitStatusParserTest {
    @Test
    fun parsesBranchAheadBehind() {
        val status = GitStatusParser.parse("## main...origin/main [ahead 2, behind 1]\n")

        assertEquals("main", status.branch)
        assertEquals(2, status.ahead)
        assertEquals(1, status.behind)
        assertTrue(status.isClean)
    }

    @Test
    fun parsesStagedUnstagedAndUntracked() {
        val output =
            buildString {
                appendLine("## main")
                appendLine("M  staged.kt")
                appendLine(" M worktree.kt")
                appendLine("MM both.kt")
                appendLine("A  added.kt")
                appendLine("?? new.kt")
            }

        val status = GitStatusParser.parse(output)

        // staged: staged.kt (M), both.kt (M index), added.kt (A)
        assertEquals(listOf("staged.kt", "both.kt", "added.kt"), status.staged.map { it.path })
        // unstaged: worktree.kt (M), both.kt (M worktree), new.kt (untracked)
        assertEquals(listOf("worktree.kt", "both.kt", "new.kt"), status.unstaged.map { it.path })
        assertEquals(GitChangeType.UNTRACKED, status.unstaged.first { it.path == "new.kt" }.type)
        assertEquals(GitChangeType.ADDED, status.staged.first { it.path == "added.kt" }.type)
    }

    @Test
    fun parsesRenameToNewPath() {
        val status = GitStatusParser.parse("## main\nR  old.kt -> new.kt\n")

        val change = status.staged.single()
        assertEquals("new.kt", change.path)
        assertEquals(GitChangeType.RENAMED, change.type)
    }

    @Test
    fun handlesNoCommitsYet() {
        val status = GitStatusParser.parse("## No commits yet on main\n?? file.kt\n")

        assertEquals("main", status.branch)
        assertEquals(listOf("file.kt"), status.unstaged.map { it.path })
    }
}
