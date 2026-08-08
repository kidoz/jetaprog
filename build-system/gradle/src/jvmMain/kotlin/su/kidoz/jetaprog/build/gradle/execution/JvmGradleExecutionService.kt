package su.kidoz.jetaprog.build.gradle.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
import su.kidoz.jetaprog.build.gradle.JvmGradleTaskRunner
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.build.gradle.importer.GradleModelImporter
import su.kidoz.jetaprog.build.gradle.importer.GradleProjectImporter
import su.kidoz.jetaprog.build.gradle.test.GradleTestReportLoader
import su.kidoz.jetaprog.build.gradle.test.JvmGradleTestReportLoader
import su.kidoz.jetaprog.platform.process.ProcessExecutor

/** JVM Gradle execution service backed by processes and the Tooling API. */
public class JvmGradleExecutionService internal constructor(
    private val taskRunner: GradleTaskRunner,
    private val modelImporter: GradleModelImporter,
    private val testReportLoader: GradleTestReportLoader,
    private val currentTimeMillis: () -> Long,
) : GradleExecutionService {
    /** Creates a project Gradle service using [processExecutor] for wrapper invocations. */
    public constructor(processExecutor: ProcessExecutor) :
        this(
            taskRunner = JvmGradleTaskRunner(processExecutor),
            modelImporter = GradleProjectImporter(),
            testReportLoader = JvmGradleTestReportLoader(),
            currentTimeMillis = System::currentTimeMillis,
        )

    private val operationMutex = Mutex()

    @Volatile
    private var activeOperation: ActiveOperation? = null

    override val isRunning: Boolean
        get() = activeOperation != null

    override fun runTask(
        project: GradleProject,
        taskPath: String,
        args: List<String>,
    ): Flow<GradleExecutionEvent> =
        flow {
            operationMutex.withLock {
                val job = requireNotNull(currentCoroutineContext()[Job])
                registerOperation(job, taskRunner::cancelTask)
                val startedAtMillis = currentTimeMillis()
                try {
                    taskRunner
                        .runTask(project, taskPath, args)
                        .getOrThrow()
                        .collect { output ->
                            emit(GradleExecutionEvent.Output(output))
                            if (output is GradleOutput.BuildFinished) {
                                emitTestResults(project.rootPath, taskPath, startedAtMillis)
                            }
                        }
                } finally {
                    clearOperation(job)
                }
            }
        }

    override suspend fun discoverTasks(project: GradleProject): Result<GradleProject> =
        executeExclusive(taskRunner::cancelTask) {
            taskRunner.discoverTasks(project)
        }

    override suspend fun importModel(projectRoot: String): Result<GradleImportModel> =
        executeExclusive(modelImporter::cancel) {
            modelImporter.import(projectRoot)
        }

    override fun cancel() {
        val operation = activeOperation ?: return
        operation.cancelBackend()
        operation.job.cancel(CancellationException("Gradle operation cancelled"))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<GradleExecutionEvent>.emitTestResults(
        projectRoot: String,
        taskPath: String,
        startedAtMillis: Long,
    ) {
        val result = testReportLoader.load(projectRoot, taskPath, startedAtMillis)
        currentCoroutineContext().ensureActive()
        result.fold(
            onSuccess = { testRun -> emit(GradleExecutionEvent.TestResults(testRun)) },
            onFailure = { error ->
                emit(
                    GradleExecutionEvent.TestReportFailure(
                        error.message ?: "Could not load Gradle test results",
                    ),
                )
            },
        )
    }

    private suspend fun <T> executeExclusive(
        cancelBackend: () -> Unit,
        operation: suspend () -> Result<T>,
    ): Result<T> =
        operationMutex.withLock {
            val job = requireNotNull(currentCoroutineContext()[Job])
            registerOperation(job, cancelBackend)
            try {
                operation().also { currentCoroutineContext().ensureActive() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                clearOperation(job)
            }
        }

    private fun registerOperation(
        job: Job,
        cancelBackend: () -> Unit,
    ) {
        activeOperation = ActiveOperation(job, cancelBackend)
    }

    private fun clearOperation(job: Job) {
        if (activeOperation?.job === job) activeOperation = null
    }

    private data class ActiveOperation(
        val job: Job,
        val cancelBackend: () -> Unit,
    )
}
