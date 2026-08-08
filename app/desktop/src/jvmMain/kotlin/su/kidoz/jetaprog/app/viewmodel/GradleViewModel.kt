package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.build.gradle.GradleDiagnostic
import su.kidoz.jetaprog.build.gradle.GradleDiagnosticsParser
import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionEvent
import su.kidoz.jetaprog.build.gradle.execution.GradleExecutionService
import su.kidoz.jetaprog.build.gradle.state.BuildResult
import su.kidoz.jetaprog.build.gradle.state.GradleIntent
import su.kidoz.jetaprog.build.gradle.state.GradleState
import su.kidoz.jetaprog.build.gradle.state.OutputLine
import su.kidoz.jetaprog.build.gradle.state.OutputType
import su.kidoz.jetaprog.build.gradle.test.GradleTestRun
import su.kidoz.jetaprog.common.Disposable

/**
 * ViewModel for the Gradle build panel.
 */
public class GradleViewModel(
    private val executionService: GradleExecutionService,
) : Disposable {
    private val _state = MutableStateFlow(GradleState())
    public val state: StateFlow<GradleState> = _state.asStateFlow()

    private val viewModelScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main,
        )

    private var buildJob: Job? = null
    private var buildStartTime: Long = 0

    public fun dispatch(intent: GradleIntent) {
        viewModelScope.launch {
            handleIntent(intent)
        }
    }

    private suspend fun handleIntent(intent: GradleIntent) {
        when (intent) {
            is GradleIntent.Initialize -> initialize(intent.projectPath)
            is GradleIntent.RunTask -> runTask(intent.taskPath, intent.args)
            is GradleIntent.CancelTask -> cancelTask()
            is GradleIntent.ClearOutput -> clearOutput()
            is GradleIntent.ToggleVisibility -> toggleVisibility()
            is GradleIntent.RefreshTasks -> refreshTasks()
            is GradleIntent.AddFavorite -> addFavorite(intent.taskPath)
            is GradleIntent.RemoveFavorite -> removeFavorite(intent.taskPath)
        }
    }

    private suspend fun initialize(projectPath: String) {
        val project = GradleProject(rootPath = projectPath)

        _state.update { it.copy(project = project) }

        // Discover tasks in background
        viewModelScope.launch {
            executionService.discoverTasks(project).onSuccess { updatedProject ->
                _state.update { it.copy(project = updatedProject) }
            }
        }
    }

    private suspend fun runTask(
        taskPath: String,
        args: List<String>,
    ) {
        val project = _state.value.project ?: return

        // Cancel any existing build
        buildJob?.cancelAndJoin()

        // Clear previous output and diagnostics
        _state.update {
            it.copy(
                isRunning = true,
                runningTask = taskPath,
                output = emptyList(),
                diagnostics = emptyList(),
                lastBuildResult = null,
                testRun = null,
                isVisible = true,
            )
        }

        buildStartTime = System.currentTimeMillis()

        // Add starting message
        appendOutput("Starting: ./gradlew $taskPath ${args.joinToString(" ")}", OutputType.INFO)

        buildJob =
            viewModelScope.launch {
                executionService
                    .runTask(project, taskPath, args)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        appendOutput("Failed to start build: ${error.message}", OutputType.ERROR)
                        _state.update { it.copy(isRunning = false, runningTask = null) }
                    }.collect { event ->
                        when (event) {
                            is GradleExecutionEvent.Output -> {
                                handleOutput(event.value, taskPath)
                            }

                            is GradleExecutionEvent.TestResults -> {
                                handleTestResults(event.value)
                            }

                            is GradleExecutionEvent.TestReportFailure -> {
                                appendOutput("Could not load test results: ${event.message}", OutputType.ERROR)
                            }
                        }
                    }
            }
    }

    private suspend fun handleOutput(
        output: GradleOutput,
        taskPath: String,
    ) {
        when (output) {
            is GradleOutput.Stdout -> {
                appendOutput(output.line, OutputType.STDOUT)
                // Check for diagnostics
                GradleDiagnosticsParser.parseLine(output.line)?.let { diagnostic ->
                    addDiagnostic(diagnostic)
                }
            }

            is GradleOutput.Stderr -> {
                appendOutput(output.line, OutputType.STDERR)
                // Check for diagnostics in stderr too
                GradleDiagnosticsParser.parseLine(output.line)?.let { diagnostic ->
                    addDiagnostic(diagnostic)
                }
            }

            is GradleOutput.TaskStarted -> {
                // Optionally show task progress
            }

            is GradleOutput.TaskCompleted -> {
                // Optionally show task completion
            }

            is GradleOutput.BuildFinished -> {
                val durationMs = System.currentTimeMillis() - buildStartTime
                val diagnosticsCount = _state.value.diagnostics.size

                val result =
                    BuildResult(
                        success = output.success,
                        exitCode = output.exitCode,
                        taskPath = taskPath,
                        durationMs = durationMs,
                        diagnosticsCount = diagnosticsCount,
                    )

                val outputType = if (output.success) OutputType.SUCCESS else OutputType.ERROR
                val message =
                    if (output.success) {
                        "BUILD SUCCESSFUL in ${formatDuration(durationMs)}"
                    } else {
                        "BUILD FAILED in ${formatDuration(durationMs)} (exit code ${output.exitCode})"
                    }

                appendOutput(message, outputType)

                _state.update {
                    it.copy(
                        isRunning = false,
                        runningTask = null,
                        lastBuildResult = result,
                    )
                }
            }
        }
    }

    private fun handleTestResults(testRun: GradleTestRun) {
        if (testRun.suites.isEmpty()) return
        _state.update { state -> state.copy(testRun = testRun) }
        appendOutput(
            "Tests: ${testRun.passedCount} passed, ${testRun.failedCount} failed, " +
                "${testRun.skippedCount} skipped",
            if (testRun.failedCount == 0) OutputType.SUCCESS else OutputType.ERROR,
        )
    }

    private suspend fun cancelTask() {
        executionService.cancel()
        buildJob?.cancelAndJoin()
        buildJob = null
        appendOutput("Build cancelled", OutputType.ERROR)
        _state.update { it.copy(isRunning = false, runningTask = null) }
    }

    private fun clearOutput() {
        _state.update { it.copy(output = emptyList(), diagnostics = emptyList(), testRun = null) }
    }

    private fun toggleVisibility() {
        _state.update { it.copy(isVisible = !it.isVisible) }
    }

    private suspend fun refreshTasks() {
        val project = _state.value.project ?: return

        executionService.discoverTasks(project).onSuccess { updatedProject ->
            _state.update { it.copy(project = updatedProject) }
        }
    }

    private fun addFavorite(taskPath: String) {
        _state.update {
            if (taskPath !in it.favoriteTasks) {
                it.copy(favoriteTasks = it.favoriteTasks + taskPath)
            } else {
                it
            }
        }
    }

    private fun removeFavorite(taskPath: String) {
        _state.update {
            it.copy(favoriteTasks = it.favoriteTasks - taskPath)
        }
    }

    private fun appendOutput(
        text: String,
        type: OutputType,
    ) {
        _state.update { state ->
            val newOutput = state.output + OutputLine(text, type)
            // Keep only last 5000 lines
            val trimmedOutput =
                if (newOutput.size > 5000) {
                    newOutput.takeLast(5000)
                } else {
                    newOutput
                }
            state.copy(output = trimmedOutput)
        }
    }

    private fun addDiagnostic(diagnostic: GradleDiagnostic) {
        _state.update { state ->
            state.copy(diagnostics = state.diagnostics + diagnostic)
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        return if (minutes > 0) {
            "${minutes}m ${remainingSeconds}s"
        } else {
            "${seconds}s"
        }
    }

    override fun dispose() {
        buildJob?.cancel()
        viewModelScope.cancel()
    }
}
