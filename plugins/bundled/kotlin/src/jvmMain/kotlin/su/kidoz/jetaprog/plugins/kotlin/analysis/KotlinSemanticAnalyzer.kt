package su.kidoz.jetaprog.plugins.kotlin.analysis

import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureJdkHomeFromSystemProperty
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.psi.KtFile
import su.kidoz.jetaprog.common.Disposable
import java.io.File
import java.nio.file.Files

/** Severity of a semantic diagnostic. */
public enum class KotlinDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

/** A semantic diagnostic produced by frontend analysis, with character offsets. */
public data class KotlinSemanticDiagnostic(
    /** The rendered diagnostic message. */
    val message: String,
    /** Inclusive start offset in the source text. */
    val startOffset: Int,
    /** Exclusive end offset in the source text. */
    val endOffset: Int,
    /** The diagnostic severity. */
    val severity: KotlinDiagnosticSeverity,
)

/**
 * Classpath-aware Kotlin analysis (Phase 2).
 *
 * Configures the embedded compiler with the project [classpath] (resolved from
 * the Gradle import) and the host JDK, then runs frontend analysis to surface
 * semantic diagnostics — unresolved references, type mismatches and the like —
 * which the parser-only [KotlinPsiAnalyzer] cannot detect.
 *
 * Source is analyzed from a disk-backed source root so that function bodies are
 * fully resolved. A fresh compiler environment is built per analysis and torn
 * down afterwards; access is serialized. This favors correctness over latency —
 * caching and incremental reuse are a later refinement.
 */
public class KotlinSemanticAnalyzer(
    private val classpath: List<String> = emptyList(),
) : Disposable {
    private val lock = Any()

    /**
     * Analyzes [text] and returns semantic diagnostics for it.
     */
    @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
    @Suppress("DEPRECATION_ERROR")
    public fun diagnostics(text: String): List<KotlinSemanticDiagnostic> =
        synchronized(lock) {
            val disposable = Disposer.newDisposable("KotlinSemanticAnalysis")
            val tempDir = Files.createTempDirectory("jetaprog-semantics")
            val sourceFile = tempDir.resolve("semantic.kt").toFile()
            try {
                sourceFile.writeText(text)

                val configuration =
                    CompilerConfiguration().apply {
                        put(CommonConfigurationKeys.MODULE_NAME, "jetaprog-kotlin-semantics")
                        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                        configureJdkHomeFromSystemProperty()
                        addJvmClasspathRoots(classpath.map(::File).filter { it.exists() })
                        addKotlinSourceRoot(sourceFile.absolutePath)
                    }

                val environment =
                    KotlinCoreEnvironment.createForProduction(
                        disposable,
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES,
                    )

                val files = environment.getSourceFiles()
                val file = files.firstOrNull() ?: return@synchronized emptyList()

                val bindingContext =
                    TopDownAnalyzerFacadeForJVM
                        .analyzeFilesWithJavaIntegration(
                            environment.project,
                            files,
                            NoScopeRecordCliBindingTrace(environment.project),
                            environment.configuration,
                            environment::createPackagePartProvider,
                        ).bindingContext

                bindingContext.diagnostics
                    .all()
                    .filter { it.psiElement.containingFile == file }
                    .mapNotNull { diagnostic -> diagnostic.toSemanticDiagnostic(file.textLength) }
            } finally {
                Disposer.dispose(disposable)
                sourceFile.delete()
                Files.deleteIfExists(tempDir)
            }
        }

    private fun Diagnostic.toSemanticDiagnostic(textLength: Int): KotlinSemanticDiagnostic? {
        val mappedSeverity =
            when (severity) {
                Severity.ERROR -> KotlinDiagnosticSeverity.ERROR
                Severity.WARNING -> KotlinDiagnosticSeverity.WARNING
                Severity.INFO -> KotlinDiagnosticSeverity.INFO
                else -> return null
            }
        val range = textRanges.firstOrNull() ?: psiElement.textRange
        val start = range.startOffset.coerceIn(0, textLength)
        return KotlinSemanticDiagnostic(
            message = DefaultErrorMessages.render(this),
            startOffset = start,
            endOffset = range.endOffset.coerceIn(start + 1, maxOf(start + 1, textLength)),
            severity = mappedSeverity,
        )
    }

    override fun dispose() {
        // Environments are created and disposed per analysis; nothing to release here.
    }
}
