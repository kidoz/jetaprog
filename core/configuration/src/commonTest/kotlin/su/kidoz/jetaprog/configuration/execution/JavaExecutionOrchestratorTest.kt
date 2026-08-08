package su.kidoz.jetaprog.configuration.execution

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.JavaBuildTool
import su.kidoz.jetaprog.configuration.JavaCommand
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessOutput
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JavaExecutionOrchestratorTest {
    @Test
    fun `Maven run forwards main class program arguments and JVM options`() =
        runTest {
            val executor = CapturingProcessExecutor(ImmediateProcess())
            val orchestrator = ExecutionOrchestrator(executor, this)
            val configuration =
                javaConfiguration(
                    ConfigurationSettings.Java(
                        command = JavaCommand.RUN,
                        buildTool = JavaBuildTool.MAVEN,
                        task = "compile exec:java",
                        executable = "/workspace/mvnw",
                        mainClass = "com.example.Main",
                        programArguments = listOf("one", "two"),
                        buildArguments = listOf("-q"),
                        jvmArguments = listOf("-Xmx1g"),
                        environment = mapOf("MAVEN_OPTS" to "-Dexisting=true"),
                    ),
                )

            val session = orchestrator.execute(configuration, "/workspace")
            assertIs<ExecutionResult.Success>(session.result.filterNotNull().first())

            assertEquals(
                listOf(
                    "/workspace/mvnw",
                    "compile",
                    "exec:java",
                    "-q",
                    "-Dexec.mainClass=com.example.Main",
                    "-Dexec.args=one two",
                ),
                executor.lastConfig?.command,
            )
            assertEquals("-Dexisting=true -Xmx1g", executor.lastConfig?.environment?.get("MAVEN_OPTS"))
        }

    @Test
    fun `Maven test filter is forwarded and execution is cancellable`() =
        runTest {
            val process = BlockingProcess()
            val executor = CapturingProcessExecutor(process)
            val orchestrator = ExecutionOrchestrator(executor, this)
            val configuration =
                javaConfiguration(
                    ConfigurationSettings.Java(
                        command = JavaCommand.TEST,
                        buildTool = JavaBuildTool.MAVEN,
                        task = "test",
                        testFilter = "com.example.MainTest.method",
                    ),
                )

            val session = orchestrator.execute(configuration, "/workspace")
            process.started.await()
            orchestrator.stop(session.id)

            assertIs<ExecutionResult.Cancelled>(session.result.filterNotNull().first())
            assertEquals(listOf("mvn", "test", "-Dtest=com.example.MainTest.method"), executor.lastConfig?.command)
            assertTrue(process.killCalled)
        }

    private fun javaConfiguration(settings: ConfigurationSettings.Java): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId("java-test"),
            name = "Java",
            type = ConfigurationType.JAVA_RUN,
            settings = settings,
        )

    private class CapturingProcessExecutor(
        private val process: RunningProcess,
    ) : ProcessExecutor {
        var lastConfig: ProcessConfig? = null

        override suspend fun execute(
            command: List<String>,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.failure(UnsupportedOperationException())

        override suspend fun executeShell(
            command: String,
            workingDirectory: String?,
            environment: Map<String, String>,
            timeoutMillis: Long,
        ): Result<ProcessResult> = Result.failure(UnsupportedOperationException())

        override suspend fun start(config: ProcessConfig): Result<RunningProcess> {
            lastConfig = config
            return Result.success(process)
        }
    }

    private class ImmediateProcess : RunningProcess {
        override suspend fun writeStdin(text: String) = Unit

        override suspend fun closeStdin() = Unit

        override fun kill() = Unit

        override suspend fun waitFor(): Int = 0

        override val isAlive: Boolean = false
        override val output: Flow<ProcessOutput> = flowOf(ProcessOutput.Exited(0))
    }

    private class BlockingProcess : RunningProcess {
        val started = CompletableDeferred<Unit>()
        var killCalled: Boolean = false

        override suspend fun writeStdin(text: String) = Unit

        override suspend fun closeStdin() = Unit

        override fun kill() {
            killCalled = true
        }

        override suspend fun waitFor(): Int = 0

        override val isAlive: Boolean get() = !killCalled
        override val output: Flow<ProcessOutput> =
            flow {
                started.complete(Unit)
                awaitCancellation()
            }
    }
}
