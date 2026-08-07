package su.kidoz.jetaprog.vcs.ignore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitignorePatternTest {
    private fun pattern(line: String): GitignorePattern {
        val parsed = GitignorePattern.parse(line)
        assertNotNull(parsed, "expected '$line' to parse")
        return parsed
    }

    private fun matches(
        line: String,
        path: String,
        isDirectory: Boolean = false,
    ): Boolean = pattern(line).matches(path, isDirectory)

    @Test
    fun skipsBlankLinesAndComments() {
        assertNull(GitignorePattern.parse(""))
        assertNull(GitignorePattern.parse("   "))
        assertNull(GitignorePattern.parse("# build output"))
    }

    @Test
    fun matchesBareNameAtAnyDepth() {
        assertTrue(matches("build", "build"))
        assertTrue(matches("build", "app/desktop/build"))
        assertFalse(matches("build", "build-system"))
    }

    @Test
    fun anchorsPatternsContainingASeparator() {
        assertTrue(matches("app/build", "app/build"))
        assertFalse(matches("app/build", "core/app/build"))
        assertTrue(matches("/out", "out"))
        assertFalse(matches("/out", "app/out"))
    }

    @Test
    fun matchesEverythingBelowAMatchedPath() {
        assertTrue(matches("build", "build/classes/Main.class"))
        assertTrue(matches("/out", "out/nested/file.txt"))
    }

    @Test
    fun restrictsDirectoryOnlyPatternsToDirectories() {
        assertTrue(matches("logs/", "logs", isDirectory = true))
        assertFalse(matches("logs/", "logs", isDirectory = false))
        // A file inside the ignored directory is still ignored.
        assertTrue(matches("logs/", "logs/today.log"))
    }

    @Test
    fun starDoesNotCrossDirectoryBoundaries() {
        assertTrue(matches("*.log", "debug.log"))
        assertTrue(matches("*.log", "logs/debug.log"))
        assertTrue(matches("/logs/*.log", "logs/debug.log"))
        assertFalse(matches("/logs/*.log", "logs/nested/debug.log"))
        assertFalse(matches("doc/*.txt", "doc/nested/a.txt"))
    }

    @Test
    fun doubleStarCrossesDirectoryBoundaries() {
        assertTrue(matches("**/foo", "foo"))
        assertTrue(matches("**/foo", "a/b/foo"))
        assertTrue(matches("a/**/b", "a/b"))
        assertTrue(matches("a/**/b", "a/x/y/b"))
        assertTrue(matches("doc/**", "doc/nested/a.txt"))
        assertFalse(matches("doc/**", "other/a.txt"))
    }

    @Test
    fun supportsSingleCharacterAndClassWildcards() {
        assertTrue(matches("?.txt", "a.txt"))
        assertFalse(matches("?.txt", "ab.txt"))
        assertTrue(matches("[abc].txt", "b.txt"))
        assertFalse(matches("[abc].txt", "d.txt"))
        assertTrue(matches("[!abc].txt", "d.txt"))
        assertFalse(matches("[!abc].txt", "a.txt"))
        assertTrue(matches("file[0-9].txt", "file7.txt"))
    }

    @Test
    fun treatsDotsAsLiterals() {
        assertTrue(matches("a.txt", "a.txt"))
        assertFalse(matches("a.txt", "axtxt"))
    }

    @Test
    fun readsLeadingBangAsNegation() {
        val negated = pattern("!keep.txt")
        assertTrue(negated.negated)
        assertTrue(negated.matches("keep.txt", isDirectory = false))

        val literal = pattern("""\!keep.txt""")
        assertFalse(literal.negated)
        assertTrue(literal.matches("!keep.txt", isDirectory = false))
    }

    @Test
    fun readsEscapedHashAsALiteralPattern() {
        val escaped = pattern("""\#notes""")
        assertTrue(escaped.matches("#notes", isDirectory = false))
    }

    @Test
    fun stripsUnescapedTrailingWhitespace() {
        assertEquals("a.txt", pattern("a.txt   ").source)
        assertTrue(matches("a.txt   ", "a.txt"))
        assertTrue(matches("""a\ """, "a "))
    }

    @Test
    fun rejectsUnsupportedGlobSyntax() {
        assertNull(GitignorePattern.parse("[[:alpha:]].txt"))
        assertNull(GitignorePattern.parse("[unterminated"))
        assertNull(GitignorePattern.parse("/"))
    }
}
