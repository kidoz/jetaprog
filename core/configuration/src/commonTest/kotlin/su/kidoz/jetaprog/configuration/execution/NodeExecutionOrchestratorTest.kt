package su.kidoz.jetaprog.configuration.execution

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationSettings
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.NodePackageManager
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

class NodeExecutionOrchestratorTest {
    @Test
    fun `npm script forwards arguments and streams process output`() =
        runTest {
            val process = ImmediateProcess(exitCode = 0)
            val processExecutor = CapturingProcessExecutor(process)
            val orchestrator = ExecutionOrchestrator(processExecutor, this)
            val configuration = nodeConfiguration(NodePackageManager.NPM, "test", listOf("--watch"))

            val session = orchestrator.execute(configuration, "/workspace")
            val result = session.result.filterNotNull().first()
            val output = session.output.take(4).toList()

            assertIs<ExecutionResult.Success>(result)
            assertEquals(listOf("npm", "run", "test", "--", "--watch"), processExecutor.lastConfig?.command)
            assertEquals("/workspace", processExecutor.lastConfig?.workingDirectory)
            assertTrue(output.any { it is ExecutionOutput.Stdout && it.line == "running tests" })
            assertTrue(output.any { it is ExecutionOutput.Stderr && it.line == "warning" })
        }

    @Test
    fun `yarn script forwards arguments without npm separator`() =
        runTest {
            val processExecutor = CapturingProcessExecutor(ImmediateProcess(exitCode = 0))
            val orchestrator = ExecutionOrchestrator(processExecutor, this)

            val session =
                orchestrator.execute(
                    nodeConfiguration(NodePackageManager.YARN, "dev", listOf("--port", "3000")),
                    "/workspace",
                )
            session.result.filterNotNull().first()

            assertEquals(
                listOf("yarn", "run", "dev", "--port", "3000"),
                processExecutor.lastConfig?.command,
            )
        }

    @Test
    fun `stop kills active node process and reports cancellation`() =
        runTest {
            val process = BlockingProcess()
            val orchestrator = ExecutionOrchestrator(CapturingProcessExecutor(process), this)
            val session = orchestrator.execute(nodeConfiguration(NodePackageManager.PNPM, "dev"), "/workspace")
            process.started.await()

            orchestrator.stop(session.id)
            val result = session.result.filterNotNull().first()

            assertIs<ExecutionResult.Cancelled>(result)
            assertTrue(process.killCalled)
        }

    private fun nodeConfiguration(
        packageManager: NodePackageManager,
        script: String,
        arguments: List<String> = emptyList(),
    ): RunConfiguration =
        RunConfiguration(
            id = ConfigurationId("node-test"),
            name = "Node.js $script",
            type = ConfigurationType.NODE_RUN,
            settings =
                ConfigurationSettings.Node(
                    packageManager = packageManager,
                    script = script,
                    arguments = arguments,
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

        override val output: Flow<ProcessOutput> =
            flowOf(
                ProcessOutput.Stdout("running tests"),
                ProcessOutput.Stderr("warning"),
                ProcessOutput.Exited(exitCode),
            )
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
