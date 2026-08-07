package su.kidoz.jetaprog.plugins.kotlin.lint

import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintFix
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
 * Reports imports that are never used in the file.
 *
 * Detection is shared with the editor's quick fix, so the warning and the
 * "Remove unused import" action always agree.
 */
public class KotlinUnusedImportRule : AbstractLintRule(DESCRIPTOR) {
    override suspend fun check(context: LintContext): List<LintResult> =
        KotlinUnusedImports.find(context.content).map { unused ->
            createResult(
                message = "Unused import: ${unused.fqName}",
                range = context.rangeFromOffsets(unused.startOffset, unused.endOffset),
                severity = LintSeverity.WARNING,
            )
        }

    override suspend fun fix(
        context: LintContext,
        result: LintResult,
    ): LintFix? {
        val unused =
            KotlinUnusedImports.find(context.content).firstOrNull { candidate ->
                "Unused import: ${candidate.fqName}" == result.message
            } ?: return null
        return LintFix.Delete(
            description = "Remove import ${unused.fqName}",
            uri = context.uri,
            range = context.rangeFromOffsets(unused.startOffset, unused.endOffset),
        )
    }

    private companion object {
        private val DESCRIPTOR =
            LintRuleDescriptor(
                id = LintRuleId.of("kotlin", "unused-import"),
                name = "Unused import",
                description = "Reports imports whose name never appears in the file.",
                category = LintCategory.STYLE,
                defaultSeverity = LintSeverity.WARNING,
                languages = listOf("kotlin"),
                hasFix = true,
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
        registerRule(KotlinUnusedImportRule())
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
        val filePath = context.uri.removePrefix("file://")
        if (!analyzer.isReady(filePath)) return emptyList()
        return analyzer.diagnostics(context.content, filePath).map { diagnostic ->
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
