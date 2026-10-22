package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitDiffFocusTest {
    @Test
    fun relativePathStripsTheProjectRoot() {
        assertEquals("src/Reader.kt", repositoryRelativePath("/proj", "/proj/src/Reader.kt"))
        assertEquals("src/Reader.kt", repositoryRelativePath("/proj/", "/proj/src/Reader.kt"))
        assertEquals("other/A.kt", repositoryRelativePath("/proj", "/other/A.kt"))
    }

    @Test
    fun unstagedChangeWinsWhenTheFileIsAlsoStaged() {
        val staged = GitChange("src/Reader.kt", GitChangeType.MODIFIED, staged = true)
        val unstaged = GitChange("src/Reader.kt", GitChangeType.MODIFIED, staged = false)
        assertEquals(unstaged, matchingGitChange(listOf(staged, unstaged), "src/Reader.kt"))
        assertEquals(staged, matchingGitChange(listOf(staged), "src/Reader.kt"))
        assertNull(matchingGitChange(listOf(unstaged), "src/Other.kt"))
    }
}
