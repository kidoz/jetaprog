package su.kidoz.jetaprog.plugins.dotnet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.dotnet.DotNetOutput

/**
 * Runs a dotnet CLI command and renders its streamed output as one text block for
 * command results.
 */
internal fun executeDotNetCommand(command: suspend () -> Result<Flow<DotNetOutput>>): String =
    runBlocking {
        command().fold(
            onSuccess = { flow ->
                flow.toList().joinToString("\n") { output -> formatDotNetOutput(output) }
            },
            onFailure = { error ->
                "Command failed: ${error.message}"
            },
        )
    }

/**
 * Renders one dotnet CLI output event as a display line.
 */
internal fun formatDotNetOutput(output: DotNetOutput): String =
    when (output) {
        is DotNetOutput.Stdout -> {
            output.line
        }

        is DotNetOutput.Stderr -> {
            "[stderr] ${output.line}"
        }

        is DotNetOutput.CommandStarted -> {
            "Running: ${output.command} ${output.args.joinToString(" ")}"
        }

        DotNetOutput.Restoring -> {
            "Restoring packages"
        }

        is DotNetOutput.Building -> {
            "Building ${output.target ?: "workspace"}"
        }

        is DotNetOutput.Testing -> {
            "Testing ${output.target ?: "workspace"}"
        }

        is DotNetOutput.Publishing -> {
            "Publishing ${output.target ?: "workspace"}"
        }

        is DotNetOutput.Packing -> {
            "Packing ${output.target ?: "workspace"}"
        }

        is DotNetOutput.CommandCompleted -> {
            val status = if (output.success) "succeeded" else "failed"
            "Command $status with exit code ${output.exitCode}"
        }
    }
