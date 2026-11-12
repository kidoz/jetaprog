package su.kidoz.jetaprog.build.go

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One event from a `go` command.
 */
public sealed interface GoOutput {
    /** A stdout line that is not a recognized diagnostic or test event. */
    public data class Stdout(
        val line: String,
    ) : GoOutput

    /** A stderr line that is not a recognized diagnostic. */
    public data class Stderr(
        val line: String,
    ) : GoOutput

    /** The command that is about to run. */
    public data class CommandStarted(
        val command: String,
        val args: List<String>,
    ) : GoOutput

    /**
     * A compiler or vet diagnostic.
     *
     * [line] and [column] are 1-based, matching the `file:line:column:` form Go prints.
     */
    public data class Diagnostic(
        val file: String,
        val line: Int,
        val column: Int?,
        val message: String,
        val severity: String,
    ) : GoOutput

    /** One record from `go test -json`. */
    public data class TestEvent(
        val action: String,
        val packageName: String?,
        val test: String?,
        val output: String?,
        val elapsedSeconds: Double?,
    ) : GoOutput

    /** The process exited. */
    public data class CommandCompleted(
        val exitCode: Int,
        val success: Boolean,
    ) : GoOutput
}

/**
 * Classifies a single line of `go` output.
 */
public object GoOutputParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val diagnosticPattern = Regex("""^(.+?):(\d+)(?::(\d+))?: (.*)$""")

    /**
     * Classifies [line].
     *
     * JSON test events are recognized on either stream. Other unrecognized stderr
     * stays [GoOutput.Stderr].
     */
    public fun parse(
        line: String,
        fromStderr: Boolean = false,
    ): GoOutput {
        parseTestEvent(line)?.let { return it }
        parseDiagnostic(line)?.let { return it }
        return if (fromStderr) GoOutput.Stderr(line) else GoOutput.Stdout(line)
    }

    private fun parseTestEvent(line: String): GoOutput.TestEvent? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"Action\"")) return null
        val event =
            runCatching { json.decodeFromString<GoTestEventJson>(trimmed) }.getOrNull() ?: return null
        return GoOutput.TestEvent(
            action = event.action,
            packageName = event.packageName,
            test = event.test,
            output = event.output,
            elapsedSeconds = event.elapsed,
        )
    }

    private fun parseDiagnostic(line: String): GoOutput.Diagnostic? {
        val match = diagnosticPattern.matchEntire(line.trim()) ?: return null
        val file = match.groupValues[1]
        if (file.startsWith("{")) return null
        val message = match.groupValues[4]
        return GoOutput.Diagnostic(
            file = file,
            line = match.groupValues[2].toIntOrNull() ?: return null,
            column = match.groupValues[3].toIntOrNull(),
            message = message,
            severity = if (message.startsWith("warning:")) "warning" else "error",
        )
    }
}

@Serializable
private data class GoTestEventJson(
    @SerialName("Action") val action: String,
    @SerialName("Package") val packageName: String? = null,
    @SerialName("Test") val test: String? = null,
    @SerialName("Output") val output: String? = null,
    @SerialName("Elapsed") val elapsed: Double? = null,
)
