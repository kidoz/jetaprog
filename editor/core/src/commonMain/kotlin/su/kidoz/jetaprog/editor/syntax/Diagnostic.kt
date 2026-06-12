package su.kidoz.jetaprog.editor.syntax

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.state.DiagnosticSeverity

/**
 * Represents a diagnostic (error, warning, etc.) in the code.
 */
@Serializable
public data class Diagnostic(
    /**
     * The range where the diagnostic applies.
     */
    val range: TextRange,
    /**
     * The diagnostic message.
     */
    val message: String,
    /**
     * The severity of the diagnostic.
     */
    val severity: DiagnosticSeverity,
    /**
     * The source of the diagnostic (e.g., "kotlin", "detekt").
     */
    val source: String? = null,
    /**
     * A diagnostic code for lookup.
     */
    val code: String? = null,
    /**
     * Related information for this diagnostic.
     */
    val relatedInformation: List<DiagnosticRelatedInformation> = emptyList(),
)

/**
 * Related information for a diagnostic.
 */
@Serializable
public data class DiagnosticRelatedInformation(
    /**
     * The location of the related information.
     */
    val location: DiagnosticLocation,
    /**
     * The message for this related information.
     */
    val message: String,
)

/**
 * A location referenced by a diagnostic.
 */
@Serializable
public data class DiagnosticLocation(
    /**
     * The file path or URI.
     */
    val uri: String,
    /**
     * The range in the file.
     */
    val range: TextRange,
)
