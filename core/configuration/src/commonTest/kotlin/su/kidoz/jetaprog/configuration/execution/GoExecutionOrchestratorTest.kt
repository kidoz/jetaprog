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
import su.kidoz.jetaprog.configuration.GoCommand
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

class GoExecutionOrchestratorTest {
    @Test
    fun `go run maps settings to process command`() =
        runTest {
            val process = ImmediateProcess(exitCode = 0)
            val processExecutor = CapturingProcessExecutor(process)
            val orchestrator = ExecutionOrchestrator(processExecutor, this)
            val configuration =
                goConfiguration(
                    command = GoCommand.RUN,
                    packagePattern = "./cmd/server",
                    arguments = listOf("-race"),
                    programArguments = listOf("--port", "8080"),
                )

            val session = orchestrator.execute(configuration, "/workspace")
            val result = session.result.filterNotNull().first()

            assertIs<ExecutionResult.Success>(result)
            assertEquals(
                listOf("go", "run", "-race", "./cmd/server", "--port", "8080"),
                processExecutor.lastConfig?.command,
            )
            assertEquals("/workspace", processExecutor.lastConfig?.workingDirectory)
        }

    @Test
    fun `stop kills active go process and reports cancellation`() =
        runTest {
            val process = BlockingProcess()
            val orchestrator = ExecutionOrchestrator(CapturingProcessExecutor(process), this)
            val session = orchestrator.execute(goConfiguration(GoCommand.TEST, "./..."), "/workspace")
            process.started.await()

            orchestrator.stop(session.id)
            val result = session.result.filterNotNull().first()

            assertIs<ExecutionResult.Cancelled>(result)
            assertTrue(process.killCalled)
        }

    private fun goConfiguration(
        command: GoCommand,
        packagePattern: String,
        arguments: List<String> = emptyList(),
        programArguments: List<String> = emptyList(),
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId("go-test"),
            name = "Go ${command.value}",
            type =
                when (command) {
                    GoCommand.BUILD -> ConfigurationType.GO_BUILD
                    GoCommand.RUN -> ConfigurationType.GO_RUN
                    GoCommand.TEST -> ConfigurationType.GO_TEST
                },
            settings =
                ConfigurationSettings.Go(
                    command = command,
                    packagePattern = packagePattern,
                    arguments = arguments,
                    programArguments = programArguments,
                ),
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

    private class ImmediateProcess(
        private val exitCode: Int,
    ) : RunningProcess {
        override suspend fun writeStdin(text: String) = Unit

        override suspend fun closeStdin() = Unit

        override fun kill() = Unit

        override suspend fun waitFor(): Int = exitCode

        override val isAlive: Boolean = false

        override val output: Flow<ProcessOutput> = flowOf(ProcessOutput.Exited(exitCode))
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

        override val isAlive: Boolean = !killCalled

        override val output: Flow<ProcessOutput> =
            flow {
                started.complete(Unit)
                awaitCancellation()
            }
    }
}
