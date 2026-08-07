package su.kidoz.jetaprog.app.ui.panels

import su.kidoz.jetaprog.build.gradle.GradleDiagnostic
import su.kidoz.jetaprog.build.gradle.GradleDiagnosticSeverity
import su.kidoz.jetaprog.build.gradle.GradleTask
import su.kidoz.jetaprog.build.gradle.state.OutputLine
import su.kidoz.jetaprog.build.gradle.state.OutputType
import su.kidoz.jetaprog.common.text.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildOutputPanelTest {
    @Test
    fun stylesWarningsFromEitherOutputStreamAsWarnings() {
        assertEquals(
            BuildOutputTone.WARNING,
            OutputLine("WARNING: restricted method used", OutputType.STDERR).buildOutputTone(),
        )
        assertEquals(
            BuildOutputTone.WARNING,
            OutputLine("w: deprecated declaration", OutputType.STDOUT).buildOutputTone(),
        )
    }

    @Test
    fun keepsOrdinaryStandardErrorNeutral() {
        assertEquals(
            BuildOutputTone.NORMAL,
            OutputLine("Native tool wrote this to stderr", OutputType.STDERR).buildOutputTone(),
        )
    }

    @Test
    fun stylesFailureMarkersAsErrors() {
        assertEquals(
            BuildOutputTone.ERROR,
            OutputLine("FAILURE: Build failed", OutputType.STDERR).buildOutputTone(),
        )
        assertEquals(
            BuildOutputTone.ERROR,
            OutputLine("> Task :app:compile FAILED", OutputType.STDOUT).buildOutputTone(),
        )
    }

    @Test
    fun summarizesDiagnosticsBySeverity() {
        val diagnostics =
            listOf(
                diagnostic(GradleDiagnosticSeverity.ERROR),
                diagnostic(GradleDiagnosticSeverity.ERROR),
                diagnostic(GradleDiagnosticSeverity.WARNING),
                diagnostic(GradleDiagnosticSeverity.INFO),
            )

        assertEquals("2 errors · 1 warning · 1 info", diagnosticSummary(diagnostics))
    }

    @Test
    fun prioritizesPinnedAndCommonTasksInThePicker() {
        val tasks =
            listOf(
                GradleTask(":app:agent:assemble", "assemble", "build"),
                GradleTask(":app:desktop:run", "run", "application"),
                GradleTask(":check", "check", "verification"),
                GradleTask(":build", "build", "build"),
            )

        val ordered = filterGradleTasksForPicker(tasks, listOf(":check"), "")

        assertEquals(listOf(":check", ":app:desktop:run", ":build", ":app:agent:assemble"), ordered.map { it.path })
    }

    @Test
    fun filtersTasksByDescription() {
        val tasks =
            listOf(
                GradleTask(":package", "package", "distribution", "Build a native installer"),
                GradleTask(":test", "test", "verification", "Run tests"),
            )

        assertEquals(
            listOf(":package"),
            filterGradleTasksForPicker(tasks, emptyList(), "installer").map { it.path },
        )
    }

    private fun diagnostic(severity: GradleDiagnosticSeverity): GradleDiagnostic =
        GradleDiagnostic(
            severity = severity,
            filePath = "src/Main.kt",
            position = TextPosition(0, 0),
            message = "message",
        )
}
