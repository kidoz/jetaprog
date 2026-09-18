package su.kidoz.jetaprog.plugins.kotlin.analysis

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The classpath holds jars only, so a file analyzed alone reported every
 * reference to a sibling source file as unresolved.
 */
class KotlinSemanticDiagnosticsContextTest {
    private val stdlibPath: String =
        File(
            Unit::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).absolutePath

    private val analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })
    private val root = Files.createTempDirectory("jetaprog-diag-context").toFile()

    @AfterTest
    fun tearDown() {
        analyzer.dispose()
        root.deleteRecursively()
    }

    @Test
    fun referencesIntoContextFilesResolve() {
        val other = File(root, "Other.kt").apply { writeText("package demo\n\nfun helper(): Int = 1\n") }
        val source = "package demo\n\nfun use(): Int = helper()\n"

        val alone = analyzer.diagnostics(source).filter { it.severity == KotlinDiagnosticSeverity.ERROR }
        val withContext =
            analyzer
                .diagnostics(source, contextFiles = listOf(other.absolutePath))
                .filter { it.severity == KotlinDiagnosticSeverity.ERROR }

        assertEquals(listOf("UNRESOLVED_REFERENCE"), alone.map { it.factoryName })
        assertTrue(withContext.isEmpty(), "expected no errors with context but got: $withContext")
    }
}
