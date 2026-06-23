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
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
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
 * Configures the embedded compiler with the project classpath (resolved from the
 * Gradle import via [classpathProvider]) and the host JDK, then runs frontend
 * analysis to surface semantic diagnostics and member completions that the
 * parser-only [KotlinPsiAnalyzer] cannot produce.
 *
 * Source is analyzed from a disk-backed source root so function bodies are fully
 * resolved. Results are immutable and cached by content + classpath, so repeated
 * requests (e.g. lint passes without edits) are served without re-analysis.
 * Access is serialized; the compiler environment is built and disposed per
 * analysis.
 */
public class KotlinSemanticAnalyzer(
    private val classpathProvider: () -> List<String> = { emptyList() },
) : Disposable {
    private val lock = Any()

    private val diagnosticsCache = lruCache<String, List<KotlinSemanticDiagnostic>>(CACHE_SIZE)
    private val completionCache = lruCache<String, List<KotlinDeclaration>>(CACHE_SIZE)

    /**
     * Whether a classpath is available. Semantic analysis without a classpath
     * produces false "unresolved" errors, so callers should gate on this.
     */
    public fun isReady(): Boolean = classpathProvider().isNotEmpty()

    /**
     * Analyzes [text] and returns its semantic diagnostics.
     */
    public fun diagnostics(text: String): List<KotlinSemanticDiagnostic> =
        synchronized(lock) {
            val key = cacheKey(text)
            diagnosticsCache.getOrPut(key) {
                withAnalysis(text) { file, bindingContext ->
                    bindingContext.diagnostics
                        .all()
                        .filter { it.psiElement.containingFile == file }
                        .mapNotNull { diagnostic -> diagnostic.toSemanticDiagnostic(file.textLength) }
                } ?: emptyList()
            }
        }

    /**
     * Returns member completions for the receiver expression preceding the `.`
     * at [offset], resolved against the classpath (for example members of
     * `String` for `"x".`).
     */
    public fun memberCompletions(
        text: String,
        offset: Int,
    ): List<KotlinDeclaration> =
        synchronized(lock) {
            val key = "$offset@${cacheKey(text)}"
            completionCache.getOrPut(key) {
                withAnalysis(text) { file, bindingContext ->
                    val receiver = findReceiver(file, offset) ?: return@withAnalysis emptyList()
                    val type =
                        bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, receiver)?.type
                            ?: return@withAnalysis emptyList()
                    type.memberScope
                        .getContributedDescriptors()
                        .mapNotNull { it.toDeclaration() }
                        .distinctBy { it.name to it.kind }
                } ?: emptyList()
            }
        }

    @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
    @Suppress("DEPRECATION_ERROR")
    private fun <R> withAnalysis(
        text: String,
        block: (KtFile, BindingContext) -> R,
    ): R? {
        val disposable = Disposer.newDisposable("KotlinSemanticAnalysis")
        val tempDir = Files.createTempDirectory("jetaprog-semantics")
        val sourceFile = tempDir.resolve("semantic.kt").toFile()
        return try {
            sourceFile.writeText(text)
            val configuration =
                CompilerConfiguration().apply {
                    put(CommonConfigurationKeys.MODULE_NAME, "jetaprog-kotlin-semantics")
                    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                    configureJdkHomeFromSystemProperty()
                    addJvmClasspathRoots(classpathProvider().map(::File).filter { it.exists() })
                    addKotlinSourceRoot(sourceFile.absolutePath)
                }
            val environment =
                KotlinCoreEnvironment.createForProduction(
                    disposable,
                    configuration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                )
            val files = environment.getSourceFiles()
            val file = files.firstOrNull() ?: return null
            val bindingContext =
                TopDownAnalyzerFacadeForJVM
                    .analyzeFilesWithJavaIntegration(
                        environment.project,
                        files,
                        NoScopeRecordCliBindingTrace(environment.project),
                        environment.configuration,
                        environment::createPackagePartProvider,
                    ).bindingContext
            block(file, bindingContext)
        } finally {
            Disposer.dispose(disposable)
            sourceFile.delete()
            Files.deleteIfExists(tempDir)
        }
    }

    private fun findReceiver(
        file: KtFile,
        offset: Int,
    ): KtExpression? {
        val anchor = (offset - 1).coerceIn(0, maxOf(0, file.textLength - 1))
        val element = file.findElementAt(anchor) ?: return null
        val qualified = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java) ?: return null
        return qualified.receiverExpression
    }

    private fun DeclarationDescriptor.toDeclaration(): KotlinDeclaration? {
        val simpleName = name.takeUnless { it.isSpecial }?.asString() ?: return null
        val kind =
            when (this) {
                is FunctionDescriptor -> KotlinSymbolKind.FUNCTION
                is PropertyDescriptor -> KotlinSymbolKind.PROPERTY
                is ClassDescriptor -> KotlinSymbolKind.CLASS
                else -> return null
            }
        return KotlinDeclaration(simpleName, kind)
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

    private fun cacheKey(text: String): String = "${classpathProvider().hashCode()}:${text.hashCode()}:${text.length}"

    override fun dispose() {
        synchronized(lock) {
            diagnosticsCache.clear()
            completionCache.clear()
        }
    }

    private companion object {
        private const val CACHE_SIZE = 32

        private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> =
            object : LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxSize
            }

        private const val LOAD_FACTOR = 0.75f
    }
}
