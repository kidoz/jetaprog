package su.kidoz.jetaprog.build.gradle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleTaskPathTest {
    @Test
    fun acceptsTaskPaths() {
        assertTrue(GradleTaskPath.isValid("test"))
        assertTrue(GradleTaskPath.isValid("compileKotlin"))
        assertTrue(GradleTaskPath.isValid(":app:desktop:test"))
        assertTrue(GradleTaskPath.isValid("asgard-build"))
    }

    @Test
    fun rejectsOptionsAndBlankValues() {
        assertFalse(GradleTaskPath.isValid(""))
        assertFalse(GradleTaskPath.isValid("--init-script=evil.gradle"))
        assertFalse(GradleTaskPath.isValid("--init-script"))
        assertFalse(GradleTaskPath.isValid("--no-daemon"))
        assertFalse(GradleTaskPath.isValid("-q"))
        assertFalse(GradleTaskPath.isValid("clean --rerun"))
        assertFalse(GradleTaskPath.isValid(":"))
        assertFalse(GradleTaskPath.isValid("foo:"))
    }
}
