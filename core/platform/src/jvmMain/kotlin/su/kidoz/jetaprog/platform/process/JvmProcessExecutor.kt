package su.kidoz.jetaprog.platform.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * JVM implementation of ProcessExecutor using Java ProcessBuilder.
 */
public class JvmProcessExecutor : ProcessExecutor {
    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        timeoutMillis: Long,
    ): Result<ProcessResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val processBuilder = ProcessBuilder(command)
                workingDirectory?.let { processBuilder.directory(File(it)) }
                processBuilder.environment().putAll(environment)

                val process = processBuilder.start()

                val stdout = StringBuilder()
                val stderr = StringBuilder()

                val stdoutThread =
                    Thread {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            reader.lineSequence().forEach { stdout.appendLine(it) }
                        }
                    }
                val stderrThread =
                    Thread {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            reader.lineSequence().forEach { stderr.appendLine(it) }
                        }
                    }

                stdoutThread.start()
                stderrThread.start()

                val exitCode =
                    if (timeoutMillis > 0) {
                        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly()
                            throw TimeoutException("Process timed out after ${timeoutMillis}ms")
                        }
                        process.exitValue()
                    } else {
                        process.waitFor()
                    }

                stdoutThread.join()
                stderrThread.join()

                ProcessResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                )
            }
        }

    override suspend fun executeShell(
        command: String,
        workingDirectory: String?,
        environment: Map<String, String>,
        timeoutMillis: Long,
    ): Result<ProcessResult> {
        val shellCommand =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                listOf("cmd", "/c", command)
            } else {
                listOf("sh", "-c", command)
            }
        return execute(shellCommand, workingDirectory, environment, timeoutMillis)
    }

    override suspend fun start(config: ProcessConfig): Result<RunningProcess> =
        withContext(Dispatchers.IO) {
            runCatching {
                val processBuilder = ProcessBuilder(config.command)
                config.workingDirectory?.let { processBuilder.directory(File(it)) }
                processBuilder.environment().putAll(config.environment)
                if (config.redirectErrorStream) {
                    processBuilder.redirectErrorStream(true)
                }

                val process = processBuilder.start()
                JvmRunningProcess(process)
            }
        }
}

/**
 * JVM implementation of RunningProcess.
 */
private class JvmRunningProcess(
    private val process: Process,
) : RunningProcess {
    private val stdinWriter = OutputStreamWriter(process.outputStream)

    override val inputStream: InputStream
        get() = process.inputStream

    override val outputStream: OutputStream
        get() = process.outputStream

    override suspend fun writeStdin(text: String) {
        withContext(Dispatchers.IO) {
            stdinWriter.write(text)
            stdinWriter.flush()
        }
    }

    override suspend fun closeStdin() {
        withContext(Dispatchers.IO) {
            stdinWriter.close()
        }
    }

    override fun kill() {
        process.destroyForcibly()
    }

    override suspend fun waitFor(): Int =
        withContext(Dispatchers.IO) {
            process.waitFor()
        }

    override val isAlive: Boolean get() = process.isAlive

    override val output: Flow<ProcessOutput> =
        callbackFlow {
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutThread =
                Thread {
                    try {
                        stdoutReader.lineSequence().forEach { line ->
                            trySend(ProcessOutput.Stdout(line))
                        }
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }

            val stderrThread =
                Thread {
                    try {
                        stderrReader.lineSequence().forEach { line ->
                            trySend(ProcessOutput.Stderr(line))
                        }
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }

            stdoutThread.isDaemon = true
            stderrThread.isDaemon = true
            stdoutThread.start()
            stderrThread.start()

            val waitThread =
                Thread {
                    try {
                        val exitCode = process.waitFor()
                        stdoutThread.join()
                        stderrThread.join()
                        trySend(ProcessOutput.Exited(exitCode))
                    } catch (_: Exception) {
                        // Interrupted
                    }
                }
            waitThread.isDaemon = true
            waitThread.start()

            awaitClose {
                process.destroyForcibly()
            }
        }
}
