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
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
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
 * The expensive compiler environment (classpath/JDK indexing) is built once per
 * classpath and reused; each analysis only rewrites and reparses the backing
 * source file. The environment is rebuilt when the classpath changes. Immutable
 * results are cached by content, so repeated requests skip analysis entirely.
 * Access is serialized.
 */
public class KotlinSemanticAnalyzer(
    private val classpathProvider: () -> List<String> = { emptyList() },
) : Disposable {
    private val lock = Any()
    private var session: Session? = null

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
            diagnosticsCache.getOrPut(cacheKey(text)) {
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
            completionCache.getOrPut("$offset@${cacheKey(text)}") {
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

    @OptIn(org.jetbrains.kotlin.K1Deprecation::class)
    @Suppress("DEPRECATION_ERROR")
    private fun <R> withAnalysis(
        text: String,
        block: (KtFile, BindingContext) -> R,
    ): R? {
        val active = session(classpathProvider())
        val file = active.reparse(text) ?: return null
        val bindingContext =
            TopDownAnalyzerFacadeForJVM
                .analyzeFilesWithJavaIntegration(
                    active.environment.project,
                    listOf(file),
                    NoScopeRecordCliBindingTrace(active.environment.project),
                    active.environment.configuration,
                    active.environment::createPackagePartProvider,
                ).bindingContext
        return block(file, bindingContext)
    }

    private fun session(classpath: List<String>): Session {
        val key = classpath.hashCode()
        val existing = session
        if (existing != null && existing.classpathKey == key) return existing
        existing?.close()
        return Session.create(classpath, key).also { session = it }
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
            session?.close()
            session = null
            diagnosticsCache.clear()
            completionCache.clear()
        }
    }

    /**
     * A reusable compiler environment bound to a specific classpath. The
     * expensive classpath/JDK indexing is done once; each analysis writes a
     * freshly named file into the session's source-root directory (avoiding any
     * stale virtual-file content cache) and parses it through the shared project.
     */
    private class Session(
        private val disposable: org.jetbrains.kotlin.com.intellij.openapi.Disposable,
        val environment: KotlinCoreEnvironment,
        private val sourceDir: File,
        val classpathKey: Int,
    ) {
        private var counter = 0
        private var previousFile: File? = null

        fun reparse(text: String): KtFile? {
            val file = File(sourceDir, "semantic_${counter++}.kt")
            file.writeText(text)
            previousFile?.delete()
            previousFile = file
            val virtualFile = environment.findLocalFile(file.absolutePath) ?: return null
            return PsiManager.getInstance(environment.project).findFile(virtualFile) as? KtFile
        }

        fun close() {
            Disposer.dispose(disposable)
            sourceDir.deleteRecursively()
        }

        companion object {
            @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
            fun create(
                classpath: List<String>,
                classpathKey: Int,
            ): Session {
                val disposable = Disposer.newDisposable("KotlinSemanticSession")
                val sourceDir = Files.createTempDirectory("jetaprog-semantics").toFile()
                val configuration =
                    CompilerConfiguration().apply {
                        put(CommonConfigurationKeys.MODULE_NAME, "jetaprog-kotlin-semantics")
                        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                        configureJdkHomeFromSystemProperty()
                        addJvmClasspathRoots(classpath.map(::File).filter { it.exists() })
                        addKotlinSourceRoot(sourceDir.absolutePath)
                    }
                val environment =
                    KotlinCoreEnvironment.createForProduction(
                        disposable,
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES,
                    )
                return Session(disposable, environment, sourceDir, classpathKey)
            }
        }
    }

    private companion object {
        private const val CACHE_SIZE = 32
        private const val LOAD_FACTOR = 0.75f

        private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> =
            object : LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxSize
            }
    }
}
