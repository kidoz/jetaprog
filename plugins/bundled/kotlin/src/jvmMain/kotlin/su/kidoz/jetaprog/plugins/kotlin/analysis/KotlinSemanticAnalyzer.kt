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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
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
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import su.kidoz.jetaprog.common.Disposable
import java.io.File
import java.nio.file.Files

/** Severity of a semantic diagnostic. */
public enum class KotlinDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

/** A resolved definition target within the analyzed source, by character offsets. */
public data class KotlinDefinitionLocation(
    /** Inclusive start offset of the target declaration's name. */
    val startOffset: Int,
    /** Exclusive end offset of the target declaration's name. */
    val endOffset: Int,
)

/** A resolved definition target that may live in a context file on disk. */
public data class KotlinCrossFileDefinition(
    /** Path of the file containing the declaration, or null for the analyzed text itself. */
    val filePath: String?,
    /** Inclusive start offset of the declaration's name within its file. */
    val startOffset: Int,
    /** Exclusive end offset of the declaration's name within its file. */
    val endOffset: Int,
)

/** A resolved reference to a symbol, by file and character offsets. */
public data class KotlinReference(
    /** Path of the file containing the reference, or null for the analyzed text itself. */
    val filePath: String?,
    /** Inclusive start offset of the reference within its file. */
    val startOffset: Int,
    /** Exclusive end offset of the reference within its file. */
    val endOffset: Int,
    /** True when this entry is the declaration itself rather than a use of it. */
    val isDeclaration: Boolean = false,
)

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
    private val classpathProvider: (String?) -> List<String> = { emptyList() },
) : Disposable {
    private val lock = Any()
    private var session: Session? = null

    private val diagnosticsCache = lruCache<String, List<KotlinSemanticDiagnostic>>(CACHE_SIZE)
    private val completionCache = lruCache<String, List<KotlinDeclaration>>(CACHE_SIZE)

    /**
     * Whether a classpath is available. Semantic analysis without a classpath
     * produces false "unresolved" errors, so callers should gate on this.
     */
    public fun isReady(filePath: String? = null): Boolean = classpathProvider(filePath).isNotEmpty()

    /**
     * Analyzes [text] and returns its semantic diagnostics.
     */
    public fun diagnostics(
        text: String,
        filePath: String? = null,
    ): List<KotlinSemanticDiagnostic> =
        synchronized(lock) {
            diagnosticsCache.getOrPut(cacheKey(text, filePath)) {
                withAnalysis(text, filePath = filePath) { file, _, bindingContext ->
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
        filePath: String? = null,
    ): List<KotlinDeclaration> =
        synchronized(lock) {
            completionCache.getOrPut("$offset@${cacheKey(text, filePath)}") {
                withAnalysis(text, filePath = filePath) { file, _, bindingContext ->
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

    /**
     * Resolves the reference at [offset] to its declaration and returns the
     * declaration's name range when the target has source in the analyzed file.
     * Returns null for references into compiled/library code (no source).
     */
    public fun definition(
        text: String,
        offset: Int,
        filePath: String? = null,
    ): KotlinDefinitionLocation? =
        definitionInContext(text, offset, filePath = filePath)
            ?.takeIf { it.filePath == null }
            ?.let { KotlinDefinitionLocation(it.startOffset, it.endOffset) }

    /**
     * Resolves the reference at [offset] to its declaration, analyzing [text]
     * together with [contextFiles] (paths of on-disk sources that may contain
     * the target — typically nominated by the symbol index). Resolution picks
     * the correct declaration among same-named candidates by actual binding.
     *
     * Context files are read from disk; unsaved edits in them are not seen.
     * Returns null for references into compiled/library code (no source).
     */
    public fun definitionInContext(
        text: String,
        offset: Int,
        contextFiles: List<String> = emptyList(),
        filePath: String? = null,
    ): KotlinCrossFileDefinition? =
        synchronized(lock) {
            withAnalysis(text, contextFiles, filePath) { file, _, bindingContext ->
                val reference =
                    PsiTreeUtil.getParentOfType(
                        file.findElementAt(offset.coerceIn(0, maxOf(0, file.textLength - 1))),
                        KtReferenceExpression::class.java,
                        false,
                    ) ?: return@withAnalysis null
                val target = bindingContext.get(BindingContext.REFERENCE_TARGET, reference) ?: return@withAnalysis null
                val declaration = DescriptorToSourceUtils.descriptorToDeclaration(target) ?: return@withAnalysis null
                val range = (declaration as? KtNamedDeclaration)?.nameIdentifier?.textRange ?: declaration.textRange
                if (declaration.containingFile == file) {
                    KotlinCrossFileDefinition(null, range.startOffset, range.endOffset)
                } else {
                    val path = declaration.containingFile?.virtualFile?.path ?: return@withAnalysis null
                    KotlinCrossFileDefinition(path, range.startOffset, range.endOffset)
                }
            }
        }

    /**
     * Finds references to the symbol at [offset], resolved by binding rather
     * than by name.
     *
     * [text] and [contextFiles] are analyzed together in a single pass, so the
     * cost is one analysis over the supplied files regardless of how many
     * references are found. Callers should nominate context files that plausibly
     * mention the symbol (for example via a project-wide text search) and cap
     * that list — files outside it are not searched.
     *
     * The cursor may sit on either the declaration or any reference to it. The
     * result includes the declaration itself (marked with
     * [KotlinReference.isDeclaration]) when its source is among the analyzed
     * files. Occurrences in comments and string literals are excluded, as are
     * unrelated symbols that merely share the name.
     */
    public fun references(
        text: String,
        offset: Int,
        contextFiles: List<String> = emptyList(),
        filePath: String? = null,
    ): List<KotlinReference> =
        synchronized(lock) {
            withAnalysis(text, contextFiles, filePath) { file, context, bindingContext ->
                val target = targetDeclarationAt(file, bindingContext, offset) ?: return@withAnalysis emptyList()
                val results = mutableListOf<KotlinReference>()

                val targetRange =
                    (target as? KtNamedDeclaration)?.nameIdentifier?.textRange ?: target.textRange
                results +=
                    KotlinReference(
                        filePath = target.containingFile.pathRelativeTo(file),
                        startOffset = targetRange.startOffset,
                        endOffset = targetRange.endOffset,
                        isDeclaration = true,
                    )

                for (analyzed in listOf(file) + context) {
                    PsiTreeUtil
                        .findChildrenOfType(analyzed, KtNameReferenceExpression::class.java)
                        .forEach { reference ->
                            if (!reference.resolvesTo(target, bindingContext)) return@forEach
                            val range = reference.textRange
                            results +=
                                KotlinReference(
                                    filePath = analyzed.pathRelativeTo(file),
                                    startOffset = range.startOffset,
                                    endOffset = range.endOffset,
                                )
                        }
                }
                results
            } ?: emptyList()
        }

    /**
     * Resolves what the cursor at [offset] designates: the declaration under the
     * caret when it sits on a declaration name, otherwise the declaration that
     * the reference under the caret binds to.
     */
    private fun targetDeclarationAt(
        file: KtFile,
        bindingContext: BindingContext,
        offset: Int,
    ): PsiElement? {
        val anchor = offset.coerceIn(0, maxOf(0, file.textLength - 1))
        val element = file.findElementAt(anchor) ?: return null

        val declaration = PsiTreeUtil.getParentOfType(element, KtNamedDeclaration::class.java, false)
        if (declaration?.nameIdentifier?.textRange?.containsOffset(offset) == true) return declaration

        val reference =
            PsiTreeUtil.getParentOfType(element, KtReferenceExpression::class.java, false) ?: return null
        val descriptor = bindingContext.get(BindingContext.REFERENCE_TARGET, reference) ?: return null
        return DescriptorToSourceUtils.descriptorToDeclaration(descriptor)
    }

    private fun KtNameReferenceExpression.resolvesTo(
        target: PsiElement,
        bindingContext: BindingContext,
    ): Boolean {
        val descriptor = bindingContext.get(BindingContext.REFERENCE_TARGET, this) ?: return false
        val declaration = DescriptorToSourceUtils.descriptorToDeclaration(descriptor) ?: return false
        // A constructor call resolves to the constructor; treat it as a use of
        // the class that declares it.
        return declaration == target || declaration.parent == target
    }

    /**
     * Path of this file, or null when it is [main] — the throwaway file backing
     * the analyzed text, whose temp path is meaningless to callers.
     */
    private fun PsiFile?.pathRelativeTo(main: KtFile): String? =
        when {
            this == null || this == main -> null
            else -> virtualFile?.path
        }

    @OptIn(org.jetbrains.kotlin.K1Deprecation::class)
    @Suppress("DEPRECATION_ERROR")
    private fun <R> withAnalysis(
        text: String,
        contextFiles: List<String> = emptyList(),
        filePath: String? = null,
        block: (KtFile, List<KtFile>, BindingContext) -> R,
    ): R? {
        val active = session(classpathProvider(filePath))
        val file = active.reparse(text) ?: return null
        val context = contextFiles.mapNotNull { active.loadFile(it) }.filter { it != file }
        val bindingContext =
            TopDownAnalyzerFacadeForJVM
                .analyzeFilesWithJavaIntegration(
                    active.environment.project,
                    listOf(file) + context,
                    NoScopeRecordCliBindingTrace(active.environment.project),
                    active.environment.configuration,
                    active.environment::createPackagePartProvider,
                ).bindingContext
        return block(file, context, bindingContext)
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

    private fun cacheKey(
        text: String,
        filePath: String?,
    ): String = "${classpathProvider(filePath).hashCode()}:${text.hashCode()}:${text.length}"

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

        /** Loads an on-disk source file into the session for context analysis. */
        fun loadFile(path: String): KtFile? {
            val virtualFile = environment.findLocalFile(path) ?: return null
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
