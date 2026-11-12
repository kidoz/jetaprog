package su.kidoz.jetaprog.platform.process

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val STREAM_JOIN_TIMEOUT_MILLIS = 1_000L
private const val PROCESS_STOP_TIMEOUT_MILLIS = 1_000L

/**
 * JVM implementation of ProcessExecutor using Java ProcessBuilder.
 */
public class JvmProcessExecutor : ProcessExecutor {
    override suspend fun executeRaw(config: ProcessConfig): Result<ProcessResult> = executeCaptured(config, raw = true)

    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        timeoutMillis: Long,
    ): Result<ProcessResult> =
        executeCaptured(ProcessConfig(command, workingDirectory, environment, timeoutMillis), raw = false)

    private suspend fun executeCaptured(
        config: ProcessConfig,
        raw: Boolean,
    ): Result<ProcessResult> =
        withContext(Dispatchers.IO) {
            var process: Process? = null
            var capturedOutput: CapturedOutput? = null
            try {
                val processBuilder = ProcessBuilder(config.command.withResolvedExecutable(config.environment))
                config.workingDirectory?.let { processBuilder.directory(File(it)) }
                processBuilder.environment().putAll(config.environment)
                processBuilder.redirectErrorStream(config.redirectErrorStream)

                val startedProcess = processBuilder.start()
                process = startedProcess
                startedProcess.outputStream.close()

                val output = CapturedOutput(startedProcess, raw)
                capturedOutput = output

                val exitCode = awaitProcessExit(startedProcess, config.timeoutMillis)
                if (!output.join()) {
                    throw IOException("Timed out while reading process output streams.")
                }
                output.rethrowFailure()

                Result.success(
                    ProcessResult(
                        exitCode = exitCode,
                        stdout = output.stdout.toString().let { if (raw) it else it.trimEnd() },
                        stderr = output.stderr.toString().let { if (raw) it else it.trimEnd() },
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            } finally {
                process?.let { cleanupProcess(it) }
                capturedOutput?.join()
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
            try {
                val processBuilder = ProcessBuilder(config.command.withResolvedExecutable(config.environment))
                config.workingDirectory?.let { processBuilder.directory(File(it)) }
                processBuilder.environment().putAll(config.environment)
                processBuilder.redirectErrorStream(config.redirectErrorStream)

                val process = processBuilder.start()
                Result.success<RunningProcess>(JvmRunningProcess(process))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
}

private class CapturedOutput(
    process: Process,
    raw: Boolean,
) {
    val stdout = StringBuilder()
    val stderr = StringBuilder()

    private val failure = AtomicReference<IOException?>()
    private val stdoutThread = captureThread(process.inputStream, stdout, raw)
    private val stderrThread = captureThread(process.errorStream, stderr, raw)

    init {
        stdoutThread.isDaemon = true
        stderrThread.isDaemon = true
        stdoutThread.start()
        stderrThread.start()
    }

    fun join(): Boolean {
        val stdoutJoined = stdoutThread.joinBounded()
        val stderrJoined = stderrThread.joinBounded()
        return stdoutJoined && stderrJoined
    }

    fun rethrowFailure() {
        failure.get()?.let { throw it }
    }

    private fun captureThread(
        stream: InputStream,
        destination: StringBuilder,
        raw: Boolean,
    ): Thread =
        Thread {
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    if (raw) {
                        destination.append(reader.readText())
                    } else {
                        reader.lineSequence().forEach { destination.appendLine(it) }
                    }
                }
            } catch (error: IOException) {
                failure.compareAndSet(null, error)
            }
        }
}

private suspend fun awaitProcessExit(
    process: Process,
    timeoutMillis: Long,
): Int {
    val exitCode =
        if (timeoutMillis > 0) {
            withTimeoutOrNull(timeoutMillis) { process.awaitExit() }
        } else {
            process.awaitExit()
        }
    return exitCode ?: throw TimeoutException("Process timed out after ${timeoutMillis}ms")
}

private suspend fun Process.awaitExit(): Int =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            destroyForcibly()
        }
        onExit().whenComplete { _, failure ->
            if (failure != null) {
                continuation.resumeWithException(failure)
            } else {
                continuation.resume(exitValue())
            }
        }
    }

private fun cleanupProcess(process: Process) {
    if (process.isAlive) {
        process.destroyForcibly()
    }
    closeProcessStreams(process)
    try {
        process.waitFor(PROCESS_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun closeProcessStreams(process: Process) {
    listOf(process.inputStream, process.errorStream, process.outputStream).forEach { stream ->
        try {
            stream.close()
        } catch (_: IOException) {
            // The process may already have closed the stream.
        }
    }
}

private fun Thread.joinBounded(): Boolean =
    try {
        join(STREAM_JOIN_TIMEOUT_MILLIS)
        !isAlive
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
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

    override suspend fun waitFor(): Int = awaitProcessExit(process, timeoutMillis = 0)

    override val isAlive: Boolean get() = process.isAlive

    override val output: Flow<ProcessOutput> =
        callbackFlow {
            val stdoutThread =
                outputThread(process.inputStream, { ProcessOutput.Stdout(it) }) { trySend(it) }
            val stderrThread =
                outputThread(process.errorStream, { ProcessOutput.Stderr(it) }) { trySend(it) }

            stdoutThread.isDaemon = true
            stderrThread.isDaemon = true
            stdoutThread.start()
            stderrThread.start()

            val waitThread =
                Thread {
                    try {
                        val exitCode = process.waitFor()
                        if (!stdoutThread.joinBounded() || !stderrThread.joinBounded()) {
                            closeProcessStreams(process)
                            stdoutThread.joinBounded()
                            stderrThread.joinBounded()
                        }
                        trySend(ProcessOutput.Exited(exitCode))
                        close()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        close()
                    }
                }
            waitThread.isDaemon = true
            waitThread.start()

            awaitClose {
                process.destroyForcibly()
                closeProcessStreams(process)
                stdoutThread.joinBounded()
                stderrThread.joinBounded()
                waitThread.joinBounded()
            }
        }

    private fun outputThread(
        stream: InputStream,
        event: (String) -> ProcessOutput,
        send: (ProcessOutput) -> Unit,
    ): Thread =
        Thread {
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        send(event(line))
                    }
                }
            } catch (_: IOException) {
                // Stream closed while the process was stopping.
            }
        }
}
