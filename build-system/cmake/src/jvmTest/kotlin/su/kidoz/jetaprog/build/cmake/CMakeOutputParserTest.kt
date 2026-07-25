package su.kidoz.jetaprog.build.cmake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CMakeOutputParserTest {
    @Test
    fun parsesCompileSteps() {
        val output =
            CMakeOutputParser.parseStdout("[ 50%] Building CXX object CMakeFiles/app.dir/main.cpp.o")
        val compiling = assertIs<CMakeOutput.Compiling>(output)
        assertEquals("CMakeFiles/app.dir/main.cpp.o", compiling.target)
        assertEquals("CXX", compiling.language)
    }

    @Test
    fun parsesLinkSteps() {
        val output = CMakeOutputParser.parseStdout("[100%] Linking CXX executable app")
        val linking = assertIs<CMakeOutput.Linking>(output)
        assertEquals("app", linking.target)
        assertEquals("CXX", linking.language)
    }

    @Test
    fun parsesGenericProgressLines() {
        val output = CMakeOutputParser.parseStdout("[ 25%] Built target support")
        val progress = assertIs<CMakeOutput.BuildProgress>(output)
        assertEquals(25, progress.percent)
    }

    @Test
    fun parsesConfigureProgress() {
        val output = CMakeOutputParser.parseStdout("-- The CXX compiler identification is Clang 20.1.0")
        val progress = assertIs<CMakeOutput.ConfigureProgress>(output)
        assertEquals("The CXX compiler identification is Clang 20.1.0", progress.message)
    }

    @Test
    fun parsesCompilerErrorsOnStderr() {
        val output = CMakeOutputParser.parseStderr("src/main.cpp:12:5: error: expected ';' after expression")
        val diagnostic = assertIs<CMakeOutput.Diagnostic>(output)
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals("src/main.cpp", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals(5, diagnostic.column)
        assertEquals("expected ';' after expression", diagnostic.message)
    }

    @Test
    fun parsesFatalErrorsAsErrors() {
        val output = CMakeOutputParser.parseStderr("src/a.c:1:10: fatal error: 'missing.h' file not found")
        assertEquals(DiagnosticSeverity.ERROR, assertIs<CMakeOutput.Diagnostic>(output).severity)
    }

    @Test
    fun parsesWarningsAndNotes() {
        assertEquals(
            DiagnosticSeverity.WARNING,
            assertIs<CMakeOutput.Diagnostic>(
                CMakeOutputParser.parseStderr("src/a.c:3:1: warning: unused variable 'x'"),
            ).severity,
        )
        assertEquals(
            DiagnosticSeverity.NOTE,
            assertIs<CMakeOutput.Diagnostic>(
                CMakeOutputParser.parseStderr("src/a.c:3:1: note: declared here"),
            ).severity,
        )
    }

    @Test
    fun parsesPassingCtestResults() {
        val output = CMakeOutputParser.parseStdout("1/3 Test #1: unit_tests ....................   Passed    0.01 sec")
        val test = assertIs<CMakeOutput.TestCompleted>(output)
        assertEquals("unit_tests", test.testName)
        assertEquals(CTestOutcome.PASSED, test.outcome)
        assertEquals(0.01, test.durationSeconds)
    }

    @Test
    fun parsesFailingCtestResultsDespiteAsteriskPrefix() {
        val output = CMakeOutputParser.parseStdout("2/3 Test #2: broken .......................***Failed    0.02 sec")
        val test = assertIs<CMakeOutput.TestCompleted>(output)
        assertEquals("broken", test.testName)
        assertEquals(CTestOutcome.FAILED, test.outcome)
    }

    @Test
    fun parsesCtestTimeouts() {
        val output = CMakeOutputParser.parseStdout("3/3 Test #3: slow .........................***Timeout   5.00 sec")
        assertEquals(CTestOutcome.TIMEOUT, assertIs<CMakeOutput.TestCompleted>(output).outcome)
    }

    @Test
    fun parsesCtestStartLines() {
        val output = CMakeOutputParser.parseStdout("    Start 3: slow")
        assertEquals("slow", assertIs<CMakeOutput.TestStarted>(output).testName)
    }

    @Test
    fun parsesCtestSummary() {
        val output = CMakeOutputParser.parseStdout("67% tests passed, 1 tests failed out of 3")
        val summary = assertIs<CMakeOutput.TestSummary>(output)
        assertEquals(2, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(3, summary.total)
    }

    @Test
    fun parsesNotRunTestsRatherThanMisreportingThem() {
        // Captured from ctest 4.4 when the test binary has not been built yet.
        val output =
            CMakeOutputParser.parseStdout(
                "1/2 Test #1: unit_tests .......................***Not Run   0.00 sec",
            )
        assertEquals(CTestOutcome.NOT_RUN, assertIs<CMakeOutput.TestCompleted>(output).outcome)
    }

    @Test
    fun parsesBuiltTargetLines() {
        val output = CMakeOutputParser.parseStdout("[ 50%] Built target app")
        val progress = assertIs<CMakeOutput.BuildProgress>(output)
        assertEquals(50, progress.percent)
        assertEquals("Built target app", progress.message)
    }

    @Test
    fun parsesCLanguageLinkSteps() {
        val output = CMakeOutputParser.parseStdout("[100%] Linking C executable capp")
        val linking = assertIs<CMakeOutput.Linking>(output)
        assertEquals("capp", linking.target)
        assertEquals("C", linking.language)
    }

    @Test
    fun fallsBackToRawOutput() {
        val output = CMakeOutputParser.parseStdout("some unstructured line")
        assertEquals("some unstructured line", assertIs<CMakeOutput.Stdout>(output).line)
    }
}
