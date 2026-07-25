package su.kidoz.jetaprog.app.ui.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandPaletteHostTest {
    @Test
    fun treatsBuildFailureMarkersAsFailures() {
        assertTrue(looksLikeFailure("Compiling app\nCommand FAILED (exit code: 1)"))
        assertTrue(looksLikeFailure("[error] src/main.cpp:3:1: expected ';'"))
        assertTrue(looksLikeFailure("Build FAILED (exit code: 2)"))
        assertTrue(looksLikeFailure("Command failed: cmake not found"))
    }

    @Test
    fun treatsSuccessfulOutputAsSuccess() {
        assertFalse(looksLikeFailure("[100%] Linking CXX executable app\nCommand SUCCESS (exit code: 0)"))
    }

    @Test
    fun summaryPrefersErrorLinesOverTrailingNoise() {
        val output =
            """
            Compiling app
            [error] src/main.cpp:3:1: expected ';'
            [100%] Built target app
            Command FAILED (exit code: 1)
            """.trimIndent()

        assertEquals("[error] src/main.cpp:3:1: expected ';'", summarize(output))
    }

    @Test
    fun summaryFallsBackToTheLastLines() {
        val output = (1..20).joinToString("\n") { "line $it" }
        val summary = summarize(output)

        assertEquals(5, summary?.lines()?.size)
        assertTrue(summary!!.endsWith("line 20"))
    }

    @Test
    fun summaryIsNullForEmptyOutput() {
        assertNull(summarize(""))
        assertNull(summarize("   \n  \n"))
    }
}
