package su.kidoz.jetaprog.plugins.kotlin.lint

import su.kidoz.jetaprog.lint.engine.LintContext
import su.kidoz.jetaprog.lint.model.AbstractLintRule
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleDescriptor
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.AbstractLintProvider
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinPsiAnalyzer

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
