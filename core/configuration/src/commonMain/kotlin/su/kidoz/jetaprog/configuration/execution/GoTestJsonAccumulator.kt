package su.kidoz.jetaprog.configuration.execution

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Aggregated outcomes from the line-oriented output of `go test -json`.
 */
public data class GoTestSummary(
    /** Number of passing test cases. */
    val passed: Int,
    /** Number of failing test cases. */
    val failed: Int,
    /** Number of skipped test cases. */
    val skipped: Int,
    /** Packages that failed before or outside an individual test case. */
    val failedPackages: Set<String>,
)

/**
 * Incrementally parses and aggregates events emitted by `go test -json`.
 */
public class GoTestJsonAccumulator {
    private val outcomes = mutableMapOf<String, String>()
    private val failedPackages = mutableSetOf<String>()

    /**
     * Consumes one output line, ignoring non-JSON and non-terminal events.
     */
    public fun accept(line: String) {
        val event =
            runCatching { Json.parseToJsonElement(line).jsonObject }
                .getOrNull() ?: return
        val action = event["Action"]?.jsonPrimitive?.contentOrNull ?: return
        val packageName = event["Package"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val testName = event["Test"]?.jsonPrimitive?.contentOrNull

        if (testName == null) {
            if (action == "fail" && packageName.isNotEmpty()) failedPackages.add(packageName)
            return
        }

        if (action in TERMINAL_ACTIONS) {
            outcomes["$packageName\u0000$testName"] = action
        }
    }

    /**
     * Returns the current aggregate summary.
     */
    public fun summary(): GoTestSummary =
        GoTestSummary(
            passed = outcomes.values.count { it == "pass" },
            failed = outcomes.values.count { it == "fail" },
            skipped = outcomes.values.count { it == "skip" },
            failedPackages = failedPackages.toSet(),
        )

    private companion object {
        val TERMINAL_ACTIONS: Set<String> = setOf("pass", "fail", "skip")
    }
}
