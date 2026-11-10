package su.kidoz.jetaprog.acp.client

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.platform.process.withResolvedExecutable
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/** How long [StdioAcpTransport.close] waits for the agent process to exit. */
private const val PROCESS_EXIT_TIMEOUT_SECONDS: Long = 5

/**
 * Configuration for launching an ACP agent subprocess over stdio.
 */
public data class StdioAgentConfig(
    /** The command and arguments used to launch the agent. */
    val command: List<String>,
    /** The working directory for the agent process. */
    val workingDirectory: String? = null,
    /** Additional environment variables for the agent process. */
    val environment: Map<String, String> = emptyMap(),
)

/**
 * An [AcpTransport] that spawns an agent subprocess and exchanges
 * newline-delimited JSON over its stdin/stdout.
 */
public class StdioAcpTransport(
    private val config: StdioAgentConfig,
) : AcpTransport {
    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    private val sendMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val queuedChars = AtomicLong()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingChannel = Channel<String>(MAX_INCOMING_FRAMES)

    /** Whether the agent process is alive. */
    public val isAlive: Boolean
        get() = process?.isAlive == true

    override val incoming: Flow<String>
        get() = incomingChannel.receiveAsFlow().onEach { queuedChars.addAndGet(-it.length.toLong()) }

    /**
     * Launches the agent process and begins reading its output.
     */
    public suspend fun start(): Unit =
        withContext(Dispatchers.IO) {
            check(!closed.get()) { "Transport is closed" }
            if (process != null) return@withContext

            logger.info { "Starting ACP agent: ${config.command.joinToString(" ")}" }

            val builder =
                ProcessBuilder(config.command.withResolvedExecutable(config.environment)).apply {
                    config.workingDirectory?.let { directory(File(it)) }
                    environment().putAll(config.environment)
                    redirectErrorStream(false)
                }
            val started = builder.start()
            process = started
            writer = BufferedWriter(OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8))

            scope.launch { readLines(started) }
            scope.launch { drainErrors(started) }
        }

    private suspend fun readLines(process: Process) =
        withContext(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    try {
                        while (isActive) {
                            val line = reader.readBoundedLine() ?: break
                            if (line.isNotBlank()) {
                                logger.debug { "ACP <- message (${line.length} chars)" }
                                val chars = queuedChars.addAndGet(line.length.toLong())
                                if (chars > MAX_INCOMING_CHARS || incomingChannel.trySend(line).isFailure) {
                                    queuedChars.addAndGet(-line.length.toLong())
                                    throw IOException("ACP input backlog exceeded the safety limit")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            logger.error { "ACP output reader stopped (${e::class.simpleName})" }
                            incomingChannel.close(e)
                            process.destroy()
                        }
                    }
                }
            } finally {
                // Signals EOF to the connection so in-flight requests fail instead
                // of waiting for a response that will never arrive.
                incomingChannel.close()
            }
        }

    private suspend fun drainErrors(process: Process) =
        withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null && isActive) {
                    logger.warn { "[ACP stderr] $line" }
                }
            }
        }

    override suspend fun send(line: String): Unit =
        sendMutex.withLock {
            check(!closed.get()) { "Transport is closed" }
            withContext(Dispatchers.IO) {
                val running = process ?: throw IllegalStateException("Transport not started")
                if (!running.isAlive) {
                    // Signal EOF so the connection fails pending requests and can be reset.
                    incomingChannel.close()
                    throw IllegalStateException("ACP agent process is not running")
                }
                val target = writer ?: throw IllegalStateException("Transport not started")
                logger.debug { "ACP -> message (${line.length} chars)" }
                target.write(line)
                target.write("\n")
                target.flush()
            }
        }

    override suspend fun close() {
        // A blocked pipe write may own sendMutex. Terminating the process must not wait for it.
        if (!closed.compareAndSet(false, true)) return

        logger.info { "Closing ACP agent transport" }
        scope.cancel()
        incomingChannel.close()
        withContext(Dispatchers.IO) {
            val running = process
            process = null
            running?.destroy()

            val target = writer
            writer = null
            if (running != null && !running.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.destroyForcibly()
                running.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            runCatching { target?.close() }
        }
    }
}

/** Bounds allocation even when a peer never terminates a line. */
private fun BufferedReader.readBoundedLine(): String? {
    val line = StringBuilder()
    while (true) {
        val char = read()
        if (char < 0) return if (line.isEmpty()) null else line.toString()
        if (char == '\n'.code) return line.toString().removeSuffix("\r")
        if (line.length >= MAX_FRAME_CHARS) throw IOException("ACP frame exceeded the safety limit")
        line.append(char.toChar())
    }
}

private const val MAX_INCOMING_FRAMES = 2_048
private const val MAX_INCOMING_CHARS = 16_777_216L
private const val MAX_FRAME_CHARS = 8_388_608
