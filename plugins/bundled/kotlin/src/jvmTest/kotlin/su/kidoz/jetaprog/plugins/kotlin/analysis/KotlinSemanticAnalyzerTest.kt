package su.kidoz.jetaprog.plugins.kotlin.analysis

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinSemanticAnalyzerTest {
    // Seed the analyzer with the kotlin-stdlib jar from the test classpath so that
    // basic types and functions resolve — exercising the classpath-aware path.
    private val stdlibPath: String =
        File(
            Unit::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).absolutePath

    private val analyzer = KotlinSemanticAnalyzer(classpath = listOf(stdlibPath))

    @AfterTest
    fun tearDown() {
        analyzer.dispose()
    }

    @Test
    fun resolvableCodeHasNoErrors() {
        val source =
            """
            package demo

            fun add(a: Int, b: Int): Int = a + b

            fun main() {
                val total = add(2, 3)
                println(total)
            }
            """.trimIndent()

        val errors = analyzer.diagnostics(source).filter { it.severity == KotlinDiagnosticSeverity.ERROR }

        assertTrue(errors.isEmpty(), "expected no errors but got: $errors")
    }

    @Test
    fun unresolvedReferenceIsReported() {
        val source =
            """
            fun broken() {
                println(doesNotExist)
            }
            """.trimIndent()

        val errors = analyzer.diagnostics(source).filter { it.severity == KotlinDiagnosticSeverity.ERROR }

        assertTrue(errors.isNotEmpty(), "expected an unresolved-reference error")
        assertTrue(errors.all { it.endOffset > it.startOffset })
    }
}
