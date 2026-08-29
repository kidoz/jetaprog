package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionEvent
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionService
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.configuration.CargoProfileType
import su.kidoz.jetaprog.configuration.ConfigurationEffect
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationManager
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.DockerComposeSettings
import su.kidoz.jetaprog.configuration.DockerRunSettings
import su.kidoz.jetaprog.configuration.DotNetConfigurationType
import su.kidoz.jetaprog.configuration.GoCommand
import su.kidoz.jetaprog.configuration.JavaBuildTool
import su.kidoz.jetaprog.configuration.JavaCommand
import su.kidoz.jetaprog.configuration.NodePackageManager
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.configuration.RunOutputLine
import su.kidoz.jetaprog.configuration.RunOutputType
import su.kidoz.jetaprog.configuration.SpringBootDevServerSettings
import su.kidoz.jetaprog.configuration.SpringBootSettings
import su.kidoz.jetaprog.configuration.TomcatLocalSettings
import su.kidoz.jetaprog.configuration.TomcatRemoteSettings
import su.kidoz.jetaprog.configuration.discovery.ConfigurationDiscovery
import su.kidoz.jetaprog.configuration.execution.ExecutionOrchestrator
import su.kidoz.jetaprog.configuration.execution.ExecutionOutput
import su.kidoz.jetaprog.configuration.execution.ExecutionResult
import su.kidoz.jetaprog.configuration.execution.GoTestJsonAccumulator
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.dap.service.DebugState
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import java.io.File

/**
 * ViewModel for run configuration management.
 */
public class ConfigurationViewModel(
    private val configurationManager: ConfigurationManager,
    private val processExecutor: ProcessExecutor,
    private val gradleExecutionService: GradleExecutionService,
    private val configurationDiscovery: ConfigurationDiscovery,
    private val executionOrchestrator: ExecutionOrchestrator,
    private val debugService: DebugService,
) : MviViewModel<ConfigurationIntent, ConfigurationState, ConfigurationEffect>(ConfigurationState()) {
    private var projectPath: String = ""
    private var debugSessionId: String? = null
    private var executionSessionId: String? = null
    private var executionJob: Job? = null

    override suspend fun handleIntent(intent: ConfigurationIntent) {
        when (intent) {
            is ConfigurationIntent.Initialize -> {
                initialize(intent.projectPath)
            }

            is ConfigurationIntent.SelectConfiguration -> {
                selectConfiguration(intent.id)
            }

            is ConfigurationIntent.RunActive -> {
                startActiveConfiguration()
            }

            is ConfigurationIntent.DebugActive -> {
                debugActiveConfiguration()
            }

            is ConfigurationIntent.Run -> {
                startConfiguration(intent.id)
            }

            is ConfigurationIntent.Stop -> {
                stopConfiguration()
            }

            is ConfigurationIntent.Restart -> {
                stopConfiguration()
                debugActiveConfiguration()
            }

            is ConfigurationIntent.Create -> {
                createConfiguration(intent.configuration)
            }

            is ConfigurationIntent.Update -> {
                updateConfiguration(intent.configuration)
            }

            is ConfigurationIntent.Delete -> {
                deleteConfiguration(intent.id)
            }

            is ConfigurationIntent.Duplicate -> {
                duplicateConfiguration(intent.id)
            }

            is ConfigurationIntent.MakePermanent -> {
                makePermanent(intent.id)
            }

            is ConfigurationIntent.MoveToFolder -> {
                moveToFolder(intent.id, intent.folderName)
            }

            is ConfigurationIntent.OpenDialog -> {
                openDialog()
            }

            is ConfigurationIntent.EditConfiguration -> {
                editConfiguration(intent.id)
            }

            is ConfigurationIntent.CreateNew -> {
                createNewConfiguration(intent.type)
            }

            is ConfigurationIntent.CreateRecommended -> {
                createRecommendedConfiguration()
            }

            is ConfigurationIntent.CloseDialog -> {
                closeDialog()
            }

            is ConfigurationIntent.SaveFromDialog -> {
                saveFromDialog(intent.configuration)
            }

            is ConfigurationIntent.ClearError -> {
                clearError()
            }

            is ConfigurationIntent.ClearExecutionOutput -> {
                clearExecutionOutput()
            }

            is ConfigurationIntent.DiscoverConfigurations -> {
                discoverConfigurations(intent.projectPath)
            }
        }
    }

    private suspend fun initialize(projectPath: String) {
        this.projectPath = projectPath
        // Clear existing state immediately so old project configs don't persist
        updateState { ConfigurationState() }
        configurationManager
            .initialize(projectPath)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }

                // Auto-discover configurations if none exist
                if (state.configurations.isEmpty()) {
                    discoverConfigurations(projectPath)
                }

                emitEffect(ConfigurationEffect.ConfigurationsLoaded(state.configurations.size))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to load configurations: ${error.message}"))
            }
    }

    private suspend fun selectConfiguration(id: ConfigurationId) {
        configurationManager
            .selectConfiguration(id)
            .onSuccess { state ->
                updateState {
                    copy(
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private fun startActiveConfiguration() {
        val activeConfig = currentState.activeConfiguration ?: return
        startConfiguration(activeConfig.id)
    }

    private fun startConfiguration(id: ConfigurationId) {
        if (executionJob?.isActive == true || currentState.isRunning) return
        executionJob = viewModelScope.launch { runConfiguration(id) }
    }

    private suspend fun debugActiveConfiguration() {
        val activeConfig = currentState.activeConfiguration ?: return
        val config = currentState.configurations.find { it.id == activeConfig.id } ?: return

        selectConfiguration(config.id)

        updateState {
            copy(
                runningConfigurationId = config.id,
                isRunning = true,
            )
        }

        emitEffect(ConfigurationEffect.DebugConfigurationStarted(config))

        debugService
            .startDebugSession(
                configuration = config,
                workspacePath = projectPath,
            ).onSuccess { session ->
                debugSessionId = session.id
                emitEffect(ConfigurationEffect.ShowSuccess("Debug session started: ${config.name}"))
                viewModelScope.launch {
                    session.state
                        .filter { it == DebugState.STOPPED }
                        .first()
                    if (debugSessionId == session.id) {
                        debugSessionId = null
                        updateState {
                            copy(
                                runningConfigurationId = null,
                                isRunning = false,
                            )
                        }
                        emitEffect(
                            ConfigurationEffect.ConfigurationFinished(
                                configuration = config,
                                success = true,
                                exitCode = session.exitCode.value ?: 0,
                            ),
                        )
                    }
                }
            }.onFailure { error ->
                updateState {
                    copy(
                        runningConfigurationId = null,
                        isRunning = false,
                        error = error.message,
                    )
                }
                emitEffect(ConfigurationEffect.ShowError("Failed to start debug session: ${error.message}"))
                emitEffect(
                    ConfigurationEffect.ConfigurationFinished(
                        configuration = config,
                        success = false,
                        exitCode = -1,
                    ),
                )
            }
    }

    private suspend fun runConfiguration(id: ConfigurationId) {
        val config = currentState.configurations.find { it.id == id } ?: return

        // Update to recent
        selectConfiguration(id)

        updateState {
            copy(
                runningConfigurationId = id,
                isRunning = true,
            )
        }

        try {
            emitEffect(ConfigurationEffect.ConfigurationStarted(config))

            val result = executeConfiguration(config)
            val exitCode = result.getOrNull() ?: -1
            if (currentState.outputConfigurationId == id) {
                updateState { copy(lastExecutionExitCode = exitCode) }
            }
            emitEffect(
                ConfigurationEffect.ConfigurationFinished(
                    configuration = config,
                    success = result.isSuccess && exitCode == 0,
                    exitCode = exitCode,
                ),
            )
        } catch (error: CancellationException) {
            if (currentState.outputConfigurationId == id) {
                updateState { copy(lastExecutionExitCode = CANCELLED_EXECUTION_EXIT_CODE) }
            }
            emitEffect(
                ConfigurationEffect.ConfigurationFinished(
                    configuration = config,
                    success = false,
                    exitCode = -1,
                ),
            )
            throw error
        } finally {
            executionJob = null
            updateState {
                if (runningConfigurationId == id) {
                    copy(
                        runningConfigurationId = null,
                        isRunning = false,
                    )
                } else {
                    this
                }
            }
        }
    }

    private suspend fun executeConfiguration(config: RunConfiguration): Result<Int> =
        when (val settings = config.settings) {
            is ConfigurationSettings.Gradle -> {
                executeGradle(settings)
            }

            is ConfigurationSettings.MesonBuild -> {
                executeMesonBuild(settings)
            }

            is ConfigurationSettings.MesonRun -> {
                executeMesonRun(settings)
            }

            is ConfigurationSettings.Python -> {
                executePython(settings)
            }

            is ConfigurationSettings.Poetry -> {
                executePoetry(settings)
            }

            is ConfigurationSettings.Uv -> {
                executeUv(settings)
            }

            is ConfigurationSettings.CargoBuild -> {
                executeCargoBuild(settings)
            }

            is ConfigurationSettings.CargoRun -> {
                executeCargoRun(settings)
            }

            is ConfigurationSettings.CargoTest -> {
                executeCargoTest(settings)
            }

            is ConfigurationSettings.CargoClippy -> {
                executeCargoClippy(settings)
            }

            is ConfigurationSettings.Go -> {
                executeGo(config)
            }

            is ConfigurationSettings.Node -> {
                executeNode(config)
            }

            is ConfigurationSettings.Java -> {
                executeJava(config)
            }

            is ConfigurationSettings.DotNetBuild -> {
                executeDotNet(config)
            }

            is ConfigurationSettings.DotNetRun -> {
                executeDotNet(config)
            }

            is ConfigurationSettings.DotNetTest -> {
                executeDotNet(config)
            }

            is ConfigurationSettings.DotNetDebug -> {
                Result.failure(UnsupportedOperationException(".NET debug requires the debug action"))
            }

            is ConfigurationSettings.Application -> {
                executeApplication(settings)
            }

            is ConfigurationSettings.ShellScript -> {
                executeShellScript(settings)
            }

            is ConfigurationSettings.Compound -> {
                executeCompound(settings)
            }

            // Server configurations - not yet implemented in this ViewModel
            is TomcatLocalSettings -> {
                Result.failure(UnsupportedOperationException("Tomcat execution not implemented"))
            }

            is TomcatRemoteSettings -> {
                Result.failure(UnsupportedOperationException("Tomcat execution not implemented"))
            }

            is SpringBootSettings -> {
                executeViaOrchestrator(config)
            }

            is SpringBootDevServerSettings -> {
                executeViaOrchestrator(config)
            }

            is DockerBuildSettings -> {
                Result.failure(UnsupportedOperationException("Docker execution not implemented"))
            }

            is DockerRunSettings -> {
                Result.failure(UnsupportedOperationException("Docker execution not implemented"))
            }

            is DockerComposeSettings -> {
                Result.failure(UnsupportedOperationException("Docker Compose execution not implemented"))
            }
        }

    private suspend fun executeGradle(settings: ConfigurationSettings.Gradle): Result<Int> {
        val args = settings.arguments + settings.jvmArguments.map { "-D$it" }
        val project = GradleProject(rootPath = projectPath)
        return try {
            var exitCode = 0
            gradleExecutionService.runTask(project, settings.taskPath, args, settings.environment).collect { event ->
                when (event) {
                    is GradleExecutionEvent.Output -> {
                        val output = event.value
                        if (output is GradleOutput.BuildFinished) exitCode = output.exitCode
                    }

                    is GradleExecutionEvent.TestResults,
                    is GradleExecutionEvent.TestReportFailure,
                    -> {
                        // Test events are presented by the Gradle tool window.
                    }
                }
            }
            Result.success(exitCode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Runs a configuration through the execution orchestrator, which knows how to build
     * its process (used for Spring Boot, whose launch logic lives in core/configuration),
     * streaming its output to the run panel.
     */
    private suspend fun executeViaOrchestrator(config: RunConfiguration): Result<Int> =
        coroutineScope {
            prepareExecutionOutput(config.id)
            val session = executionOrchestrator.execute(config, projectPath)
            executionSessionId = session.id
            val outputJob =
                launch {
                    session.output.collect { output ->
                        appendExecutionOutput(output.toRunOutputLine())
                    }
                }
            try {
                when (val executionResult = session.result.filterNotNull().first()) {
                    is ExecutionResult.Success -> {
                        Result.success(executionResult.exitCode)
                    }

                    is ExecutionResult.Failure -> {
                        if (executionResult.exitCode >= 0) {
                            Result.success(executionResult.exitCode)
                        } else {
                            Result.failure(IllegalStateException(executionResult.message))
                        }
                    }

                    is ExecutionResult.Cancelled -> {
                        throw CancellationException("Execution cancelled")
                    }
                }
            } finally {
                outputJob.cancelAndJoin()
                if (executionSessionId == session.id) executionSessionId = null
            }
        }

    private suspend fun executeGo(config: RunConfiguration): Result<Int> =
        coroutineScope {
            val settings = config.settings as ConfigurationSettings.Go
            val accumulator = if (settings.command == GoCommand.TEST) GoTestJsonAccumulator() else null
            val session = executionOrchestrator.execute(config, projectPath)
            executionSessionId = session.id
            val outputJob =
                accumulator?.let { summaryAccumulator ->
                    launch {
                        session.output.collect { output ->
                            if (output is ExecutionOutput.Stdout) summaryAccumulator.accept(output.line)
                        }
                    }
                }

            val result =
                try {
                    when (val executionResult = session.result.filterNotNull().first()) {
                        is ExecutionResult.Success -> {
                            Result.success(executionResult.exitCode)
                        }

                        is ExecutionResult.Failure -> {
                            if (executionResult.exitCode >= 0) {
                                Result.success(executionResult.exitCode)
                            } else {
                                Result.failure(IllegalStateException(executionResult.message))
                            }
                        }

                        is ExecutionResult.Cancelled -> {
                            throw CancellationException("Go execution cancelled")
                        }
                    }
                } finally {
                    outputJob?.cancelAndJoin()
                    if (executionSessionId == session.id) executionSessionId = null
                }

            accumulator?.summary()?.let { summary ->
                emitEffect(
                    ConfigurationEffect.GoTestsFinished(
                        passed = summary.passed,
                        failed = summary.failed,
                        skipped = summary.skipped,
                        failedPackages = summary.failedPackages,
                    ),
                )
            }
            result
        }

    private suspend fun executeNode(config: RunConfiguration): Result<Int> =
        coroutineScope {
            updateState {
                copy(
                    outputConfigurationId = config.id,
                    executionOutput = emptyList(),
                    lastExecutionExitCode = null,
                )
            }
            val session = executionOrchestrator.execute(config, projectPath)
            executionSessionId = session.id
            val outputJob =
                launch {
                    session.output.collect { output ->
                        appendExecutionOutput(output.toRunOutputLine())
                    }
                }
            try {
                when (val executionResult = session.result.filterNotNull().first()) {
                    is ExecutionResult.Success -> {
                        Result.success(executionResult.exitCode)
                    }

                    is ExecutionResult.Failure -> {
                        if (executionResult.exitCode >= 0) {
                            Result.success(executionResult.exitCode)
                        } else {
                            Result.failure(IllegalStateException(executionResult.message))
                        }
                    }

                    is ExecutionResult.Cancelled -> {
                        throw CancellationException("Node.js execution cancelled")
                    }
                }
            } finally {
                outputJob.cancelAndJoin()
                if (executionSessionId == session.id) executionSessionId = null
            }
        }

    private suspend fun executeJava(config: RunConfiguration): Result<Int> {
        val settings = config.settings as ConfigurationSettings.Java
        return if (settings.buildTool == JavaBuildTool.GRADLE) {
            executeJavaGradle(config, settings)
        } else {
            executeJavaProcess(config)
        }
    }

    private suspend fun executeJavaGradle(
        config: RunConfiguration,
        settings: ConfigurationSettings.Java,
    ): Result<Int> {
        prepareExecutionOutput(config.id)
        val rootPath = settings.workingDirectory ?: projectPath
        val args =
            buildList {
                addAll(settings.buildArguments)
                settings.testFilter?.takeIf { it.isNotBlank() }?.let {
                    add("--tests")
                    add(it)
                }
                if (settings.programArguments.isNotEmpty()) {
                    add("--args=${settings.programArguments.joinToString(" ")}")
                }
                settings.jvmArguments.forEach { add("-D$it") }
            }
        return try {
            var exitCode = 0
            gradleExecutionService
                .runTask(GradleProject(rootPath = rootPath), settings.task, args, settings.environment)
                .collect { event ->
                    when (event) {
                        is GradleExecutionEvent.Output -> {
                            val output = event.value
                            appendExecutionOutput(output.toRunOutputLine())
                            if (output is GradleOutput.BuildFinished) exitCode = output.exitCode
                        }

                        is GradleExecutionEvent.TestResults -> {
                            val results = event.value
                            appendExecutionOutput(
                                RunOutputLine(
                                    "Tests: ${results.passedCount} passed, ${results.failedCount} failed, " +
                                        "${results.skippedCount} skipped",
                                    if (results.failedCount == 0) RunOutputType.SUCCESS else RunOutputType.ERROR,
                                ),
                            )
                        }

                        is GradleExecutionEvent.TestReportFailure -> {
                            appendExecutionOutput(
                                RunOutputLine(
                                    "Could not load test results: ${event.message}",
                                    RunOutputType.ERROR,
                                ),
                            )
                        }
                    }
                }
            Result.success(exitCode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun executeJavaProcess(config: RunConfiguration): Result<Int> =
        coroutineScope {
            prepareExecutionOutput(config.id)
            val session = executionOrchestrator.execute(config, projectPath)
            executionSessionId = session.id
            val outputJob =
                launch {
                    session.output.collect { output ->
                        appendExecutionOutput(output.toRunOutputLine())
                    }
                }
            try {
                when (val executionResult = session.result.filterNotNull().first()) {
                    is ExecutionResult.Success -> {
                        Result.success(executionResult.exitCode)
                    }

                    is ExecutionResult.Failure -> {
                        if (executionResult.exitCode >= 0) {
                            Result.success(executionResult.exitCode)
                        } else {
                            Result.failure(IllegalStateException(executionResult.message))
                        }
                    }

                    is ExecutionResult.Cancelled -> {
                        throw CancellationException("Java execution cancelled")
                    }
                }
            } finally {
                outputJob.cancelAndJoin()
                if (executionSessionId == session.id) executionSessionId = null
            }
        }

    private suspend fun executeMesonBuild(settings: ConfigurationSettings.MesonBuild): Result<Int> {
        val command =
            buildList {
                add("meson")
                add("compile")
                add("-C")
                add(settings.buildDirectory)
                settings.target?.let { add(it) }
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = projectPath,
            ).map { it.exitCode }
    }

    private suspend fun executeMesonRun(settings: ConfigurationSettings.MesonRun): Result<Int> {
        val executablePath = "$projectPath/${settings.buildDirectory}/${settings.executable}"

        val command =
            buildList {
                add(executablePath)
                addAll(settings.programArguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeApplication(settings: ConfigurationSettings.Application): Result<Int> {
        val command =
            buildList {
                add(settings.executablePath)
                addAll(settings.programArguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeShellScript(settings: ConfigurationSettings.ShellScript): Result<Int> {
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCompound(settings: ConfigurationSettings.Compound): Result<Int> {
        if (settings.parallel) {
            // Run all configurations in parallel
            val results =
                settings.configurationIds.map { id ->
                    viewModelScope.launch { runConfiguration(id) }
                }
            results.forEach { it.join() }
        } else {
            // Run sequentially
            for (id in settings.configurationIds) {
                runConfiguration(id)
            }
        }
        return Result.success(0)
    }

    private suspend fun executePython(settings: ConfigurationSettings.Python): Result<Int> {
        val command =
            buildList {
                add(settings.pythonInterpreter)
                addAll(settings.interpreterArguments)
                if (settings.module != null) {
                    add("-m")
                    add(settings.module!!)
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = environment,
            ).map { it.exitCode }
    }

    private suspend fun executePoetry(settings: ConfigurationSettings.Poetry): Result<Int> {
        val command =
            buildList {
                add("poetry")
                add(settings.command.value)
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeUv(settings: ConfigurationSettings.Uv): Result<Int> {
        val command =
            buildList {
                add("uv")
                // Handle compound commands like "pip install"
                settings.command.value
                    .split(" ")
                    .forEach { add(it) }
                addAll(settings.arguments)
            }

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoBuild(settings: ConfigurationSettings.CargoBuild): Result<Int> {
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoRun(settings: ConfigurationSettings.CargoRun): Result<Int> {
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoTest(settings: ConfigurationSettings.CargoTest): Result<Int> {
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeCargoClippy(settings: ConfigurationSettings.CargoClippy): Result<Int> {
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

        return processExecutor
            .execute(
                command = command,
                workingDirectory = settings.workingDirectory ?: projectPath,
                environment = settings.environment,
            ).map { it.exitCode }
    }

    private suspend fun executeDotNet(config: RunConfiguration): Result<Int> =
        coroutineScope {
            prepareExecutionOutput(config.id)
            val session = executionOrchestrator.execute(config, projectPath)
            executionSessionId = session.id
            val outputJob =
                launch {
                    session.output.collect { output ->
                        appendExecutionOutput(output.toRunOutputLine())
                    }
                }
            try {
                when (val executionResult = session.result.filterNotNull().first()) {
                    is ExecutionResult.Success -> {
                        Result.success(executionResult.exitCode)
                    }

                    is ExecutionResult.Failure -> {
                        if (executionResult.exitCode >= 0) {
                            Result.success(executionResult.exitCode)
                        } else {
                            Result.failure(IllegalStateException(executionResult.message))
                        }
                    }

                    is ExecutionResult.Cancelled -> {
                        throw CancellationException(".NET execution cancelled")
                    }
                }
            } finally {
                outputJob.cancelAndJoin()
                if (executionSessionId == session.id) executionSessionId = null
            }
        }

    private suspend fun stopConfiguration() {
        gradleExecutionService.cancel()
        executionSessionId?.let(executionOrchestrator::stop)
        executionSessionId = null
        executionJob?.cancel(CancellationException("Run configuration stopped"))
        debugSessionId?.let { sessionId ->
            debugService.stopSession(sessionId)
            debugSessionId = null
        }
        updateState {
            copy(
                runningConfigurationId = null,
                isRunning = false,
            )
        }
    }

    private suspend fun createConfiguration(configuration: RunConfiguration) {
        configurationManager
            .addConfiguration(configuration)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
                emitEffect(ConfigurationEffect.ConfigurationSaved(configuration))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to create configuration: ${error.message}"))
            }
    }

    private suspend fun updateConfiguration(configuration: RunConfiguration) {
        configurationManager
            .updateConfiguration(configuration)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
                emitEffect(ConfigurationEffect.ConfigurationSaved(configuration))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
                emitEffect(ConfigurationEffect.ShowError("Failed to update configuration: ${error.message}"))
            }
    }

    private suspend fun deleteConfiguration(id: ConfigurationId) {
        configurationManager
            .deleteConfiguration(id)
            .onSuccess { state ->
                updateState {
                    copy(
                        configurations = state.configurations,
                        activeConfigurationId = state.activeConfigurationId,
                        recentConfigurationIds = state.recentConfigurationIds,
                    )
                }
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private suspend fun duplicateConfiguration(id: ConfigurationId) {
        configurationManager
            .duplicateConfiguration(id)
            .onSuccess { duplicated ->
                updateState {
                    copy(configurations = configurationManager.getState().configurations)
                }
                emitEffect(ConfigurationEffect.ConfigurationSaved(duplicated))
            }.onFailure { error ->
                updateState { copy(error = error.message) }
            }
    }

    private suspend fun makePermanent(id: ConfigurationId) {
        configurationManager
            .makePermanent(id)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
            }
    }

    private suspend fun moveToFolder(
        id: ConfigurationId,
        folderName: String?,
    ) {
        configurationManager
            .moveToFolder(id, folderName)
            .onSuccess { state ->
                updateState { copy(configurations = state.configurations) }
            }
    }

    private fun openDialog() {
        updateState { copy(isDialogOpen = true, editingConfiguration = null) }
    }

    private fun editConfiguration(id: ConfigurationId) {
        val config = currentState.configurations.find { it.id == id }
        updateState { copy(isDialogOpen = true, editingConfiguration = config) }
    }

    private fun createNewConfiguration(type: ConfigurationType) {
        val newConfig = createConfigurationForType(type, defaultNameForType(type))
        updateState { copy(isDialogOpen = true, editingConfiguration = newConfig) }
    }

    private fun createRecommendedConfiguration() {
        val recommendedType = detectRecommendedType() ?: ConfigurationType.APPLICATION
        val recommendedConfig = createConfigurationForType(recommendedType, recommendedNameForType(recommendedType))
        updateState { copy(isDialogOpen = true, editingConfiguration = recommendedConfig) }
    }

    private fun detectRecommendedType(): ConfigurationType? {
        if (projectPath.isBlank()) return null
        val root = File(projectPath)

        fun exists(name: String): Boolean = File(root, name).exists()

        val hasGradleSettings = exists("settings.gradle.kts") || exists("settings.gradle")
        val hasGradleBuild = exists("build.gradle.kts") || exists("build.gradle")
        if (hasGradleSettings || hasGradleBuild) {
            return if (isJavaProject(root)) ConfigurationType.JAVA_RUN else ConfigurationType.GRADLE
        }
        if (exists("pom.xml") && isJavaProject(root)) return ConfigurationType.JAVA_RUN
        if (exists("Cargo.toml")) return ConfigurationType.CARGO_RUN
        if (exists("go.mod")) return ConfigurationType.GO_RUN
        if (exists("package.json")) return ConfigurationType.NODE_RUN
        if (hasDotNetProject(root)) return ConfigurationType.DOTNET_RUN
        if (exists("meson.build")) return ConfigurationType.MESON_BUILD
        if (exists("uv.lock") || hasPyprojectSection(root, "tool.uv")) return ConfigurationType.UV
        if (exists("poetry.lock") || hasPyprojectSection(root, "tool.poetry")) return ConfigurationType.POETRY

        return null
    }

    private fun hasDotNetProject(root: File): Boolean =
        root
            .listFiles()
            ?.any { file ->
                file.isFile &&
                    (
                        file.name.endsWith(".sln") ||
                            file.name.endsWith(".slnx") ||
                            file.name.endsWith(".csproj") ||
                            file.name.endsWith(".fsproj") ||
                            file.name.endsWith(".vbproj") ||
                            file.name.endsWith(".dplproj")
                    )
            } ?: false

    private fun hasPyprojectSection(
        root: File,
        section: String,
    ): Boolean {
        val pyproject = File(root, "pyproject.toml")
        if (!pyproject.exists()) return false
        return runCatching { pyproject.readText().contains("[$section]") }.getOrDefault(false)
    }

    private fun createConfigurationForType(
        type: ConfigurationType,
        name: String,
    ): RunConfiguration =
        when (type) {
            ConfigurationType.GRADLE -> {
                configurationManager.createGradleConfiguration(
                    name = name,
                    taskPath = "build",
                )
            }

            ConfigurationType.MESON_BUILD -> {
                configurationManager.createMesonBuildConfiguration(
                    name = name,
                )
            }

            ConfigurationType.MESON_RUN -> {
                configurationManager.createMesonRunConfiguration(
                    name = name,
                    executable = "",
                )
            }

            ConfigurationType.APPLICATION -> {
                configurationManager.createApplicationConfiguration(
                    name = name,
                    executablePath = "",
                )
            }

            ConfigurationType.SHELL_SCRIPT -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.SHELL_SCRIPT,
                    settings = ConfigurationSettings.ShellScript(script = ""),
                )
            }

            ConfigurationType.COMPOUND -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.COMPOUND,
                    settings = ConfigurationSettings.Compound(configurationIds = emptyList()),
                )
            }

            ConfigurationType.PYTHON -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.PYTHON,
                    settings = ConfigurationSettings.Python(scriptPath = ""),
                )
            }

            ConfigurationType.POETRY -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.POETRY,
                    settings =
                        ConfigurationSettings.Poetry(
                            command = su.kidoz.jetaprog.configuration.PoetryCommand.RUN,
                        ),
                )
            }

            ConfigurationType.UV -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.UV,
                    settings = ConfigurationSettings.Uv(command = su.kidoz.jetaprog.configuration.UvCommand.RUN),
                )
            }

            ConfigurationType.CARGO_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_BUILD,
                    settings = ConfigurationSettings.CargoBuild(),
                )
            }

            ConfigurationType.CARGO_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_RUN,
                    settings = ConfigurationSettings.CargoRun(),
                )
            }

            ConfigurationType.CARGO_TEST -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_TEST,
                    settings = ConfigurationSettings.CargoTest(),
                )
            }

            ConfigurationType.CARGO_CLIPPY -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.CARGO_CLIPPY,
                    settings = ConfigurationSettings.CargoClippy(),
                )
            }

            ConfigurationType.GO_BUILD,
            ConfigurationType.GO_RUN,
            ConfigurationType.GO_TEST,
            -> {
                val command =
                    when (type) {
                        ConfigurationType.GO_BUILD -> GoCommand.BUILD
                        ConfigurationType.GO_RUN -> GoCommand.RUN
                        ConfigurationType.GO_TEST -> GoCommand.TEST
                    }
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = type,
                    settings =
                        ConfigurationSettings.Go(
                            command = command,
                            packagePattern = if (command == GoCommand.RUN) "." else "./...",
                            arguments = if (command == GoCommand.TEST) listOf("-json") else emptyList(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.NODE_RUN,
            ConfigurationType.NODE_BUILD,
            ConfigurationType.NODE_TEST,
            -> {
                val script =
                    when (type) {
                        ConfigurationType.NODE_RUN -> "start"
                        ConfigurationType.NODE_BUILD -> "build"
                        ConfigurationType.NODE_TEST -> "test"
                    }
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = type,
                    settings =
                        ConfigurationSettings.Node(
                            packageManager = detectNodePackageManager(),
                            script = script,
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.JAVA_RUN,
            ConfigurationType.JAVA_DEBUG,
            ConfigurationType.JAVA_TEST,
            -> {
                val command =
                    when (type) {
                        ConfigurationType.JAVA_RUN -> JavaCommand.RUN
                        ConfigurationType.JAVA_DEBUG -> JavaCommand.DEBUG
                        ConfigurationType.JAVA_TEST -> JavaCommand.TEST
                    }
                val buildTool = detectJavaBuildTool()
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = type,
                    settings =
                        ConfigurationSettings.Java(
                            command = command,
                            buildTool = buildTool,
                            task =
                                when {
                                    command == JavaCommand.TEST -> "test"
                                    buildTool == JavaBuildTool.GRADLE -> "run"
                                    else -> "compile exec:java"
                                },
                            executable = detectJavaBuildExecutable(buildTool),
                            mainClass = detectJavaMainClass(buildTool),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_BUILD,
                    settings =
                        ConfigurationSettings.DotNetBuild(
                            targetPath = findDotNetTargetPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_RUN,
                    settings =
                        ConfigurationSettings.DotNetRun(
                            projectPath = findDotNetProjectPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_TEST -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_TEST,
                    settings =
                        ConfigurationSettings.DotNetTest(
                            targetPath = findDotNetTargetPath(),
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.DOTNET_DEBUG -> {
                val projectFile = findDotNetProjectPath()
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOTNET_DEBUG,
                    settings =
                        ConfigurationSettings.DotNetDebug(
                            projectPath = projectFile,
                            targetFramework = projectFile?.let { readDotNetTargetFramework(File(it)) },
                            assemblyName = projectFile?.let { readDotNetAssemblyName(File(it)) },
                            workingDirectory = projectPath.ifBlank { null },
                        ),
                )
            }

            ConfigurationType.TOMCAT_LOCAL -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.TOMCAT_LOCAL,
                    settings =
                        TomcatLocalSettings(
                            tomcatHome = "",
                            deploymentSource =
                                su.kidoz.jetaprog.configuration.DeploymentSource
                                    .WarFile(""),
                        ),
                )
            }

            ConfigurationType.TOMCAT_REMOTE -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.TOMCAT_REMOTE,
                    settings =
                        TomcatRemoteSettings(
                            host = "",
                            username = "",
                            password = "",
                            deploymentSource =
                                su.kidoz.jetaprog.configuration.DeploymentSource
                                    .WarFile(""),
                        ),
                )
            }

            ConfigurationType.SPRING_BOOT -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.SPRING_BOOT,
                    settings = SpringBootSettings(mainClass = ""),
                )
            }

            ConfigurationType.DOCKER_BUILD -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_BUILD,
                    settings = DockerBuildSettings(imageName = ""),
                )
            }

            ConfigurationType.DOCKER_RUN -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_RUN,
                    settings = DockerRunSettings(imageName = ""),
                )
            }

            ConfigurationType.DOCKER_COMPOSE -> {
                RunConfiguration(
                    id = ConfigurationId.generate(),
                    name = name,
                    type = ConfigurationType.DOCKER_COMPOSE,
                    settings = DockerComposeSettings(),
                )
            }
        }

    private fun defaultNameForType(type: ConfigurationType): String =
        when (type) {
            ConfigurationType.GRADLE -> "New Gradle Configuration"
            ConfigurationType.MESON_BUILD -> "New Meson Build"
            ConfigurationType.MESON_RUN -> "New Meson Run"
            ConfigurationType.APPLICATION -> "New Application"
            ConfigurationType.SHELL_SCRIPT -> "New Shell Script"
            ConfigurationType.COMPOUND -> "New Compound"
            ConfigurationType.PYTHON -> "New Python Script"
            ConfigurationType.POETRY -> "New Poetry Command"
            ConfigurationType.UV -> "New uv Command"
            ConfigurationType.CARGO_BUILD -> "New Cargo Build"
            ConfigurationType.CARGO_RUN -> "New Cargo Run"
            ConfigurationType.CARGO_TEST -> "New Cargo Test"
            ConfigurationType.CARGO_CLIPPY -> "New Cargo Clippy"
            ConfigurationType.GO_BUILD -> "New Go Build"
            ConfigurationType.GO_RUN -> "New Go Run"
            ConfigurationType.GO_TEST -> "New Go Test"
            ConfigurationType.NODE_RUN -> "New Node.js Run"
            ConfigurationType.NODE_BUILD -> "New Node.js Build"
            ConfigurationType.NODE_TEST -> "New Node.js Test"
            ConfigurationType.JAVA_RUN -> "New Java Run"
            ConfigurationType.JAVA_DEBUG -> "New Java Debug"
            ConfigurationType.JAVA_TEST -> "New Java Test"
            ConfigurationType.DOTNET_BUILD -> "New .NET Build"
            ConfigurationType.DOTNET_RUN -> "New .NET Run"
            ConfigurationType.DOTNET_TEST -> "New .NET Test"
            ConfigurationType.DOTNET_DEBUG -> "New .NET Debug"
            ConfigurationType.TOMCAT_LOCAL -> "New Tomcat Local"
            ConfigurationType.TOMCAT_REMOTE -> "New Tomcat Remote"
            ConfigurationType.SPRING_BOOT -> "New Spring Boot"
            ConfigurationType.DOCKER_BUILD -> "New Docker Build"
            ConfigurationType.DOCKER_RUN -> "New Docker Run"
            ConfigurationType.DOCKER_COMPOSE -> "New Docker Compose"
        }

    private fun recommendedNameForType(type: ConfigurationType): String =
        when (type) {
            ConfigurationType.GRADLE -> "Gradle Build"
            ConfigurationType.MESON_BUILD -> "Meson Build"
            ConfigurationType.MESON_RUN -> "Meson Run"
            ConfigurationType.APPLICATION -> "Application"
            ConfigurationType.SHELL_SCRIPT -> "Shell Script"
            ConfigurationType.COMPOUND -> "Compound"
            ConfigurationType.PYTHON -> "Python"
            ConfigurationType.POETRY -> "Poetry Run"
            ConfigurationType.UV -> "uv Run"
            ConfigurationType.CARGO_BUILD -> "Cargo Build"
            ConfigurationType.CARGO_RUN -> "Cargo Run"
            ConfigurationType.CARGO_TEST -> "Cargo Test"
            ConfigurationType.CARGO_CLIPPY -> "Cargo Clippy"
            ConfigurationType.GO_BUILD -> "Go Build"
            ConfigurationType.GO_RUN -> "Go Run"
            ConfigurationType.GO_TEST -> "Go Test"
            ConfigurationType.NODE_RUN -> "Node.js Run"
            ConfigurationType.NODE_BUILD -> "Node.js Build"
            ConfigurationType.NODE_TEST -> "Node.js Test"
            ConfigurationType.JAVA_RUN -> "Java Run"
            ConfigurationType.JAVA_DEBUG -> "Java Debug"
            ConfigurationType.JAVA_TEST -> "Java Test"
            ConfigurationType.DOTNET_BUILD -> ".NET Build"
            ConfigurationType.DOTNET_RUN -> ".NET Run"
            ConfigurationType.DOTNET_TEST -> ".NET Test"
            ConfigurationType.DOTNET_DEBUG -> ".NET Debug"
            ConfigurationType.TOMCAT_LOCAL -> "Tomcat Local"
            ConfigurationType.TOMCAT_REMOTE -> "Tomcat Remote"
            ConfigurationType.SPRING_BOOT -> "Spring Boot"
            ConfigurationType.DOCKER_BUILD -> "Docker Build"
            ConfigurationType.DOCKER_RUN -> "Docker Run"
            ConfigurationType.DOCKER_COMPOSE -> "Docker Compose"
        }

    private fun detectNodePackageManager(): NodePackageManager {
        if (projectPath.isBlank()) return NodePackageManager.NPM
        val root = File(projectPath)
        return when {
            File(root, "pnpm-lock.yaml").exists() -> NodePackageManager.PNPM
            File(root, "yarn.lock").exists() -> NodePackageManager.YARN
            File(root, "bun.lock").exists() || File(root, "bun.lockb").exists() -> NodePackageManager.BUN
            else -> NodePackageManager.NPM
        }
    }

    private fun isJavaProject(root: File): Boolean {
        if (File(root, "src/main/java").exists()) return true
        val buildFile =
            File(root, "build.gradle.kts").takeIf(File::exists)
                ?: File(root, "build.gradle").takeIf(File::exists)
                ?: File(root, "pom.xml").takeIf(File::exists)
                ?: return false
        return runCatching {
            val content = buildFile.readText()
            content.contains("maven-compiler-plugin") ||
                content.contains("maven.compiler.source") ||
                JAVA_BUILD_PLUGIN_PATTERN.containsMatchIn(content)
        }.getOrDefault(false)
    }

    private fun detectJavaBuildTool(): JavaBuildTool =
        if (projectPath.isNotBlank() && File(projectPath, "pom.xml").exists()) {
            JavaBuildTool.MAVEN
        } else {
            JavaBuildTool.GRADLE
        }

    private fun detectJavaBuildExecutable(buildTool: JavaBuildTool): String? {
        if (projectPath.isBlank() || buildTool == JavaBuildTool.GRADLE) return null
        val root = File(projectPath)
        return File(root, "mvnw").takeIf(File::exists)?.absolutePath
            ?: File(root, "mvnw.cmd").takeIf(File::exists)?.absolutePath
    }

    private fun detectJavaMainClass(buildTool: JavaBuildTool): String? {
        if (projectPath.isBlank()) return null
        val root = File(projectPath)
        val buildFile =
            when (buildTool) {
                JavaBuildTool.GRADLE -> {
                    File(root, "build.gradle.kts").takeIf(File::exists)
                        ?: File(root, "build.gradle").takeIf(File::exists)
                }

                JavaBuildTool.MAVEN -> {
                    File(root, "pom.xml").takeIf(File::exists)
                }
            } ?: return null
        val pattern = if (buildTool == JavaBuildTool.GRADLE) JAVA_GRADLE_MAIN_PATTERN else JAVA_MAVEN_MAIN_PATTERN
        return runCatching {
            pattern
                .find(buildFile.readText())
                ?.groupValues
                ?.get(1)
                ?.trim()
        }.getOrNull()
    }

    private fun findDotNetTargetPath(): String? {
        if (projectPath.isBlank()) return null
        val root = File(projectPath)
        return root.findChildPath(".sln", ".slnx")
            ?: root.findDescendantPath(".csproj", ".fsproj", ".vbproj", ".dplproj")
    }

    private fun findDotNetProjectPath(): String? {
        if (projectPath.isBlank()) return null
        val projectFiles = File(projectPath).findDescendantFiles(".csproj", ".dplproj")
        return projectFiles.firstOrNull(::isRunnableDotNetProject)?.path ?: projectFiles.firstOrNull()?.path
    }

    private fun File.findChildPath(vararg extensions: String): String? =
        listFiles()
            ?.firstOrNull { file ->
                file.isFile && extensions.any { file.name.endsWith(it) }
            }?.path

    private fun File.findDescendantPath(vararg extensions: String): String? =
        findDescendantFiles(*extensions).firstOrNull()?.path

    private fun File.findDescendantFiles(vararg extensions: String): List<File> =
        walkTopDown()
            .onEnter { directory ->
                directory == this ||
                    (directory.name !in DOTNET_EXCLUDED_DIRECTORIES && !directory.name.startsWith('.'))
            }.maxDepth(DOTNET_PROJECT_SCAN_DEPTH)
            .filter { file -> file.isFile && extensions.any { file.name.endsWith(it, ignoreCase = true) } }
            .sortedBy(File::getPath)
            .toList()

    private fun isRunnableDotNetProject(projectFile: File): Boolean =
        runCatching {
            val content = projectFile.readText()
            dotNetOutputTypeRegex
                .find(content)
                ?.groupValues
                ?.get(1)
                ?.let { it.equals("Exe", true) || it.equals("WinExe", true) } == true ||
                content.contains("Microsoft.NET.Sdk.Web", ignoreCase = true)
        }.getOrDefault(false)

    private fun readDotNetTargetFramework(projectFile: File): String? =
        runCatching {
            val content = projectFile.readText()
            targetFrameworkRegex.find(content)?.groupValues?.get(1)
                ?: targetFrameworksRegex
                    .find(content)
                    ?.groupValues
                    ?.get(1)
                    ?.substringBefore(";")
        }.getOrNull()

    private fun readDotNetAssemblyName(projectFile: File): String? =
        runCatching {
            dotNetAssemblyNameRegex.find(projectFile.readText())?.groupValues?.get(1)
                ?: projectFile.nameWithoutExtension
        }.getOrNull()

    private fun closeDialog() {
        updateState { copy(isDialogOpen = false, editingConfiguration = null) }
    }

    private suspend fun saveFromDialog(configuration: RunConfiguration) {
        val isNew = currentState.configurations.none { it.id == configuration.id }
        if (isNew) {
            createConfiguration(configuration)
        } else {
            updateConfiguration(configuration)
        }
        closeDialog()
    }

    private fun clearError() {
        updateState { copy(error = null) }
    }

    private fun clearExecutionOutput() {
        updateState {
            val keepOutputSelected = isRunning && runningConfigurationId == outputConfigurationId
            copy(
                outputConfigurationId = if (keepOutputSelected) outputConfigurationId else null,
                executionOutput = emptyList(),
                lastExecutionExitCode = if (keepOutputSelected) lastExecutionExitCode else null,
            )
        }
    }

    private fun prepareExecutionOutput(configurationId: ConfigurationId) {
        updateState {
            copy(
                outputConfigurationId = configurationId,
                executionOutput = emptyList(),
                lastExecutionExitCode = null,
            )
        }
    }

    private fun appendExecutionOutput(line: RunOutputLine) {
        updateState {
            copy(executionOutput = (executionOutput + line).takeLast(MAX_EXECUTION_OUTPUT_LINES))
        }
    }

    private fun ExecutionOutput.toRunOutputLine(): RunOutputLine =
        when (this) {
            is ExecutionOutput.Stdout -> {
                RunOutputLine(line, RunOutputType.STDOUT)
            }

            is ExecutionOutput.Stderr -> {
                RunOutputLine(line, RunOutputType.STDERR)
            }

            is ExecutionOutput.Status -> {
                RunOutputLine(message, RunOutputType.INFO)
            }

            is ExecutionOutput.TaskStarted -> {
                RunOutputLine("Starting: $taskDescription", RunOutputType.INFO)
            }

            is ExecutionOutput.TaskCompleted -> {
                val type = if (success) RunOutputType.SUCCESS else RunOutputType.ERROR
                val action = if (success) "Finished" else "Failed"
                RunOutputLine("$action: $taskDescription", type)
            }

            is ExecutionOutput.MainExecutionStarted -> {
                RunOutputLine("Starting: $configurationName", RunOutputType.INFO)
            }

            is ExecutionOutput.ExecutionFinished -> {
                when (val executionResult = result) {
                    is ExecutionResult.Success -> {
                        RunOutputLine(
                            "Process finished with exit code ${executionResult.exitCode}",
                            RunOutputType.SUCCESS,
                        )
                    }

                    is ExecutionResult.Failure -> {
                        RunOutputLine(executionResult.message, RunOutputType.ERROR)
                    }

                    is ExecutionResult.Cancelled -> {
                        RunOutputLine("Process cancelled", RunOutputType.ERROR)
                    }
                }
            }
        }

    private fun GradleOutput.toRunOutputLine(): RunOutputLine =
        when (this) {
            is GradleOutput.Stdout -> {
                RunOutputLine(line, RunOutputType.STDOUT)
            }

            is GradleOutput.Stderr -> {
                RunOutputLine(line, RunOutputType.STDERR)
            }

            is GradleOutput.TaskStarted -> {
                RunOutputLine("Starting task: $taskPath", RunOutputType.INFO)
            }

            is GradleOutput.TaskCompleted -> {
                val success =
                    outcome != su.kidoz.jetaprog.build.gradle.TaskOutcome.FAILED &&
                        outcome != su.kidoz.jetaprog.build.gradle.TaskOutcome.CANCELLED
                RunOutputLine(
                    "$taskPath $outcome",
                    if (success) RunOutputType.INFO else RunOutputType.ERROR,
                )
            }

            is GradleOutput.BuildFinished -> {
                RunOutputLine(
                    if (success) "BUILD SUCCESSFUL" else "BUILD FAILED (exit code $exitCode)",
                    if (success) RunOutputType.SUCCESS else RunOutputType.ERROR,
                )
            }
        }

    private suspend fun discoverConfigurations(projectPath: String) {
        val existingNames = currentState.configurations.map { it.name }.toSet()

        // Use the configuration discovery service to detect project types and create configs
        val discoveredConfigs =
            configurationDiscovery.discoverConfigurations(
                projectPath = projectPath,
                existingNames = existingNames,
            )

        // Add all discovered configurations
        for (config in discoveredConfigs) {
            configurationManager.addConfiguration(config)
        }

        // Update state with new configurations
        val newState = configurationManager.getState()
        updateState {
            copy(
                configurations = newState.configurations,
                activeConfigurationId = newState.activeConfigurationId,
            )
        }

        if (discoveredConfigs.isNotEmpty()) {
            emitEffect(
                ConfigurationEffect.ShowSuccess(
                    "Discovered ${discoveredConfigs.size} configuration(s)",
                ),
            )
        }
    }

    /**
     * Get the output flow for a running configuration.
     * Returns null if no configuration is running.
     */
    public fun getRunningOutput(): Flow<su.kidoz.jetaprog.build.gradle.GradleOutput>? {
        // For now, only Gradle output is supported
        return null // Would need to track the flow from execution
    }
}

private val targetFrameworkRegex = Regex("<TargetFramework>\\s*([^<\\s]+)\\s*</TargetFramework>")
private val targetFrameworksRegex = Regex("<TargetFrameworks>\\s*([^<\\s]+)\\s*</TargetFrameworks>")
private val dotNetAssemblyNameRegex = Regex("<AssemblyName>\\s*([^<]+)\\s*</AssemblyName>")
private val dotNetOutputTypeRegex = Regex("<OutputType>\\s*([^<]+)\\s*</OutputType>")
private const val DOTNET_PROJECT_SCAN_DEPTH = 5
private val DOTNET_EXCLUDED_DIRECTORIES = setOf(".git", ".idea", ".gradle", "bin", "obj", "build", "node_modules")
private val JAVA_BUILD_PLUGIN_PATTERN =
    Regex("""(?m)(\bjava\b|id\s*\(\s*["']java(?:-library)?["']\s*\)|id\s+["']java(?:-library)?["'])""")
private val JAVA_GRADLE_MAIN_PATTERN = Regex("""mainClass(?:\.set\s*\(|\s*=\s*)["']([^"']+)["']""")
private val JAVA_MAVEN_MAIN_PATTERN = Regex("""<mainClass>\s*([^<]+)\s*</mainClass>""")
private const val CANCELLED_EXECUTION_EXIT_CODE = -1
private const val MAX_EXECUTION_OUTPUT_LINES = 5_000
