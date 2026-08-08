package su.kidoz.jetaprog.plugins.api.language

import su.kidoz.jetaprog.editor.document.LanguageId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobPatternTest {
    @Test
    fun starMatchesWithinSegment() {
        assertTrue(GlobPattern("*.kt").matches("Main.kt"))
        assertFalse(GlobPattern("*.kt").matches("src/Main.kt"))
        assertFalse(GlobPattern("*.kt").matches("Main.kts"))
    }

    @Test
    fun doubleStarMatchesAcrossSegments() {
        assertTrue(GlobPattern("**/*.kt").matches("src/main/kotlin/Main.kt"))
        assertTrue(GlobPattern("**/*.kt").matches("Main.kt"))
        assertTrue(GlobPattern("src/**").matches("src/a/b/c.txt"))
    }

    @Test
    fun questionMarkMatchesSingleCharacter() {
        assertTrue(GlobPattern("?.go").matches("a.go"))
        assertFalse(GlobPattern("?.go").matches("ab.go"))
    }

    @Test
    fun alternationMatchesAnyBranch() {
        val pattern = GlobPattern("*.{yaml,yml}")
        assertTrue(pattern.matches("config.yaml"))
        assertTrue(pattern.matches("config.yml"))
        assertFalse(pattern.matches("config.toml"))
    }

    @Test
    fun literalDotsAreNotWildcards() {
        assertFalse(GlobPattern("a.b").matches("axb"))
    }

    @Test
    fun selectorMatchesFileNameForBarePatterns() {
        val selector = DocumentSelector.forPattern("*.go")
        assertTrue(selector.matches(LanguageId.GO, "file:///work/project/main.go"))
        assertFalse(selector.matches(LanguageId.GO, "file:///work/project/main.rs"))
    }

    @Test
    fun selectorMatchesFullPathForNestedPatterns() {
        val selector = DocumentSelector.forPattern("**/test/**/*.kt")
        assertTrue(selector.matches(LanguageId.KOTLIN, "file:///repo/module/test/foo/BarTest.kt"))
        assertFalse(selector.matches(LanguageId.KOTLIN, "file:///repo/module/main/foo/Bar.kt"))
    }

    @Test
    fun selectorStillFiltersByLanguageAndScheme() {
        val selector = DocumentSelector(languages = listOf(LanguageId.KOTLIN), scheme = "file")
        assertTrue(selector.matches(LanguageId.KOTLIN, "file:///a/B.kt"))
        assertFalse(selector.matches(LanguageId.JAVA, "file:///a/B.java"))
        assertFalse(selector.matches(LanguageId.KOTLIN, "untitled:1"))
    }
}
