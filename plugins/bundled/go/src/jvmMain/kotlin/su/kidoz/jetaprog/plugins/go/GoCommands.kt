package su.kidoz.jetaprog.plugins.go

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.build.go.GoOperation
import su.kidoz.jetaprog.build.go.GoOutput
import su.kidoz.jetaprog.build.go.GoProject
import su.kidoz.jetaprog.build.go.GoRunner

/**
 * Runs one Go toolchain command and returns the text shown for that command.
 */
internal fun runGoCommand(
    project: GoProject?,
    runner: GoRunner?,
    operation: GoOperation,
    requiresToolchainRoot: Boolean = true,
): String {
    val current = project ?: return "Go plugin is not active"
    val activeRunner = runner ?: return "Go plugin is not active"
    if (requiresToolchainRoot && !current.hasToolchainRoot) return "No Go module or workspace found"
    if (operation is GoOperation.WorkSync && current.workspace == null) return "No go.work file found"
    return runBlocking {
        activeRunner.execute(current, operation).fold(
            onSuccess = { flow -> flow.toList().joinToString("\n", transform = ::formatGoOutput) },
            onFailure = { error -> "Command failed: ${error.message}" },
        )
    }
}

/**
 * Formats one toolchain event for the command result.
 */
internal fun formatGoOutput(output: GoOutput): String =
    when (output) {
        is GoOutput.Stdout -> {
            output.line
        }

        is GoOutput.Stderr -> {
            "[stderr] ${output.line}"
        }

        is GoOutput.CommandStarted -> {
            "Running: ${output.command} ${output.args.joinToString(" ")}".trim()
        }

        is GoOutput.Diagnostic -> {
            formatDiagnostic(output)
        }

        is GoOutput.TestEvent -> {
            formatTestEvent(output)
        }

        is GoOutput.CommandCompleted -> {
            if (output.success) {
                "SUCCESS (exit code 0)"
            } else {
                "FAILED (exit code ${output.exitCode})"
            }
        }
    }

private fun formatDiagnostic(diagnostic: GoOutput.Diagnostic): String =
    buildString {
        append("[${diagnostic.severity}] ")
        append(diagnostic.file)
        append(':')
        append(diagnostic.line)
        diagnostic.column?.let {
            append(':')
            append(it)
        }
        append(": ")
        append(diagnostic.message)
    }

private fun formatTestEvent(event: GoOutput.TestEvent): String =
    when (event.action) {
        "output" -> {
            event.output.orEmpty().trimEnd()
        }

        "run" -> {
            "run ${event.packageName.orEmpty()} ${event.test.orEmpty()}".trim()
        }

        "pass", "fail", "skip" -> {
            val name = event.test ?: event.packageName ?: "package"
            val elapsed = event.elapsedSeconds?.let { " (${it}s)" }.orEmpty()
            "${event.action} $name$elapsed"
        }

        else -> {
            event.action
        }
    }
