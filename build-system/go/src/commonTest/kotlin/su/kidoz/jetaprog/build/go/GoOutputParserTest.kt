package su.kidoz.jetaprog.build.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GoOutputParserTest {
    @Test
    fun parsesCompilerDiagnosticsWithAndWithoutAColumn() {
        val withColumn = assertIs<GoOutput.Diagnostic>(GoOutputParser.parse("./main.go:10:2: undefined: answer"))
        assertEquals("./main.go", withColumn.file)
        assertEquals(10, withColumn.line)
        assertEquals(2, withColumn.column)
        assertEquals("error", withColumn.severity)

        val warning = assertIs<GoOutput.Diagnostic>(GoOutputParser.parse("main.go:4: warning: unused"))
        assertNull(warning.column)
        assertEquals("warning", warning.severity)
    }

    @Test
    fun parsesGoTestJsonEvents() {
        val event =
            assertIs<GoOutput.TestEvent>(
                GoOutputParser.parse(
                    """{"Action":"pass","Package":"example.com/acme","Test":"TestAdd","Elapsed":0.01}""",
                ),
            )

        assertEquals("pass", event.action)
        assertEquals("example.com/acme", event.packageName)
        assertEquals("TestAdd", event.test)
        assertEquals(0.01, event.elapsedSeconds)
    }

    @Test
    fun keepsOrdinaryStderrAsStderr() {
        val line =
            assertIs<GoOutput.Stderr>(GoOutputParser.parse("go: downloading example.com/dep v1.2.3", fromStderr = true))
        assertEquals("go: downloading example.com/dep v1.2.3", line.line)
    }
}
