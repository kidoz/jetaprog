package su.kidoz.jetaprog.plugins.kotlin.analysis

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinSemanticAnalyzerTest {
    // Seed the analyzer with the kotlin-stdlib jar from the test classpath so that
    // basic types and members resolve — exercising the classpath-aware path.
    private val stdlibPath: String =
        File(
            Unit::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).absolutePath

    private val analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })

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

    @Test
    fun reusedEnvironmentProducesIndependentResults() {
        // Analyses share one reused compiler environment; each must reflect only
        // its own content with no state leaking between calls.
        val cleanFirst =
            analyzer
                .diagnostics(
                    "fun f(): Int = 1",
                ).filter { it.severity == KotlinDiagnosticSeverity.ERROR }
        val broken = analyzer.diagnostics("fun g() { nope() }").filter { it.severity == KotlinDiagnosticSeverity.ERROR }
        val cleanAgain =
            analyzer
                .diagnostics(
                    "fun h(): Int = 2",
                ).filter { it.severity == KotlinDiagnosticSeverity.ERROR }

        assertTrue(cleanFirst.isEmpty(), "first clean analysis should have no errors: $cleanFirst")
        assertTrue(broken.isNotEmpty(), "broken analysis should report an error")
        assertTrue(cleanAgain.isEmpty(), "later clean analysis should have no errors: $cleanAgain")
    }

    @Test
    fun memberCompletionResolvesClasspathTypeMembers() {
        val source = "val length = \"hello\".length"
        val offset = source.length // cursor at the end of `.length`

        val members = analyzer.memberCompletions(source, offset).map { it.name }

        assertTrue("length" in members, "expected String members from the classpath, got $members")
    }
}
