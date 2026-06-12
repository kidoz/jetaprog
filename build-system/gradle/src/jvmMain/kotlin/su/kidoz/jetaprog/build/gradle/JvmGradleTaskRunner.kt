package su.kidoz.jetaprog.build.gradle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        runCatching {
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

            val process = processExecutor.start(config).getOrThrow()
            runningProcess = process

            // Transform ProcessOutput to GradleOutput
            flow {
                process.output.collect { output ->
                    when (output) {
                        is ProcessOutput.Stdout -> {
                            val line = output.line
                            emit(parseOutputLine(line))
                        }

                        is ProcessOutput.Stderr -> {
                            emit(GradleOutput.Stderr(output.line))
                        }

                        is ProcessOutput.Exited -> {
                            runningProcess = null
                            emit(
                                GradleOutput.BuildFinished(
                                    success = output.exitCode == 0,
                                    exitCode = output.exitCode,
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun cancelTask() {
        runningProcess?.kill()
        runningProcess = null
    }

    override suspend fun discoverTasks(project: GradleProject): Result<GradleProject> =
        runCatching {
            val gradlewPath = getGradlewPath(project.rootPath)
            val result =
                processExecutor
                    .execute(
                        command = listOf(gradlewPath, "tasks", "--all", "--console=plain"),
                        workingDirectory = project.rootPath,
                        timeoutMillis = 60_000, // 1 minute timeout
                    ).getOrThrow()

            val tasks = parseTasksOutput(result.stdout)
            val projectName = parseProjectName(project.rootPath)

            project.copy(
                name = projectName,
                tasks = tasks,
            )
        }

    private fun getGradlewPath(projectPath: String): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val gradlewName = if (isWindows) "gradlew.bat" else "gradlew"
        return File(projectPath, gradlewName).absolutePath
    }

    private fun parseOutputLine(line: String): GradleOutput {
        // Check for task execution patterns
        val taskStartPattern = Regex("""^>\s*Task\s+([:\w]+)$""")
        val taskCompletedPattern = Regex("""^>\s*Task\s+([:\w]+)\s+(\w+)$""")

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

        val groupPattern = Regex("""^(\w[\w\s]+) tasks$""")
        val taskPattern = Regex("""^([:\w]+)\s*-\s*(.+)$""")

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

        return tasks
    }

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
}
