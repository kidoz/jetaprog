package su.kidoz.jetaprog.plugins.kotlin.mpp

import su.kidoz.jetaprog.build.gradle.GradleOutput

/**
 * Renders one Gradle output event as a display line for command results.
 */
internal fun formatGradleOutput(output: GradleOutput): String =
    when (output) {
        is GradleOutput.Stdout -> {
            output.line
        }

        is GradleOutput.Stderr -> {
            "[stderr] ${output.line}"
        }

        is GradleOutput.TaskStarted -> {
            "> Task ${output.taskPath}"
        }

        is GradleOutput.TaskCompleted -> {
            "> Task ${output.taskPath} ${output.outcome.name}"
        }

        is GradleOutput.BuildFinished -> {
            val status = if (output.success) "succeeded" else "failed"
            "Build $status with exit code ${output.exitCode}"
        }
    }
