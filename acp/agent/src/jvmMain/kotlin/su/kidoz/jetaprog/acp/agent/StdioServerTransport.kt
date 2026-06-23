package su.kidoz.jetaprog.acp.agent

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
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * An [AcpTransport] for the agent side that reads newline-delimited JSON from an
 * [InputStream] and writes to an [OutputStream].
 *
 * When launched as a subprocess by an editor, pass `System.in`/`System.out`.
 */
public class StdioServerTransport(
    input: InputStream,
    output: OutputStream,
) : AcpTransport {
    private val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
    private val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingChannel = Channel<String>(Channel.UNLIMITED)

    override val incoming: Flow<String>
        get() = incomingChannel.receiveAsFlow()

    /**
     * Begins reading inbound lines.
     */
    public fun start() {
        scope.launch { readLines() }
    }

    private suspend fun readLines() =
        withContext(Dispatchers.IO) {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        logger.debug { "ACP <- $line" }
                        incomingChannel.send(line)
                    }
                }
            } catch (e: Exception) {
                if (isActive) logger.error(e) { "Error reading ACP client input" }
            }
        }

    override suspend fun send(line: String): Unit =
        withContext(Dispatchers.IO) {
            logger.debug { "ACP -> $line" }
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }

    override suspend fun close() {
        scope.cancel()
        incomingChannel.close()
        withContext(Dispatchers.IO) {
            try {
                writer.close()
                reader.close()
            } catch (e: Exception) {
                logger.error(e) { "Error closing ACP server transport" }
            }
        }
    }
}
