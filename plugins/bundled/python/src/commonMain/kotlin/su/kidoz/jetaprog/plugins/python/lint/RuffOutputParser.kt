package su.kidoz.jetaprog.plugins.python.lint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity

/**
 * Parser for ruff check --output-format json output.
 */
public object RuffOutputParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Parse ruff JSON output into LintResults.
     */
    public fun parse(
        jsonOutput: String,
        uri: String,
    ): List<LintResult> {
        if (jsonOutput.isBlank()) return emptyList()

        return try {
            val diagnostics = json.decodeFromString<List<RuffDiagnostic>>(jsonOutput)
            diagnostics.map { it.toLintResult(uri) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Map ruff rule code to LintCategory.
     */
    public fun mapCategory(code: String): LintCategory =
        when {
            code.startsWith("F") -> LintCategory.CORRECTNESS

            // Pyflakes
            code.startsWith("E") -> LintCategory.STYLE

            // pycodestyle errors
            code.startsWith("W") -> LintCategory.STYLE

            // pycodestyle warnings
            code.startsWith("C") -> LintCategory.COMPLEXITY

            // mccabe
            code.startsWith("I") -> LintCategory.STYLE

            // isort
            code.startsWith("N") -> LintCategory.STYLE

            // pep8-naming
            code.startsWith("D") -> LintCategory.STYLE

            // pydocstyle
            code.startsWith("UP") -> LintCategory.MODERNIZE

            // pyupgrade
            code.startsWith("YTT") -> LintCategory.CORRECTNESS

            // flake8-2020
            code.startsWith("ANN") -> LintCategory.STYLE

            // flake8-annotations
            code.startsWith("ASYNC") -> LintCategory.CORRECTNESS

            // flake8-async
            code.startsWith("S") -> LintCategory.SECURITY

            // bandit/flake8-bandit
            code.startsWith("BLE") -> LintCategory.CORRECTNESS

            // flake8-blind-except
            code.startsWith("FBT") -> LintCategory.STYLE

            // flake8-boolean-trap
            code.startsWith("B") -> LintCategory.CORRECTNESS

            // flake8-bugbear
            code.startsWith("A") -> LintCategory.STYLE

            // flake8-builtins
            code.startsWith("COM") -> LintCategory.STYLE

            // flake8-commas
            code.startsWith("CPY") -> LintCategory.STYLE

            // flake8-copyright
            code.startsWith("DTZ") -> LintCategory.CORRECTNESS

            // flake8-datetimez
            code.startsWith("T10") -> LintCategory.STYLE

            // flake8-debugger
            code.startsWith("DJ") -> LintCategory.CORRECTNESS

            // flake8-django
            code.startsWith("EM") -> LintCategory.STYLE

            // flake8-errmsg
            code.startsWith("EXE") -> LintCategory.CORRECTNESS

            // flake8-executable
            code.startsWith("FA") -> LintCategory.MODERNIZE

            // flake8-future-annotations
            code.startsWith("ISC") -> LintCategory.STYLE

            // flake8-implicit-str-concat
            code.startsWith("ICN") -> LintCategory.STYLE

            // flake8-import-conventions
            code.startsWith("LOG") -> LintCategory.CORRECTNESS

            // flake8-logging
            code.startsWith("G") -> LintCategory.STYLE

            // flake8-logging-format
            code.startsWith("INP") -> LintCategory.CORRECTNESS

            // flake8-no-pep420
            code.startsWith("PIE") -> LintCategory.STYLE

            // flake8-pie
            code.startsWith("T20") -> LintCategory.STYLE

            // flake8-print
            code.startsWith("PYI") -> LintCategory.STYLE

            // flake8-pyi
            code.startsWith("PT") -> LintCategory.STYLE

            // flake8-pytest-style
            code.startsWith("Q") -> LintCategory.STYLE

            // flake8-quotes
            code.startsWith("RSE") -> LintCategory.STYLE

            // flake8-raise
            code.startsWith("RET") -> LintCategory.STYLE

            // flake8-return
            code.startsWith("SLF") -> LintCategory.STYLE

            // flake8-self
            code.startsWith("SLOT") -> LintCategory.PERFORMANCE

            // flake8-slots
            code.startsWith("SIM") -> LintCategory.STYLE

            // flake8-simplify
            code.startsWith("TID") -> LintCategory.STYLE

            // flake8-tidy-imports
            code.startsWith("TCH") -> LintCategory.PERFORMANCE

            // flake8-type-checking
            code.startsWith("INT") -> LintCategory.CORRECTNESS

            // flake8-gettext
            code.startsWith("ARG") -> LintCategory.STYLE

            // flake8-unused-arguments
            code.startsWith("PTH") -> LintCategory.MODERNIZE

            // flake8-use-pathlib
            code.startsWith("TD") -> LintCategory.STYLE

            // flake8-todos
            code.startsWith("FIX") -> LintCategory.STYLE

            // flake8-fixme
            code.startsWith("ERA") -> LintCategory.STYLE

            // eradicate
            code.startsWith("PD") -> LintCategory.STYLE

            // pandas-vet
            code.startsWith("PGH") -> LintCategory.STYLE

            // pygrep-hooks
            code.startsWith("PL") -> LintCategory.STYLE

            // Pylint
            code.startsWith("TRY") -> LintCategory.CORRECTNESS

            // tryceratops
            code.startsWith("FLY") -> LintCategory.PERFORMANCE

            // flynt
            code.startsWith("NPY") -> LintCategory.CORRECTNESS

            // NumPy-specific
            code.startsWith("FAST") -> LintCategory.CORRECTNESS

            // FastAPI
            code.startsWith("AIR") -> LintCategory.CORRECTNESS

            // Airflow
            code.startsWith("PERF") -> LintCategory.PERFORMANCE

            // Perflint
            code.startsWith("FURB") -> LintCategory.MODERNIZE

            // refurb
            code.startsWith("DOC") -> LintCategory.STYLE

            // pydoclint
            code.startsWith("RUF") -> LintCategory.STYLE

            // Ruff-specific
            else -> LintCategory.STYLE
        }

    /**
     * Map ruff rule code to LintSeverity.
     */
    public fun mapSeverity(code: String): LintSeverity =
        when {
            // Errors
            code.startsWith("F") -> LintSeverity.ERROR

            // Pyflakes (often undefined names)
            code.startsWith("E9") -> LintSeverity.ERROR

            // Syntax errors
            // Security issues
            code.startsWith("S") -> LintSeverity.WARNING

            // Security
            // Style/warnings
            code.startsWith("E") -> LintSeverity.WARNING

            // pycodestyle
            code.startsWith("W") -> LintSeverity.WARNING

            // pycodestyle warnings
            code.startsWith("B") -> LintSeverity.WARNING

            // Bugbear
            // Info/hints
            code.startsWith("D") -> LintSeverity.INFO

            // pydocstyle
            code.startsWith("UP") -> LintSeverity.HINT

            // pyupgrade
            code.startsWith("SIM") -> LintSeverity.HINT

            // simplify
            else -> LintSeverity.WARNING
        }
}

/**
 * Ruff diagnostic from JSON output.
 */
@Serializable
public data class RuffDiagnostic(
    val code: String? = null,
    val message: String,
    val filename: String,
    val location: RuffLocation,
    @SerialName("end_location")
    val endLocation: RuffLocation,
    val fix: RuffFix? = null,
    val url: String? = null,
    val noqa_row: Int? = null,
) {
    /**
     * Convert to LintResult.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun toLintResult(uri: String): LintResult {
        val ruleCode = code ?: "unknown"
        return LintResult(
            ruleId = LintRuleId.of("ruff", ruleCode),
            message = message,
            range =
                TextRange(
                    start = TextPosition(location.row - 1, location.column - 1),
                    end = TextPosition(endLocation.row - 1, endLocation.column - 1),
                ),
            severity = RuffOutputParser.mapSeverity(ruleCode),
            category = RuffOutputParser.mapCategory(ruleCode),
            data =
                buildMap {
                    put("code", ruleCode)
                    url?.let { put("url", it) }
                    if (fix != null) put("hasFix", "true")
                },
        )
    }
}

/**
 * Location in ruff output.
 */
@Serializable
public data class RuffLocation(
    val row: Int,
    val column: Int,
)

/**
 * Fix information from ruff.
 */
@Serializable
public data class RuffFix(
    val message: String? = null,
    val edits: List<RuffEdit>? = null,
    val applicability: String? = null,
)

/**
 * Edit from ruff fix.
 */
@Serializable
public data class RuffEdit(
    val content: String,
    val location: RuffLocation,
    @SerialName("end_location")
    val endLocation: RuffLocation,
)
