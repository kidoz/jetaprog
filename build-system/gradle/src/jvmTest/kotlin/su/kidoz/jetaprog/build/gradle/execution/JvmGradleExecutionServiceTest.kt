package su.kidoz.jetaprog.build.gradle.execution

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.build.gradle.GradleOutput
import su.kidoz.jetaprog.build.gradle.GradleProject
import su.kidoz.jetaprog.build.gradle.GradleTaskRunner
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.build.gradle.importer.GradleModelImporter
import su.kidoz.jetaprog.build.gradle.test.GradleTestReportLoader
import su.kidoz.jetaprog.build.gradle.test.GradleTestRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmGradleExecutionServiceTest {
    @Test
    fun `task execution emits output and structured test results through one flow`() =
        runTest {
            val testRun = GradleTestRun(emptyList())
            val testLoader = FakeTestReportLoader(Result.success(testRun))
            val service =
                JvmGradleExecutionService(
                    taskRunner =
                        FakeTaskRunner(
                            flowOf(
                                GradleOutput.Stdout("compiling"),
                                GradleOutput.BuildFinished(success = true, exitCode = 0),
                            ),
                        ),
                    modelImporter = ImmediateModelImporter,
                    testReportLoader = testLoader,
                    currentTimeMillis = { 42L },
                )

            val events = service.runTask(GradleProject(rootPath = "/workspace"), "test").toList()

            assertEquals(3, events.size)
            assertIs<GradleExecutionEvent.Output>(events[0])
            assertIs<GradleExecutionEvent.Output>(events[1])
            assertEquals(testRun, assertIs<GradleExecutionEvent.TestResults>(events[2]).value)
            assertEquals(TestLoadRequest("/workspace", "test", 42L), testLoader.lastRequest)
            assertFalse(service.isRunning)
        }

    @Test
    fun `cancel stops active tooling api import and cancels its caller`() =
        runTest {
            val importer = BlockingModelImporter()
            val service =
                JvmGradleExecutionService(
                    taskRunner = FakeTaskRunner(flowOf()),
                    modelImporter = importer,
                    testReportLoader = FakeTestReportLoader(Result.success(GradleTestRun(emptyList()))),
                    currentTimeMillis = { 0L },
                )

            val importJob = launch { service.importModel("/workspace") }
            importer.started.await()

            assertTrue(service.isRunning)
            service.cancel()
            importJob.join()

            assertTrue(importer.cancelCalled)
            assertTrue(importJob.isCancelled)
            assertFalse(service.isRunning)
        }

    @Test
    fun `cancel stops active wrapper process and cancels its collector`() =
        runTest {
            val taskRunner = BlockingTaskRunner()
            val service =
                JvmGradleExecutionService(
                    taskRunner = taskRunner,
                    modelImporter = ImmediateModelImporter,
                    testReportLoader = FakeTestReportLoader(Result.success(GradleTestRun(emptyList()))),
                    currentTimeMillis = { 0L },
                )

            val taskJob =
                launch {
                    service.runTask(GradleProject(rootPath = "/workspace"), "build").collect { }
                }
            taskRunner.started.await()

            assertTrue(service.isRunning)
            service.cancel()
            taskJob.join()

            assertTrue(taskRunner.cancelCalled)
            assertTrue(taskJob.isCancelled)
            assertFalse(service.isRunning)
        }

    private class FakeTaskRunner(
        private val output: Flow<GradleOutput>,
    ) : GradleTaskRunner {
        override val isRunning: Boolean = false

        override suspend fun runTask(
            project: GradleProject,
            taskPath: String,
            args: List<String>,
        ): Result<Flow<GradleOutput>> = Result.success(output)

        override fun cancelTask() = Unit

        override suspend fun discoverTasks(project: GradleProject): Result<GradleProject> = Result.success(project)
    }

    private class BlockingTaskRunner : GradleTaskRunner {
        val started = CompletableDeferred<Unit>()
        var cancelCalled: Boolean = false

        override val isRunning: Boolean
            get() = started.isCompleted

        override suspend fun runTask(
            project: GradleProject,
            taskPath: String,
            args: List<String>,
        ): Result<Flow<GradleOutput>> =
            Result.success(
                flow {
                    started.complete(Unit)
                    awaitCancellation()
                },
            )

        override fun cancelTask() {
            cancelCalled = true
        }

        override suspend fun discoverTasks(project: GradleProject): Result<GradleProject> = Result.success(project)
    }

    private object ImmediateModelImporter : GradleModelImporter {
        override suspend fun import(projectRoot: String): Result<GradleImportModel> =
            Result.success(GradleImportModel(rootName = "project"))

        override fun cancel() = Unit
    }

    private class BlockingModelImporter : GradleModelImporter {
        val started = CompletableDeferred<Unit>()
        var cancelCalled: Boolean = false

        override suspend fun import(projectRoot: String): Result<GradleImportModel> {
            started.complete(Unit)
            awaitCancellation()
        }

        override fun cancel() {
            cancelCalled = true
        }
    }

    private class FakeTestReportLoader(
        private val result: Result<GradleTestRun>,
    ) : GradleTestReportLoader {
        var lastRequest: TestLoadRequest? = null

        override suspend fun load(
            projectRoot: String,
            taskPath: String,
            startedAtMillis: Long,
        ): Result<GradleTestRun> {
            lastRequest = TestLoadRequest(projectRoot, taskPath, startedAtMillis)
            return result
        }
    }

    private data class TestLoadRequest(
        val projectRoot: String,
        val taskPath: String,
        val startedAtMillis: Long,
    )
}
