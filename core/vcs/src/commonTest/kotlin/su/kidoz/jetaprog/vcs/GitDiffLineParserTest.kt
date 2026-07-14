package su.kidoz.jetaprog.vcs

import kotlin.test.Test
import kotlin.test.assertEquals

class GitDiffLineParserTest {
    @Test
    fun parsesAddedLines() {
        val diff =
            """
            diff --git a/file.kt b/file.kt
            @@ -5,0 +6,2 @@ fun main() {
            +val a = 1
            +val b = 2
            """.trimIndent()

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(
            listOf(
                GitLineChange(line = 5, type = GitLineChangeType.ADDED),
                GitLineChange(line = 6, type = GitLineChangeType.ADDED),
            ),
            changes,
        )
    }

    @Test
    fun parsesModifiedLines() {
        val diff =
            """
            @@ -3,2 +3,2 @@
            -old line
            -old line 2
            +new line
            +new line 2
            """.trimIndent()

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(
            listOf(
                GitLineChange(line = 2, type = GitLineChangeType.MODIFIED),
                GitLineChange(line = 3, type = GitLineChangeType.MODIFIED),
            ),
            changes,
        )
    }

    @Test
    fun parsesDeletionAfterLine() {
        val diff =
            """
            @@ -10,3 +9,0 @@
            -gone
            -gone
            -gone
            """.trimIndent()

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(listOf(GitLineChange(line = 8, type = GitLineChangeType.DELETED)), changes)
    }

    @Test
    fun parsesDeletionAtTopOfFile() {
        val diff = "@@ -1,2 +0,0 @@"

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(listOf(GitLineChange(line = 0, type = GitLineChangeType.DELETED)), changes)
    }

    @Test
    fun defaultsOmittedCountsToOne() {
        val diff = "@@ -4 +4 @@"

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(listOf(GitLineChange(line = 3, type = GitLineChangeType.MODIFIED)), changes)
    }

    @Test
    fun parsesMultipleHunks() {
        val diff =
            """
            @@ -1,0 +1,1 @@
            +added
            @@ -8 +9 @@
            -x
            +y
            """.trimIndent()

        val changes = GitDiffLineParser.parse(diff)

        assertEquals(
            listOf(
                GitLineChange(line = 0, type = GitLineChangeType.ADDED),
                GitLineChange(line = 8, type = GitLineChangeType.MODIFIED),
            ),
            changes,
        )
    }

    @Test
    fun ignoresNonHunkLines() {
        val diff =
            """
            diff --git a/file.kt b/file.kt
            index 1234567..89abcde 100644
            --- a/file.kt
            +++ b/file.kt
            """.trimIndent()

        assertEquals(emptyList(), GitDiffLineParser.parse(diff))
    }
}
