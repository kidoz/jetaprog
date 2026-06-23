package su.kidoz.jetaprog.plugins.kotlin.analysis

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import su.kidoz.jetaprog.common.Disposable

/** The kind of a Kotlin declaration surfaced by the analyzer. */
public enum class KotlinSymbolKind {
    FUNCTION,
    CLASS,
    INTERFACE,
    OBJECT,
    PROPERTY,
    PARAMETER,
    OTHER,
}

/** A named declaration discovered in a source file. */
public data class KotlinDeclaration(
    /** The declaration's simple name. */
    val name: String,
    /** The declaration kind. */
    val kind: KotlinSymbolKind,
)

/** A syntax error reported by the Kotlin parser, with character offsets. */
public data class KotlinSyntaxError(
    /** The parser's error description. */
    val message: String,
    /** Inclusive start offset in the source text. */
    val startOffset: Int,
    /** Exclusive end offset in the source text. */
    val endOffset: Int,
)

/**
 * In-process Kotlin source analysis backed by the embedded Kotlin compiler's PSI.
 *
 * This is the parser-level foundation (Phase 1): it builds a [KtFile] from source
 * text and exposes syntax errors and in-file declarations without semantic
 * resolution. Classpath-aware, type-aware analysis is layered on top later via
 * the Kotlin Analysis API.
 *
 * The underlying IntelliJ core is not thread-safe, so all PSI access is
 * serialized.
 */
public class KotlinPsiAnalyzer : Disposable {
    private val parentDisposable = Disposer.newDisposable("KotlinPsiAnalyzer")
    private val lock = Any()

    // KotlinCoreEnvironment is the K1 PSI entry point; intentional until the K2
    // Analysis API is wired in (see module note in build.gradle.kts).
    @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
    private val environment: KotlinCoreEnvironment by lazy {
        val configuration =
            CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MODULE_NAME, "jetaprog-kotlin-analysis")
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
        KotlinCoreEnvironment.createForProduction(
            parentDisposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
    }

    private val psiFactory: KtPsiFactory by lazy { KtPsiFactory(environment.project, markGenerated = false) }

    /**
     * Parses [text] and returns any syntax errors found by the parser.
     */
    public fun syntaxErrors(text: String): List<KotlinSyntaxError> =
        synchronized(lock) {
            val file = parse(text)
            PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { error ->
                val range = error.textRange
                KotlinSyntaxError(
                    message = error.errorDescription,
                    startOffset = range.startOffset,
                    endOffset = range.endOffset.coerceAtLeast(range.startOffset + 1),
                )
            }
        }

    /**
     * Parses [text] and returns the named declarations it contains.
     */
    public fun declarations(text: String): List<KotlinDeclaration> =
        synchronized(lock) {
            val file = parse(text)
            PsiTreeUtil
                .collectElementsOfType(file, KtNamedDeclaration::class.java)
                .mapNotNull { declaration ->
                    val name = declaration.name ?: return@mapNotNull null
                    KotlinDeclaration(name, declaration.symbolKind())
                }.distinctBy { it.name to it.kind }
        }

    private fun parse(text: String): KtFile = psiFactory.createFile("analysis.kt", text)

    private fun KtNamedDeclaration.symbolKind(): KotlinSymbolKind =
        when (this) {
            is KtNamedFunction -> KotlinSymbolKind.FUNCTION
            is KtClass -> if (isInterface()) KotlinSymbolKind.INTERFACE else KotlinSymbolKind.CLASS
            is KtObjectDeclaration -> KotlinSymbolKind.OBJECT
            is KtParameter -> KotlinSymbolKind.PARAMETER
            is KtProperty -> KotlinSymbolKind.PROPERTY
            else -> KotlinSymbolKind.OTHER
        }

    override fun dispose() {
        Disposer.dispose(parentDisposable)
    }
}
