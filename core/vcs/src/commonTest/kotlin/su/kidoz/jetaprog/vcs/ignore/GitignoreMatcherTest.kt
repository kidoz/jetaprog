package su.kidoz.jetaprog.vcs.ignore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitignoreMatcherTest {
    private val root = "/repo"

    private fun matcher(files: Map<String, String>): GitignoreMatcher = GitignoreMatcher(root) { path -> files[path] }

    private fun matcherOf(vararg rules: Pair<String, String>): GitignoreMatcher = matcher(rules.toMap())

    private fun rootIgnore(content: String): Pair<String, String> = "/repo/.gitignore" to content

    @Test
    fun ignoresNothingWithoutRules() {
        val matcher = matcherOf()
        assertFalse(matcher.isIgnored("/repo/src/Main.kt", isDirectory = false))
    }

    @Test
    fun appliesRootRulesAtAnyDepth() {
        val matcher = matcherOf(rootIgnore("*.log\nbuild/\n"))
        assertTrue(matcher.isIgnored("/repo/debug.log", isDirectory = false))
        assertTrue(matcher.isIgnored("/repo/app/logs/debug.log", isDirectory = false))
        assertTrue(matcher.isIgnored("/repo/app/build", isDirectory = true))
        assertFalse(matcher.isIgnored("/repo/app/src", isDirectory = true))
    }

    @Test
    fun ignoresFilesInsideAnIgnoredDirectory() {
        val matcher = matcherOf(rootIgnore("build/\n"))
        assertTrue(matcher.isIgnored("/repo/build/classes/Main.class", isDirectory = false))
        assertTrue(matcher.isIgnored("/repo/app/build/tmp", isDirectory = true))
    }

    @Test
    fun honoursNegationWithinTheSameFile() {
        val matcher = matcherOf(rootIgnore("*.log\n!keep.log\n"))
        assertTrue(matcher.isIgnored("/repo/debug.log", isDirectory = false))
        assertFalse(matcher.isIgnored("/repo/keep.log", isDirectory = false))
    }

    @Test
    fun cannotReIncludeInsideAnIgnoredDirectory() {
        // Git never descends into an excluded directory, so the negation is inert.
        val matcher = matcherOf(rootIgnore("build/\n!build/keep.txt\n"))
        assertTrue(matcher.isIgnored("/repo/build/keep.txt", isDirectory = false))
    }

    @Test
    fun nestedFilesOverrideTheRoot() {
        val matcher =
            matcherOf(
                rootIgnore("*.log\n"),
                "/repo/app/.gitignore" to "!*.log\n",
            )
        assertTrue(matcher.isIgnored("/repo/debug.log", isDirectory = false))
        assertFalse(matcher.isIgnored("/repo/app/debug.log", isDirectory = false))
    }

    @Test
    fun nestedRulesAreRelativeToTheirOwnDirectory() {
        val matcher = matcherOf("/repo/app/.gitignore" to "/generated\n")
        assertTrue(matcher.isIgnored("/repo/app/generated", isDirectory = true))
        assertFalse(matcher.isIgnored("/repo/generated", isDirectory = true))
        assertFalse(matcher.isIgnored("/repo/app/nested/generated", isDirectory = true))
    }

    @Test
    fun readsRepositoryExcludesWithLowerPrecedenceThanGitignore() {
        val matcher =
            matcherOf(
                "/repo/.git/info/exclude" to "notes.md\n",
                rootIgnore("!/notes.md\n"),
            )
        assertFalse(matcher.isIgnored("/repo/notes.md", isDirectory = false))
        assertTrue(matcher.isIgnored("/repo/scratch/notes.md", isDirectory = false))
    }

    @Test
    fun alwaysIgnoresTheGitDirectory() {
        val matcher = matcherOf()
        assertTrue(matcher.isIgnored("/repo/.git", isDirectory = true))
        assertTrue(matcher.isIgnored("/repo/.git/config", isDirectory = false))
    }

    @Test
    fun leavesThePathsOutsideTheRootAlone() {
        val matcher = matcherOf(rootIgnore("*.log\n"))
        assertFalse(matcher.isIgnored("/elsewhere/debug.log", isDirectory = false))
        assertFalse(matcher.isIgnored(root, isDirectory = true))
    }

    @Test
    fun cachesRuleFilesUntilInvalidated() {
        var reads = 0
        val matcher =
            GitignoreMatcher(root) { path ->
                reads++
                if (path == "/repo/.gitignore") "*.log\n" else null
            }

        assertTrue(matcher.isIgnored("/repo/a.log", isDirectory = false))
        val firstPass = reads
        assertTrue(matcher.isIgnored("/repo/b.log", isDirectory = false))
        assertEquals(firstPass, reads, "cached rules should not be re-read")

        matcher.invalidate()
        assertTrue(matcher.isIgnored("/repo/c.log", isDirectory = false))
        assertTrue(reads > firstPass, "invalidate() should force a re-read")
    }

    @Test
    fun acceptsWindowsSeparators() {
        val matcher =
            GitignoreMatcher("""C:\repo""") { path ->
                if (path == "C:/repo/.gitignore") "build/\n" else null
            }
        assertTrue(matcher.isIgnored("""C:\repo\build""", isDirectory = true))
        assertFalse(matcher.isIgnored("""C:\repo\src""", isDirectory = true))
    }
}
