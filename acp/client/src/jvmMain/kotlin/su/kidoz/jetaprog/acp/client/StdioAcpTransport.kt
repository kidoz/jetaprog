package su.kidoz.jetaprog.acp.client

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

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
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingChannel = Channel<String>(Channel.UNLIMITED)

    /** Whether the agent process is alive. */
    public val isAlive: Boolean
        get() = process?.isAlive == true

    override val incoming: Flow<String>
        get() = incomingChannel.receiveAsFlow()

    /**
     * Launches the agent process and begins reading its output.
     */
    public suspend fun start(): Unit =
        withContext(Dispatchers.IO) {
            logger.info { "Starting ACP agent: ${config.command.joinToString(" ")}" }

            val builder =
                ProcessBuilder(config.command).apply {
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
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            logger.debug { "ACP <- $line" }
                            incomingChannel.send(line)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) logger.error(e) { "Error reading ACP agent output" }
                }
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
        withContext(Dispatchers.IO) {
            val target = writer ?: throw IllegalStateException("Transport not started")
            logger.debug { "ACP -> $line" }
            target.write(line)
            target.write("\n")
            target.flush()
        }

    override suspend fun close() {
        logger.info { "Closing ACP agent transport" }
        scope.cancel()
        incomingChannel.close()
        withContext(Dispatchers.IO) {
            try {
                writer?.close()
                process?.destroy()
            } catch (e: Exception) {
                logger.error(e) { "Error closing ACP agent transport" }
            }
        }
    }
}
