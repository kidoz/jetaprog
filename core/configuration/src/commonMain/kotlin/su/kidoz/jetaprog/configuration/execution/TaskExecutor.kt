package su.kidoz.jetaprog.configuration.execution

import su.kidoz.jetaprog.configuration.BeforeLaunchTask
import su.kidoz.jetaprog.configuration.CargoProfileType
import su.kidoz.jetaprog.platform.process.ProcessExecutor

/**
 * Executes individual before-launch tasks.
 */
public class TaskExecutor(
    private val processExecutor: ProcessExecutor,
) {
    /**
     * Execute a before-launch task.
     *
     * @param task The task to execute.
     * @param workspacePath The workspace root path.
     * @return The result of the task execution.
     */
    public suspend fun execute(
        task: BeforeLaunchTask,
        workspacePath: String,
    ): TaskResult =
        when (task) {
            is BeforeLaunchTask.Build -> {
                executeBuild(workspacePath)
            }

            is BeforeLaunchTask.GradleTask -> {
                executeGradleTask(task, workspacePath)
            }

            is BeforeLaunchTask.ShellCommand -> {
                executeShellCommand(task, workspacePath)
            }

            is BeforeLaunchTask.CargoBuild -> {
                executeCargoBuild(task, workspacePath)
            }

            is BeforeLaunchTask.CargoClippy -> {
                executeCargoClippy(workspacePath)
            }

            is BeforeLaunchTask.PoetryTask -> {
                executePoetryTask(task, workspacePath)
            }

            is BeforeLaunchTask.UvTask -> {
                executeUvTask(task, workspacePath)
            }

            is BeforeLaunchTask.RunConfiguration -> {
                // RunConfiguration tasks are handled by the orchestrator
                // as they require access to the configuration manager
                TaskResult.Success
            }
        }

    /**
     * Get a human-readable description of a task.
     */
    public fun getTaskDescription(task: BeforeLaunchTask): String =
        when (task) {
            is BeforeLaunchTask.Build -> "Build project"
            is BeforeLaunchTask.GradleTask -> "Run Gradle task: ${task.taskPath}"
            is BeforeLaunchTask.ShellCommand -> "Run command: ${task.command}"
            is BeforeLaunchTask.CargoBuild -> "Cargo build (${task.profile.displayName})"
            is BeforeLaunchTask.CargoClippy -> "Cargo clippy"
            is BeforeLaunchTask.PoetryTask -> "Poetry ${task.command.value}"
            is BeforeLaunchTask.UvTask -> "uv ${task.command.value}"
            is BeforeLaunchTask.RunConfiguration -> "Run configuration: ${task.configurationId.value}"
        }

    private suspend fun executeBuild(workspacePath: String): TaskResult {
        // Detect build system and run appropriate build command
        val buildFile =
            listOf(
                "build.gradle.kts",
                "build.gradle",
                "Cargo.toml",
                "meson.build",
                "pyproject.toml",
            ).firstOrNull { java.io.File(workspacePath, it).exists() }

        val command =
            when (buildFile) {
                "build.gradle.kts", "build.gradle" -> listOf("./gradlew", "build")
                "Cargo.toml" -> listOf("cargo", "build")
                "meson.build" -> listOf("meson", "compile", "-C", "builddir")
                "pyproject.toml" -> listOf("python", "-m", "build")
                else -> return TaskResult.Failure("No supported build system found")
            }

        return executeCommand(command, workspacePath)
    }

    private suspend fun executeGradleTask(
        task: BeforeLaunchTask.GradleTask,
        workspacePath: String,
    ): TaskResult {
        val command = listOf("./gradlew", task.taskPath)
        return executeCommand(command, workspacePath)
    }

    private suspend fun executeShellCommand(
        task: BeforeLaunchTask.ShellCommand,
        workspacePath: String,
    ): TaskResult =
        processExecutor
            .executeShell(task.command, workspacePath)
            .fold(
                onSuccess = { result ->
                    if (result.isSuccess) {
                        TaskResult.Success
                    } else {
                        TaskResult.Failure(
                            message = result.stderr.ifBlank { "Command failed with exit code ${result.exitCode}" },
                            exitCode = result.exitCode,
                        )
                    }
                },
                onFailure = { error ->
                    TaskResult.Failure(error.message ?: "Shell command failed")
                },
            )

    private suspend fun executeCargoBuild(
        task: BeforeLaunchTask.CargoBuild,
        workspacePath: String,
    ): TaskResult {
        val command =
            buildList {
                add("cargo")
                add("build")
                if (task.profile == CargoProfileType.RELEASE) {
                    add("--release")
                }
            }
        return executeCommand(command, workspacePath)
    }

    private suspend fun executeCargoClippy(workspacePath: String): TaskResult {
        val command = listOf("cargo", "clippy", "--all-targets")
        return executeCommand(command, workspacePath)
    }

    private suspend fun executePoetryTask(
        task: BeforeLaunchTask.PoetryTask,
        workspacePath: String,
    ): TaskResult {
        val command =
            buildList {
                add("poetry")
                add(task.command.value)
                addAll(task.arguments)
            }
        return executeCommand(command, workspacePath)
    }

    private suspend fun executeUvTask(
        task: BeforeLaunchTask.UvTask,
        workspacePath: String,
    ): TaskResult {
        val command =
            buildList {
                add("uv")
                task.command.value
                    .split(" ")
                    .forEach { add(it) }
                addAll(task.arguments)
            }
        return executeCommand(command, workspacePath)
    }

    private suspend fun executeCommand(
        command: List<String>,
        workspacePath: String,
    ): TaskResult =
        processExecutor
            .execute(command, workspacePath)
            .fold(
                onSuccess = { result ->
                    if (result.isSuccess) {
                        TaskResult.Success
                    } else {
                        TaskResult.Failure(
                            message = result.stderr.ifBlank { "Command failed with exit code ${result.exitCode}" },
                            exitCode = result.exitCode,
                        )
                    }
                },
                onFailure = { error ->
                    TaskResult.Failure(error.message ?: "Command execution failed")
                },
            )
}
