package su.kidoz.jetaprog.vcs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.process.ProcessConfig
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import su.kidoz.jetaprog.platform.process.ProcessResult
import su.kidoz.jetaprog.platform.process.RunningProcess
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitServiceConfigurationTest {
    @Test
    fun localGitUsesBoundedTimeoutAndNonInteractiveConfiguration() =
        runTest {
            val executor =
                RecordingProcessExecutor(
                    Result.success(ProcessResult(0, "## main\u0000", "")),
                )

            DefaultGitService(executor, "/workspace").status().getOrThrow()

            val config = assertNotNull(executor.lastConfig)
            assertEquals(30_000L, config.timeoutMillis)
            assertNonInteractiveGitConfiguration(config)
        }

    @Test
    fun networkGitUsesLongerBoundedTimeoutWithoutDisablingCredentialHelpers() =
        runTest {
            val executor = RecordingProcessExecutor(Result.success(ProcessResult(0, "done", "")))
            val service = DefaultGitService(executor, "/workspace")

            service.push().getOrThrow()
            assertEquals(120_000L, executor.lastConfig?.timeoutMillis)
            assertNonInteractiveGitConfiguration(assertNotNull(executor.lastConfig))

            service.pull().getOrThrow()
            assertEquals(120_000L, executor.lastConfig?.timeoutMillis)
            assertNonInteractiveGitConfiguration(assertNotNull(executor.lastConfig))
        }

    @Test
    fun networkTimeoutReturnsActionableFailure() =
        runTest {
            val executor =
                RecordingProcessExecutor(
                    Result.failure(TimeoutException("Process timed out after 120000ms")),
                )

            val result = DefaultGitService(executor, "/workspace").push()

            val error = assertIs<IOException>(result.exceptionOrNull())
            assertIs<TimeoutException>(error.cause)
            assertTrue(error.message.orEmpty().contains("git push timed out after 120 seconds"))
            assertTrue(error.message.orEmpty().contains("network connection"))
            assertTrue(error.message.orEmpty().contains("credentials"))
        }

    @Test
    fun cancellationIsNotConvertedToGitFailure() =
        runTest {
            val executor = RecordingProcessExecutor(Result.failure(CancellationException("cancelled")))

            assertFailsWith<CancellationException> {
                DefaultGitService(executor, "/workspace").push()
            }
        }

    private fun assertNonInteractiveGitConfiguration(config: ProcessConfig) {
        assertEquals("0", config.environment["GIT_TERMINAL_PROMPT"])
        assertEquals("never", config.environment["SSH_ASKPASS_REQUIRE"])
        assertEquals("never", config.environment["GCM_INTERACTIVE"])
        assertTrue(config.environment["GIT_SSH_COMMAND"].orEmpty().contains("BatchMode=yes"))
        assertFalse(config.command.contains("-c"))
        assertFalse(config.command.any { it.startsWith("credential.helper") })
        assertFalse(config.environment.containsKey("GIT_CONFIG_PARAMETERS"))
        assertFalse(config.environment.containsKey("GIT_CONFIG_COUNT"))
    }
}

private class RecordingProcessExecutor(
    private val executionResult: Result<ProcessResult>,
) : ProcessExecutor {
    var lastConfig: ProcessConfig? = null
        private set

    override suspend fun executeRaw(config: ProcessConfig): Result<ProcessResult> {
        lastConfig = config
        return executionResult
    }

    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        timeoutMillis: Long,
    ): Result<ProcessResult> {
        lastConfig = ProcessConfig(command, workingDirectory, environment, timeoutMillis)
        return executionResult
    }

    override suspend fun executeShell(
        command: String,
        workingDirectory: String?,
        environment: Map<String, String>,
        timeoutMillis: Long,
    ): Result<ProcessResult> = Result.failure(UnsupportedOperationException("Not used by this test"))

    override suspend fun start(config: ProcessConfig): Result<RunningProcess> =
        Result.failure(UnsupportedOperationException("Not used by this test"))
}
