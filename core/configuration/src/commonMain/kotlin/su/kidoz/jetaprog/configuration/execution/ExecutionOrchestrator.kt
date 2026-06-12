package su.kidoz.jetaprog.configuration.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.configuration.BeforeLaunchTask
import su.kidoz.jetaprog.configuration.CargoProfileType
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.DockerComposeSettings
import su.kidoz.jetaprog.configuration.DockerRunSettings
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.configuration.SpringBootDevServerSettings
import su.kidoz.jetaprog.configuration.SpringBootSettings
import su.kidoz.jetaprog.configuration.TomcatLocalSettings
import su.kidoz.jetaprog.configuration.TomcatRemoteSettings
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.RunningProcess

/**
 * Orchestrates the execution of run configurations.
 *
 * Coordinates before-launch tasks and main execution, providing output streaming
 * and lifecycle management for execution sessions.
 */
public class ExecutionOrchestrator(
    private val processExecutor: ProcessExecutor,
    private val scope: CoroutineScope,
) : Disposable {
    private val taskExecutor = TaskExecutor(processExecutor)
    private val activeSessions = mutableMapOf<String, ExecutionSession>()
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeProcesses = mutableMapOf<String, RunningProcess>()

    @Volatile
    private var disposed = false

    /**
     * Execute a run configuration.
     *
     * @param configuration The configuration to execute.
     * @param workspacePath The workspace root path.
     * @return The execution session.
     */
    public fun execute(
        configuration: RunConfiguration,
        workspacePath: String,
    ): ExecutionSession {
        check(!disposed) { "ExecutionOrchestrator has been disposed" }

        val session =
            ExecutionSession(
                id = ExecutionSession.generateId(),
                configuration = configuration,
            )

        activeSessions[session.id] = session

        val job =
            scope.launch {
                executeInternal(session, workspacePath)
            }

        activeJobs[session.id] = job

        return session
    }

    /**
     * Stop an execution session.
     *
     * @param sessionId The session ID to stop.
     */
    public fun stop(sessionId: String) {
        activeSessions[sessionId]?.stop()
        activeProcesses[sessionId]?.kill()
        activeJobs[sessionId]?.cancel()
    }

    /**
     * Get an active session by ID.
     */
    public fun getSession(sessionId: String): ExecutionSession? = activeSessions[sessionId]

    /**
     * Get all active sessions.
     */
    public fun getActiveSessions(): List<ExecutionSession> = activeSessions.values.filter { !it.isCompleted }

    private suspend fun executeInternal(
        session: ExecutionSession,
        workspacePath: String,
    ) {
        try {
            // Execute before-launch tasks
            val beforeLaunchTasks = session.configuration.beforeLaunch
            if (beforeLaunchTasks.isNotEmpty()) {
                session.updateState(ExecutionState.RUNNING_TASKS)

                for ((index, task) in beforeLaunchTasks.withIndex()) {
                    if (session.isStopRequested) {
                        session.setResult(ExecutionResult.Cancelled)
                        return
                    }

                    val description = taskExecutor.getTaskDescription(task)

                    session.emitOutput(
                        ExecutionOutput.TaskStarted(
                            taskIndex = index,
                            taskDescription = description,
                        ),
                    )

                    val result = executeBeforeLaunchTask(task, workspacePath, session)

                    val success = result is TaskResult.Success

                    session.emitOutput(
                        ExecutionOutput.TaskCompleted(
                            taskIndex = index,
                            taskDescription = description,
                            success = success,
                        ),
                    )

                    if (!success) {
                        val failure = result as TaskResult.Failure
                        session.setResult(
                            ExecutionResult.Failure(
                                message = "Before-launch task failed: ${failure.message}",
                                exitCode = failure.exitCode,
                                phase = ExecutionPhase.BEFORE_LAUNCH,
                            ),
                        )
                        return
                    }
                }
            }

            // Check for stop request before main execution
            if (session.isStopRequested) {
                session.setResult(ExecutionResult.Cancelled)
                return
            }

            // Execute main configuration
            session.updateState(ExecutionState.RUNNING)
            session.emitOutput(
                ExecutionOutput.MainExecutionStarted(session.configuration.name),
            )

            val result = executeMainConfiguration(session, workspacePath)

            session.emitOutput(ExecutionOutput.ExecutionFinished(result))
            session.setResult(result)
        } catch (e: Exception) {
            val result =
                ExecutionResult.Failure(
                    message = e.message ?: "Unknown error",
                    phase = ExecutionPhase.MAIN,
                )
            session.emitOutput(ExecutionOutput.ExecutionFinished(result))
            session.setResult(result)
        } finally {
            cleanup(session.id)
        }
    }

    private suspend fun executeBeforeLaunchTask(
        task: BeforeLaunchTask,
        workspacePath: String,
        session: ExecutionSession,
    ): TaskResult {
        // Handle RunConfiguration tasks specially (they need ConfigurationManager)
        // For now, we delegate to TaskExecutor for all other tasks
        return taskExecutor.execute(task, workspacePath)
    }

    private suspend fun executeMainConfiguration(
        session: ExecutionSession,
        workspacePath: String,
    ): ExecutionResult {
        val settings = session.configuration.settings

        val processConfig =
            buildProcessConfig(settings, workspacePath)
                ?: return ExecutionResult.Failure("Unsupported configuration type")

        return processExecutor.start(processConfig).fold(
            onSuccess = { process ->
                activeProcesses[session.id] = process

                // Collect output
                process.output.collect { output ->
                    if (session.isStopRequested) {
                        process.kill()
                        return@collect
                    }

                    when (output) {
                        is ProcessOutput.Stdout -> {
                            session.emitOutput(ExecutionOutput.Stdout(output.line))
                        }

                        is ProcessOutput.Stderr -> {
                            session.emitOutput(ExecutionOutput.Stderr(output.line))
                        }

                        is ProcessOutput.Exited -> {
                            // Exit is handled after collection
                        }
                    }
                }

                if (session.isStopRequested) {
                    ExecutionResult.Cancelled
                } else {
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        ExecutionResult.Success(exitCode)
                    } else {
                        ExecutionResult.Failure(
                            message = "Process exited with code $exitCode",
                            exitCode = exitCode,
                        )
                    }
                }
            },
            onFailure = { error ->
                ExecutionResult.Failure(error.message ?: "Failed to start process")
            },
        )
    }

    private fun buildProcessConfig(
        settings: ConfigurationSettings,
        workspacePath: String,
    ): ProcessConfig? =
        when (settings) {
            is ConfigurationSettings.Gradle -> buildGradleConfig(settings, workspacePath)

            is ConfigurationSettings.MesonBuild -> buildMesonBuildConfig(settings, workspacePath)

            is ConfigurationSettings.MesonRun -> buildMesonRunConfig(settings, workspacePath)

            is ConfigurationSettings.Python -> buildPythonConfig(settings, workspacePath)

            is ConfigurationSettings.Poetry -> buildPoetryConfig(settings, workspacePath)

            is ConfigurationSettings.Uv -> buildUvConfig(settings, workspacePath)

            is ConfigurationSettings.CargoBuild -> buildCargoBuildConfig(settings, workspacePath)

            is ConfigurationSettings.CargoRun -> buildCargoRunConfig(settings, workspacePath)

            is ConfigurationSettings.CargoTest -> buildCargoTestConfig(settings, workspacePath)

            is ConfigurationSettings.CargoClippy -> buildCargoClippyConfig(settings, workspacePath)

            is ConfigurationSettings.Application -> buildApplicationConfig(settings, workspacePath)

            is ConfigurationSettings.ShellScript -> buildShellScriptConfig(settings, workspacePath)

            is ConfigurationSettings.Compound -> null

            // Compound requires special handling
            // Server configurations require their own managers
            is TomcatLocalSettings -> null

            is TomcatRemoteSettings -> null

            is SpringBootSettings -> buildSpringBootConfig(settings, workspacePath)

            is SpringBootDevServerSettings -> buildSpringBootConfig(settings.baseSettings, workspacePath)

            is DockerBuildSettings -> null

            // Docker requires DockerManager
            is DockerRunSettings -> null

            is DockerComposeSettings -> null
        }

    private fun buildGradleConfig(
        settings: ConfigurationSettings.Gradle,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("./gradlew")
                add(settings.taskPath)
                addAll(settings.arguments)
                settings.jvmArguments.forEach { add("-D$it") }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildMesonBuildConfig(
        settings: ConfigurationSettings.MesonBuild,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("meson")
                add("compile")
                add("-C")
                add(settings.buildDirectory)
                settings.target?.let { add(it) }
                addAll(settings.arguments)
            }

        return ProcessConfig(
            command = command,
            workingDirectory = workspacePath,
        )
    }

    private fun buildMesonRunConfig(
        settings: ConfigurationSettings.MesonRun,
        workspacePath: String,
    ): ProcessConfig {
        val executablePath = "$workspacePath/${settings.buildDirectory}/${settings.executable}"

        val command =
            buildList {
                add(executablePath)
                addAll(settings.programArguments)
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildPythonConfig(
        settings: ConfigurationSettings.Python,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add(settings.pythonInterpreter)
                addAll(settings.interpreterArguments)
                val module = settings.module
                if (module != null) {
                    add("-m")
                    add(module)
                } else {
                    add(settings.scriptPath)
                }
                addAll(settings.scriptArguments)
            }

        val environment = settings.environment.toMutableMap()
        if (settings.pythonPath.isNotEmpty()) {
            val pythonPath = settings.pythonPath.joinToString(System.getProperty("path.separator"))
            val existingPythonPath = environment["PYTHONPATH"]
            environment["PYTHONPATH"] =
                if (existingPythonPath != null) {
                    "$pythonPath${System.getProperty("path.separator")}$existingPythonPath"
                } else {
                    pythonPath
                }
        }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = environment,
        )
    }

    private fun buildPoetryConfig(
        settings: ConfigurationSettings.Poetry,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("poetry")
                add(settings.command.value)
                addAll(settings.arguments)
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildUvConfig(
        settings: ConfigurationSettings.Uv,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("uv")
                settings.command.value
                    .split(" ")
                    .forEach { add(it) }
                addAll(settings.arguments)
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildCargoBuildConfig(
        settings: ConfigurationSettings.CargoBuild,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("cargo")
                add("build")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.target?.let { add("--target=$it") }
                if (settings.features.isNotEmpty()) add("--features=${settings.features.joinToString(",")}")
                if (settings.allFeatures) add("--all-features")
                if (settings.noDefaultFeatures) add("--no-default-features")
                settings.package_?.let { add("--package=$it") }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildCargoRunConfig(
        settings: ConfigurationSettings.CargoRun,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("cargo")
                add("run")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.bin?.let { add("--bin=$it") }
                settings.example?.let { add("--example=$it") }
                if (settings.features.isNotEmpty()) add("--features=${settings.features.joinToString(",")}")
                if (settings.programArguments.isNotEmpty()) {
                    add("--")
                    addAll(settings.programArguments)
                }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildCargoTestConfig(
        settings: ConfigurationSettings.CargoTest,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("cargo")
                add("test")
                if (settings.profile == CargoProfileType.RELEASE) add("--release")
                settings.package_?.let { add("--package=$it") }
                if (settings.lib) add("--lib")
                if (settings.doc) add("--doc")
                settings.testName?.let { add(it) }
                if (settings.nocapture || settings.testArguments.isNotEmpty()) {
                    add("--")
                    if (settings.nocapture) add("--nocapture")
                    addAll(settings.testArguments)
                }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildCargoClippyConfig(
        settings: ConfigurationSettings.CargoClippy,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add("cargo")
                add("clippy")
                if (settings.fix) add("--fix")
                settings.package_?.let { add("--package=$it") }
                if (settings.allTargets) add("--all-targets")
                if (settings.denyWarnings) {
                    add("--")
                    add("-D")
                    add("warnings")
                }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildApplicationConfig(
        settings: ConfigurationSettings.Application,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            buildList {
                add(settings.executablePath)
                addAll(settings.programArguments)
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildShellScriptConfig(
        settings: ConfigurationSettings.ShellScript,
        workspacePath: String,
    ): ProcessConfig {
        val command =
            if (settings.isFile) {
                buildList {
                    settings.interpreter?.let { add(it) } ?: add("/bin/sh")
                    add(settings.script)
                    addAll(settings.arguments)
                }
            } else {
                buildList {
                    settings.interpreter?.let { add(it) } ?: add("/bin/sh")
                    add("-c")
                    add(settings.script)
                }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun buildSpringBootConfig(
        settings: SpringBootSettings,
        workspacePath: String,
    ): ProcessConfig {
        // If using Gradle task, delegate to Gradle
        if (settings.useGradleTask) {
            val command =
                buildList {
                    add("./gradlew")
                    add(settings.gradleTask)
                    if (settings.activeProfiles.isNotEmpty()) {
                        add("-Dspring.profiles.active=${settings.activeProfiles.joinToString(",")}")
                    }
                }

            return ProcessConfig(
                command = command,
                workingDirectory = settings.workingDirectory ?: workspacePath,
                environment = settings.environment,
            )
        }

        // Direct Java launch
        val command =
            buildList {
                add("java")

                // Add VM options
                if (settings.vmOptions.isNotBlank()) {
                    settings.vmOptions.split(" ").forEach { add(it) }
                }

                // Add debug options
                if (settings.enableDebug) {
                    add(settings.buildDebugArgs())
                }

                // Add Spring profiles
                settings.buildProfilesArg()?.let { add(it) }

                // Add Spring properties
                settings.springProperties.forEach { (key, value) ->
                    add("-D$key=$value")
                }

                // Server port override
                if (settings.serverPort > 0) {
                    add("-Dserver.port=${settings.serverPort}")
                }

                // Config file
                settings.configFile?.let {
                    add("-Dspring.config.location=$it")
                }

                // Main class
                add("-cp")
                add("${settings.modulePath}/build/classes/java/main:${settings.modulePath}/build/resources/main")
                add(settings.mainClass)

                // Program arguments
                if (settings.programArguments.isNotBlank()) {
                    settings.programArguments.split(" ").forEach { add(it) }
                }
            }

        return ProcessConfig(
            command = command,
            workingDirectory = settings.workingDirectory ?: workspacePath,
            environment = settings.environment,
        )
    }

    private fun cleanup(sessionId: String) {
        activeProcesses.remove(sessionId)
        activeJobs.remove(sessionId)
        // Keep session in activeSessions for result retrieval
    }

    override fun dispose() {
        disposed = true
        activeSessions.keys.toList().forEach { stop(it) }
        activeSessions.clear()
        activeJobs.clear()
        activeProcesses.clear()
    }
}
