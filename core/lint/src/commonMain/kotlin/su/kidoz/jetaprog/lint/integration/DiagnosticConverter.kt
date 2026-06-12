package su.kidoz.jetaprog.lint.integration

import su.kidoz.jetaprog.editor.state.DiagnosticSeverity
import su.kidoz.jetaprog.editor.syntax.Diagnostic
import su.kidoz.jetaprog.editor.syntax.DiagnosticLocation
import su.kidoz.jetaprog.editor.syntax.DiagnosticRelatedInformation
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintSeverity

/**
 * Converts between lint results and editor diagnostics.
 */
public object DiagnosticConverter {
    /**
     * Convert a lint result to an editor diagnostic.
     *
     * @param result The lint result to convert.
     * @param fileUri The URI of the file containing the result.
     * @return The converted diagnostic.
     */
    public fun toDiagnostic(
        result: LintResult,
        fileUri: String? = null,
    ): Diagnostic =
        Diagnostic(
            range = result.range,
            message = result.message,
            severity = toSeverity(result.severity),
            source = "lint:${result.ruleId.language}",
            code = result.ruleId.value,
            relatedInformation =
                result.relatedLocations.map { location ->
                    DiagnosticRelatedInformation(
                        location =
                            DiagnosticLocation(
                                uri = location.uri,
                                range = location.range,
                            ),
                        message = location.message,
                    )
                },
        )

    /**
     * Convert multiple lint results to diagnostics.
     *
     * @param results The lint results to convert.
     * @param fileUri The URI of the file containing the results.
     * @return The converted diagnostics.
     */
    public fun toDiagnostics(
        results: List<LintResult>,
        fileUri: String? = null,
    ): List<Diagnostic> = results.map { toDiagnostic(it, fileUri) }

    /**
     * Convert lint severity to diagnostic severity.
     */
    public fun toSeverity(severity: LintSeverity): DiagnosticSeverity =
        when (severity) {
            LintSeverity.ERROR -> DiagnosticSeverity.ERROR
            LintSeverity.WARNING -> DiagnosticSeverity.WARNING
            LintSeverity.INFO -> DiagnosticSeverity.INFORMATION
            LintSeverity.HINT -> DiagnosticSeverity.HINT
            LintSeverity.OFF -> DiagnosticSeverity.HINT // Shouldn't happen
        }

    /**
     * Convert diagnostic severity to lint severity.
     */
    public fun toLintSeverity(severity: DiagnosticSeverity): LintSeverity =
        when (severity) {
            DiagnosticSeverity.ERROR -> LintSeverity.ERROR
            DiagnosticSeverity.WARNING -> LintSeverity.WARNING
            DiagnosticSeverity.INFORMATION -> LintSeverity.INFO
            DiagnosticSeverity.HINT -> LintSeverity.HINT
        }
}
