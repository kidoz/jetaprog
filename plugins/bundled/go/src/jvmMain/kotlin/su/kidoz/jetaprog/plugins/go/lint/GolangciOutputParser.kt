package su.kidoz.jetaprog.plugins.go.lint

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.lint.model.LintCategory
import su.kidoz.jetaprog.lint.model.LintResult
import su.kidoz.jetaprog.lint.model.LintRuleId
import su.kidoz.jetaprog.lint.model.LintSeverity

/**
 * Parses golangci-lint JSON from either the v1 `--out-format json` report or the v2
 * `--output.json.path stdout` report.
 */
public object GolangciOutputParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Converts [output] into lint results. Blank or non-JSON text yields an empty list.
     */
    public fun parse(output: String): List<LintResult> {
        val payload = extractJson(output) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val issues = issueArray(root) ?: return emptyList()
        return issues.mapNotNull { element ->
            runCatching { toResult(element.jsonObject) }.getOrNull()
        }
    }

    private fun issueArray(root: JsonObject) =
        (root["Issues"] ?: root["issues"] ?: root["Diagnostics"] ?: root["diagnostics"])?.jsonArray

    private fun toResult(issue: JsonObject): LintResult? {
        val message = issue.string("Text", "text") ?: return null
        val linter = issue.string("FromLinter", "fromLinter", "linter") ?: "golangci"
        val position = (issue["Pos"] ?: issue["pos"])?.jsonObject ?: return null
        val line = ((position.int("Line", "line") ?: return null) - 1).coerceAtLeast(0)
        val column = ((position.int("Column", "column") ?: 1) - 1).coerceAtLeast(0)
        return LintResult(
            ruleId = LintRuleId.of("go", linter),
            message = "$message ($linter)",
            range = TextRange(TextPosition(line, column), TextPosition(line, column + 1)),
            severity = severity(issue.string("Severity", "severity")),
            category = category(linter),
            data = mapOf("linter" to linter),
        )
    }

    private fun severity(value: String?): LintSeverity =
        when (value?.lowercase()) {
            "error" -> LintSeverity.ERROR
            "info" -> LintSeverity.INFO
            else -> LintSeverity.WARNING
        }

    private fun category(linter: String): LintCategory =
        when (linter) {
            "gosec" -> LintCategory.SECURITY
            "gofmt", "goimports", "revive", "stylecheck", "lll", "whitespace" -> LintCategory.STYLE
            "prealloc" -> LintCategory.PERFORMANCE
            else -> LintCategory.CORRECTNESS
        }

    private fun extractJson(output: String): String? {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return output.substring(start, end + 1)
    }
}

private fun JsonObject.string(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> this[name]?.jsonPrimitive?.contentOrNull }

private fun JsonObject.int(vararg names: String): Int? =
    names.firstNotNullOfOrNull { name -> this[name]?.jsonPrimitive?.intOrNull }
