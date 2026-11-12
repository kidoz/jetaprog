package su.kidoz.jetaprog.plugins.go.lint

import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GolangciOutputParserTest {
    @Test
    fun parsesIssuesFromV1AndV2Reports() {
        val report =
            """
            level=info starting
            {"Issues":[
              {"FromLinter":"govet","Text":"unreachable code","Severity":"error","Pos":{"Filename":"main.go","Line":12,"Column":2}},
              {"FromLinter":"gosec","Text":"weak random","Severity":"warning","Pos":{"Filename":"main.go","Line":4,"Column":5}}
            ]}
            """.trimIndent()

        val results = GolangciOutputParser.parse(report)

        assertEquals(2, results.size)
        assertEquals("go/govet", results[0].ruleId.value)
        assertEquals(11, results[0].range.start.line)
        assertEquals(1, results[0].range.start.column)
        assertEquals(LintSeverity.ERROR, results[0].severity)
        assertEquals(LintCategory.SECURITY, results[1].category)
    }

    @Test
    fun ignoresTextThatIsNotAReport() {
        assertTrue(GolangciOutputParser.parse("golangci-lint is not installed").isEmpty())
        assertTrue(GolangciOutputParser.parse("").isEmpty())
    }
}
