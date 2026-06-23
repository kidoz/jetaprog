package su.kidoz.jetaprog.plugins.kotlin.analysis

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinPsiAnalyzerTest {
    private val analyzer = KotlinPsiAnalyzer()

    @AfterTest
    fun tearDown() {
        analyzer.dispose()
    }

    @Test
    fun validSourceHasNoSyntaxErrors() {
        val source =
            """
            package demo

            class Greeter(val name: String) {
                fun greet(): String = "Hello, ${'$'}name"
            }
            """.trimIndent()

        assertEquals(emptyList(), analyzer.syntaxErrors(source))
    }

    @Test
    fun invalidSourceReportsSyntaxError() {
        val source =
            """
            fun broken( {
            """.trimIndent()

        val errors = analyzer.syntaxErrors(source)

        assertTrue(errors.isNotEmpty(), "expected at least one syntax error")
        assertTrue(errors.all { it.endOffset > it.startOffset })
    }

    @Test
    fun declarationsAreExtractedWithKinds() {
        val source =
            """
            package demo

            class Service
            interface Repo
            object Registry
            fun compute(value: Int): Int = value
            val constant = 42
            """.trimIndent()

        val declarations = analyzer.declarations(source)
        val byName = declarations.associate { it.name to it.kind }

        assertEquals(KotlinSymbolKind.CLASS, byName["Service"])
        assertEquals(KotlinSymbolKind.INTERFACE, byName["Repo"])
        assertEquals(KotlinSymbolKind.OBJECT, byName["Registry"])
        assertEquals(KotlinSymbolKind.FUNCTION, byName["compute"])
        assertEquals(KotlinSymbolKind.PROPERTY, byName["constant"])
        assertEquals(KotlinSymbolKind.PARAMETER, byName["value"])
    }
}
