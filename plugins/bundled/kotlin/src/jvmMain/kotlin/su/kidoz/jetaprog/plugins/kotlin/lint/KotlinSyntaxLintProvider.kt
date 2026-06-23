package su.kidoz.jetaprog.plugins.kotlin.lint

import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleDescriptor
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.AbstractLintProvider
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinDiagnosticSeverity
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinPsiAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer

/**
 * Reports Kotlin parser syntax errors as lint diagnostics.
 *
 * Backed by the embedded compiler PSI ([KotlinPsiAnalyzer]); this is the
 * content-driven diagnostics path that surfaces parse errors in the editor.
 */
public class KotlinSyntaxRule(
    private val analyzer: KotlinPsiAnalyzer,
) : AbstractLintRule(DESCRIPTOR) {
    override suspend fun check(context: LintContext): List<LintResult> =
        analyzer.syntaxErrors(context.content).map { error ->
            createResult(
                message = error.message,
                range = context.rangeFromOffsets(error.startOffset, error.endOffset),
                severity = LintSeverity.ERROR,
            )
        }

    private companion object {
        private val DESCRIPTOR =
            LintRuleDescriptor(
                id = LintRuleId.of("kotlin", "syntax-error"),
                name = "Syntax error",
                description = "Reports Kotlin syntax errors detected by the compiler parser.",
                category = LintCategory.CORRECTNESS,
                defaultSeverity = LintSeverity.ERROR,
                languages = listOf("kotlin"),
            )
    }
}

/**
 * Lint provider contributing Kotlin syntax-error diagnostics.
 */
public class KotlinSyntaxLintProvider(
    analyzer: KotlinPsiAnalyzer,
) : AbstractLintProvider(
        id = "kotlin-syntax",
        name = "Kotlin Syntax",
        languages = listOf("kotlin"),
    ) {
    init {
        registerRule(KotlinSyntaxRule(analyzer))
    }
}

/**
 * Reports Kotlin semantic diagnostics (unresolved references, type errors) from
 * classpath-aware frontend analysis.
 *
 * No diagnostics are produced until the classpath is available, to avoid false
 * "unresolved" reports during project import.
 */
public class KotlinSemanticRule(
    private val analyzer: KotlinSemanticAnalyzer,
) : AbstractLintRule(DESCRIPTOR) {
    override suspend fun check(context: LintContext): List<LintResult> {
        if (!analyzer.isReady()) return emptyList()
        return analyzer.diagnostics(context.content).map { diagnostic ->
            createResult(
                message = diagnostic.message,
                range = context.rangeFromOffsets(diagnostic.startOffset, diagnostic.endOffset),
                severity =
                    when (diagnostic.severity) {
                        KotlinDiagnosticSeverity.ERROR -> LintSeverity.ERROR
                        KotlinDiagnosticSeverity.WARNING -> LintSeverity.WARNING
                        KotlinDiagnosticSeverity.INFO -> LintSeverity.INFO
                    },
            )
        }
    }

    private companion object {
        private val DESCRIPTOR =
            LintRuleDescriptor(
                id = LintRuleId.of("kotlin", "semantic"),
                name = "Semantic error",
                description = "Reports Kotlin semantic errors (unresolved references, type mismatches).",
                category = LintCategory.CORRECTNESS,
                defaultSeverity = LintSeverity.ERROR,
                languages = listOf("kotlin"),
            )
    }
}

/**
 * Lint provider contributing classpath-aware Kotlin semantic diagnostics.
 */
public class KotlinSemanticLintProvider(
    analyzer: KotlinSemanticAnalyzer,
) : AbstractLintProvider(
        id = "kotlin-semantic",
        name = "Kotlin Semantics",
        languages = listOf("kotlin"),
    ) {
    init {
        registerRule(KotlinSemanticRule(analyzer))
    }
}
