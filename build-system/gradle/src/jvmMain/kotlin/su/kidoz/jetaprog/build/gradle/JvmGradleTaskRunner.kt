package su.kidoz.jetaprog.build.gradle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.File

/**
 * JVM implementation of GradleTaskRunner using ProcessExecutor.
 */
public class JvmGradleTaskRunner(
    private val processExecutor: ProcessExecutor,
) : GradleTaskRunner {
    private var runningProcess: RunningProcess? = null

    override val isRunning: Boolean
        get() = runningProcess?.isAlive == true

    override suspend fun runTask(
        project: GradleProject,
        taskPath: String,
        args: List<String>,
    ): Result<Flow<GradleOutput>> =
        try {
            // Build the command
            val gradlewPath = getGradlewPath(project.rootPath)
            val command =
                buildList {
                    add(gradlewPath)
                    add(taskPath)
                    add("--console=plain") // Easier to parse output
                    addAll(args)
                }

            val config =
                ProcessConfig(
                    command = command,
                    workingDirectory = project.rootPath,
                )

            val process = startProcess(config)

            Result.success(
                flow {
                    var exitCode: Int? = null
                    try {
                        process.output
                            .takeWhile { output ->
                                if (output is ProcessOutput.Exited) exitCode = output.exitCode
                                output !is ProcessOutput.Exited
                            }.collect { output ->
                                when (output) {
                                    is ProcessOutput.Stdout -> emit(parseOutputLine(output.line))
                                    is ProcessOutput.Stderr -> emit(GradleOutput.Stderr(output.line))
                                    is ProcessOutput.Exited -> Unit
                                }
                            }
                        val completedExitCode = checkNotNull(exitCode) { "Gradle process ended without an exit code" }
                        emit(
                            GradleOutput.BuildFinished(
                                success = completedExitCode == 0,
                                exitCode = completedExitCode,
                            ),
                        )
                    } finally {
                        if (process.isAlive) process.kill()
                        if (runningProcess === process) runningProcess = null
                    }
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    override fun cancelTask() {
        runningProcess?.kill()
        runningProcess = null
    }

    override suspend fun discoverTasks(project: GradleProject): Result<GradleProject> =
        try {
            val gradlewPath = getGradlewPath(project.rootPath)
            val process =
                startProcess(
                    ProcessConfig(
                        command = listOf(gradlewPath, "tasks", "--all", "--console=plain"),
                        workingDirectory = project.rootPath,
                    ),
                )

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            var exitCode: Int? = null
            try {
                withTimeout(TASK_DISCOVERY_TIMEOUT_MILLIS) {
                    process.output
                        .takeWhile { output ->
                            if (output is ProcessOutput.Exited) exitCode = output.exitCode
                            output !is ProcessOutput.Exited
                        }.collect { output ->
                            when (output) {
                                is ProcessOutput.Stdout -> stdout.appendLine(output.line)
                                is ProcessOutput.Stderr -> stderr.appendLine(output.line)
                                is ProcessOutput.Exited -> Unit
                            }
                        }
                }
            } finally {
                if (process.isAlive) process.kill()
                if (runningProcess === process) runningProcess = null
            }

            val completedExitCode = checkNotNull(exitCode) { "Gradle task discovery ended without an exit code" }
            check(completedExitCode == 0) {
                stderr.toString().trim().ifEmpty { "Gradle task discovery failed with exit code $completedExitCode" }
            }
            val tasks = parseTasksOutput(stdout.toString())
            val projectName = parseProjectName(project.rootPath)

            Result.success(
                project.copy(
                    name = projectName,
                    tasks = tasks,
                    subprojects = parseSubprojects(tasks),
                ),
            )
        } catch (error: CancellationException) {
            cancelTask()
            throw error
        } catch (error: Exception) {
            cancelTask()
            Result.failure(error)
        }

    private suspend fun startProcess(config: ProcessConfig): RunningProcess {
        val process =
            withContext(NonCancellable) {
                processExecutor.start(config).getOrThrow()
            }
        runningProcess = process
        currentCoroutineContext().ensureActive()
        return process
    }

    private fun getGradlewPath(projectPath: String): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val gradlewName = if (isWindows) "gradlew.bat" else "gradlew"
        return File(projectPath, gradlewName).absolutePath
    }

    private fun parseOutputLine(line: String): GradleOutput {
        // Check for task execution patterns
        val taskStartPattern = Regex("""^>\s*Task\s+($GRADLE_TASK_PATH_PATTERN)$""")
        val taskCompletedPattern = Regex("""^>\s*Task\s+($GRADLE_TASK_PATH_PATTERN)\s+([A-Z-]+)$""")

        taskCompletedPattern.matchEntire(line.trim())?.let { match ->
            val taskPath = match.groupValues[1]
            val outcomeStr = match.groupValues[2]
            val outcome =
                when (outcomeStr) {
                    "UP-TO-DATE" -> TaskOutcome.UP_TO_DATE
                    "SKIPPED" -> TaskOutcome.SKIPPED
                    "NO-SOURCE" -> TaskOutcome.NO_SOURCE
                    "FROM-CACHE" -> TaskOutcome.FROM_CACHE
                    "FAILED" -> TaskOutcome.FAILED
                    else -> TaskOutcome.SUCCESS
                }
            return GradleOutput.TaskCompleted(taskPath, outcome)
        }

        taskStartPattern.matchEntire(line.trim())?.let { match ->
            return GradleOutput.TaskStarted(match.groupValues[1])
        }

        return GradleOutput.Stdout(line)
    }

    private fun parseTasksOutput(output: String): List<GradleTask> {
        val tasks = mutableListOf<GradleTask>()
        var currentGroup: String? = null

        val groupPattern = Regex("""^(.+?) tasks$""")
        // The separator dash must be surrounded by whitespace so section underlines
        // ("--------") and prose ("Type-safe project accessors...") never parse as tasks.
        val taskPattern = Regex("""^($GRADLE_TASK_PATH_PATTERN)\s+-\s+(.+)$""")

        for (line in output.lines()) {
            groupPattern.matchEntire(line)?.let { match ->
                currentGroup = match.groupValues[1].trim()
                return@let
            }

            taskPattern.matchEntire(line)?.let { match ->
                val taskPath = match.groupValues[1]
                val description = match.groupValues[2].trim()
                val taskName = taskPath.substringAfterLast(":")

                tasks.add(
                    GradleTask(
                        path = taskPath,
                        name = taskName,
                        group = currentGroup,
                        description = description,
                    ),
                )
            }
        }

        return tasks.distinctBy { it.path }
    }

    private fun parseSubprojects(tasks: List<GradleTask>): List<String> =
        tasks
            .mapNotNull { task ->
                task.path
                    .takeIf { it.startsWith(":") && it.count { character -> character == ':' } > 1 }
                    ?.substringBeforeLast(":")
                    ?.removePrefix(":")
                    ?.replace(':', '/')
            }.distinct()
            .sorted()

    private fun parseProjectName(projectPath: String): String {
        val settingsFile =
            File(projectPath, "settings.gradle.kts")
                .takeIf { it.exists() }
                ?: File(projectPath, "settings.gradle")
                    .takeIf { it.exists() }

        if (settingsFile != null) {
            val content = settingsFile.readText()
            val pattern = Regex("""rootProject\.name\s*=\s*["'](.+?)["']""")
            pattern.find(content)?.let { match ->
                return match.groupValues[1]
            }
        }

        return File(projectPath).name
    }

    private companion object {
        private const val GRADLE_TASK_PATH_PATTERN = "[:A-Za-z0-9_.-]+"
        private const val TASK_DISCOVERY_TIMEOUT_MILLIS = 60_000L
    }
}
