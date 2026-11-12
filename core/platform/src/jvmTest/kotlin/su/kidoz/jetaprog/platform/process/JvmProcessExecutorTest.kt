package su.kidoz.jetaprog.platform.process

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmProcessExecutorTest {
    private val executor = JvmProcessExecutor()
    private val tempDir = Files.createTempDirectory("jetaprog-process-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun rawExecutionPreservesOutputExactly() =
        runBlocking {
            val result =
                executor
                    .executeRaw(
                        ProcessConfig(
                            command =
                                listOf(
                                    "sh",
                                    "-c",
                                    "printf 'first\\r\\nsecond \\n'; printf 'error\\r\\nlast \\t' >&2",
                                ),
                        ),
                    ).getOrThrow()

            assertEquals(0, result.exitCode)
            assertEquals("first\r\nsecond \n", result.stdout)
            assertEquals("error\r\nlast \t", result.stderr)
        }

    @Test
    fun capturedExecutionClosesStandardInput() =
        runBlocking {
            val result =
                executor
                    .executeRaw(
                        ProcessConfig(
                            command =
                                listOf(
                                    "sh",
                                    "-c",
                                    "if IFS= read -r line; then printf 'unexpected:%s' \"\$line\"; " +
                                        "else printf 'stdin-eof'; fi",
                                ),
                        ),
                    ).getOrThrow()

            assertEquals("stdin-eof", result.stdout)
        }

    @Test
    fun timeoutDestroysProcessAndReturnsFailure() =
        runBlocking {
            val pidFile = File(tempDir, "timeout.pid")
            val result =
                withTimeout(5_000) {
                    executor.executeRaw(
                        ProcessConfig(
                            command = waitingProcessCommand(pidFile),
                            timeoutMillis = 100,
                        ),
                    )
                }

            val error = result.exceptionOrNull()
            assertIs<TimeoutException>(error)
            assertTrue(error.message.orEmpty().contains("100ms"))
            assertProcessExits(pidFile)
        }

    @Test
    fun cancellationDestroysProcessAndPropagatesCancellation() =
        runBlocking {
            val pidFile = File(tempDir, "cancellation.pid")
            val execution =
                async {
                    executor.executeRaw(ProcessConfig(command = waitingProcessCommand(pidFile)))
                }
            awaitPidFile(pidFile)

            withTimeout(5_000) {
                execution.cancelAndJoin()
            }

            assertFailsWith<CancellationException> { execution.await() }
            assertProcessExits(pidFile)
        }

    @Test
    fun runningProcessOutputCompletesAfterExit() =
        runBlocking {
            val process =
                executor
                    .start(ProcessConfig(command = listOf("sh", "-c", "printf 'ready\\n'")))
                    .getOrThrow()

            val output = withTimeout(5_000) { process.output.toList() }

            assertEquals(listOf(ProcessOutput.Stdout("ready"), ProcessOutput.Exited(0)), output)
            assertEquals(0, process.waitFor())
        }

    private fun waitingProcessCommand(pidFile: File): List<String> =
        listOf(
            "sh",
            "-c",
            "printf '%s' \"\$\$\" > \"$1\"; while :; do :; done",
            "sh",
            pidFile.absolutePath,
        )

    private suspend fun awaitPidFile(pidFile: File) {
        withTimeout(5_000) {
            while (!pidFile.isFile || pidFile.length() == 0L) {
                delay(10)
            }
        }
    }

    private suspend fun assertProcessExits(pidFile: File) {
        awaitPidFile(pidFile)
        val processHandle = ProcessHandle.of(pidFile.readText().trim().toLong()).orElse(null)
        withTimeout(5_000) {
            while (processHandle?.isAlive == true) {
                delay(10)
            }
        }
        assertFalse(processHandle?.isAlive == true, "process ${pidFile.readText()} is still running")
    }
}
