package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryEffect
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryIntent
import su.kidoz.jetaprog.app.ui.dialogs.clone.CloneRepositoryState
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import java.io.File

/**
 * Drives the Clone Repository dialog: validates the form, runs `git clone
 * --progress` and streams its progress into the dialog state, and reports the
 * cloned project path back to the host.
 */
public class CloneRepositoryViewModel(
    private val processExecutor: ProcessExecutor,
    private val defaultDestination: String = System.getProperty("user.home").orEmpty(),
) : MviViewModel<CloneRepositoryIntent, CloneRepositoryState, CloneRepositoryEffect>(CloneRepositoryState()),
    Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cloneJob: Job? = null

    override suspend fun handleIntent(intent: CloneRepositoryIntent) {
        when (intent) {
            CloneRepositoryIntent.Show -> {
                updateState { CloneRepositoryState(isVisible = true, destinationDirectory = defaultDestination) }
            }

            CloneRepositoryIntent.Hide -> {
                if (!currentState.isCloning) {
                    updateState { CloneRepositoryState(isVisible = false) }
                }
            }

            is CloneRepositoryIntent.SetRepositoryUrl -> {
                updateState {
                    copy(
                        repositoryUrl = intent.url.trim(),
                        projectName = deriveProjectName(intent.url, keepUserValue = projectName.isNotBlank()),
                    )
                }
            }

            is CloneRepositoryIntent.SetDestinationDirectory -> {
                updateState { copy(destinationDirectory = intent.path) }
            }

            is CloneRepositoryIntent.SetProjectName -> {
                updateState { copy(projectName = intent.name) }
            }

            CloneRepositoryIntent.Clone -> {
                startClone()
            }
        }
    }

    /** Derives the folder name from a repository URL (`…/name.git` → `name`). */
    internal fun deriveProjectName(
        url: String,
        keepUserValue: Boolean,
    ): String {
        val current = currentState.projectName
        if (keepUserValue && current.isNotBlank()) return current
        val cleaned = url.trim().removeSuffix("/").substringAfterLast('/')
        return cleaned.removeSuffix(".git")
    }

    private fun startClone() {
        val state = currentState
        val url = state.repositoryUrl.trim()
        val projectName = state.projectName.trim()
        val destinationDirectory = state.destinationDirectory.trim()

        val error =
            when {
                url.isBlank() -> {
                    "Repository URL is required."
                }

                destinationDirectory.isBlank() -> {
                    "Destination directory is required."
                }

                projectName.isBlank() -> {
                    "Project name is required."
                }

                File(destinationDirectory, projectName).exists() -> {
                    "Target folder already exists: ${File(destinationDirectory, projectName).path}"
                }

                else -> {
                    null
                }
            }
        if (error != null) {
            updateState { copy(error = error) }
            return
        }

        val targetPath = File(destinationDirectory, projectName).absolutePath
        updateState { copy(isCloning = true, error = null, progressText = "Cloning…") }

        cloneJob =
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runClone(url = url, targetPath = targetPath)
                    }
                result.fold(
                    onSuccess = {
                        updateState { CloneRepositoryState(isVisible = false) }
                        emitEffect(CloneRepositoryEffect.Cloned(targetPath))
                    },
                    onFailure = { error ->
                        updateState {
                            copy(
                                isCloning = false,
                                progressText = "",
                                error = error.message ?: "Cloning failed.",
                            )
                        }
                    },
                )
            }
    }

    /**
     * Runs `git clone --progress` and reports the latest progress line into the
     * dialog state; fails with git's stderr message on a non-zero exit.
     */
    private suspend fun runClone(
        url: String,
        targetPath: String,
    ): Result<Unit> =
        runCatching {
            val process =
                processExecutor
                    .start(
                        config =
                            ProcessConfig(
                                command = listOf("git", "clone", "--progress", url, targetPath),
                                workingDirectory = null,
                            ),
                    ).getOrThrow()

            process.output.collect { output ->
                val line =
                    when (output) {
                        is ProcessOutput.Stdout -> output.line
                        is ProcessOutput.Stderr -> output.line
                        is ProcessOutput.Exited -> return@collect
                    }
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    updateState { copy(progressText = trimmed) }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("git clone failed (exit code $exitCode). Check the URL and your credentials.")
            }
        }

    override fun dispose() {
        cloneJob?.cancel()
        scope.cancel()
    }
}
