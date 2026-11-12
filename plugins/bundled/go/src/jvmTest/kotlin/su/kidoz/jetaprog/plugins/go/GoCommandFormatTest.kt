package su.kidoz.jetaprog.plugins.go

import su.kidoz.jetaprog.build.go.GoOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class GoCommandFormatTest {
    @Test
    fun formatsDiagnosticsAndTestEvents() {
        assertEquals(
            "[error] main.go:8:1: undefined: answer",
            formatGoOutput(GoOutput.Diagnostic("main.go", 8, 1, "undefined: answer", "error")),
        )
        assertEquals(
            "pass TestAdd (0.02s)",
            formatGoOutput(GoOutput.TestEvent("pass", "example.com/acme", "TestAdd", null, 0.02)),
        )
        assertEquals(
            "SUCCESS (exit code 0)",
            formatGoOutput(GoOutput.CommandCompleted(exitCode = 0, success = true)),
        )
    }
}
