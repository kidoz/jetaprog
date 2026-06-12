package su.kidoz.jetaprog.lsp.client.transport

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.lsp.protocol.JsonRpcMessage
import su.kidoz.jetaprog.lsp.protocol.JsonRpcNotification
import su.kidoz.jetaprog.lsp.protocol.JsonRpcRequest
import su.kidoz.jetaprog.lsp.protocol.JsonRpcResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Standard I/O transport for LSP communication via process stdin/stdout.
 */
public class StdioTransport(
    private val config: TransportConfig.Stdio,
) : LspTransport {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingChannel = Channel<JsonRpcMessage>(Channel.UNLIMITED)

    override val isConnected: Boolean
        get() = process?.isAlive == true

    override val incoming: Flow<JsonRpcMessage>
        get() = incomingChannel.receiveAsFlow()

    /**
     * Start the language server process.
     */
    public suspend fun start(): Unit =
        withContext(Dispatchers.IO) {
            logger.info { "Starting language server: ${config.command.joinToString(" ")}" }

            val processBuilder =
                ProcessBuilder(config.command).apply {
                    config.workingDirectory?.let { directory(File(it)) }
                    environment().putAll(config.environment)
                    redirectErrorStream(false)
                }

            process = processBuilder.start()

            reader =
                BufferedReader(
                    InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8),
                )
            writer =
                BufferedWriter(
                    OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8),
                )

            // Start reading messages
            scope.launch {
                readMessages()
            }

            // Log stderr
            scope.launch {
                BufferedReader(
                    InputStreamReader(process!!.errorStream, StandardCharsets.UTF_8),
                ).use { errorReader ->
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null && isActive) {
                        logger.warn { "[LSP stderr] $line" }
                    }
                }
            }

            logger.info { "Language server started" }
        }

    private suspend fun readMessages() =
        withContext(Dispatchers.IO) {
            try {
                while (isActive && process?.isAlive == true) {
                    val message = readMessage()
                    if (message != null) {
                        incomingChannel.send(message)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logger.error(e) { "Error reading LSP message" }
                }
            }
        }

    private fun readMessage(): JsonRpcMessage? {
        val reader = reader ?: return null

        // Read headers
        val headers = mutableMapOf<String, String>()
        var line: String?

        while (true) {
            line = reader.readLine() ?: return null
            if (line.isEmpty()) break

            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        // Get content length
        val contentLength =
            headers["Content-Length"]?.toIntOrNull()
                ?: throw IllegalStateException("Missing Content-Length header")

        // Read content
        val buffer = CharArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = reader.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) throw IllegalStateException("Unexpected end of stream")
            totalRead += read
        }

        val content = String(buffer)
        logger.debug { "Received: $content" }

        return parseMessage(content)
    }

    private fun parseMessage(content: String): JsonRpcMessage {
        val jsonElement = json.parseToJsonElement(content)
        val jsonObject = jsonElement.jsonObject

        return when {
            // Response (has id but no method)
            jsonObject.containsKey("id") && !jsonObject.containsKey("method") -> {
                val id = jsonObject["id"]!!.jsonPrimitive.int
                val result = jsonObject["result"]
                val error =
                    jsonObject["error"]?.let { errorJson ->
                        json.decodeFromJsonElement(
                            su.kidoz.jetaprog.lsp.protocol.JsonRpcError
                                .serializer(),
                            errorJson,
                        )
                    }
                JsonRpcResponse(id, result, error)
            }

            // Request (has id and method)
            jsonObject.containsKey("id") && jsonObject.containsKey("method") -> {
                val id = jsonObject["id"]!!.jsonPrimitive.int
                val method = jsonObject["method"]!!.jsonPrimitive.content
                val params = jsonObject["params"]
                JsonRpcRequest(id, method, params)
            }

            // Notification (has method but no id)
            jsonObject.containsKey("method") -> {
                val method = jsonObject["method"]!!.jsonPrimitive.content
                val params = jsonObject["params"]
                JsonRpcNotification(method, params)
            }

            else -> {
                throw IllegalArgumentException("Unknown message format: $content")
            }
        }
    }

    override suspend fun send(message: JsonRpcMessage): Unit =
        withContext(Dispatchers.IO) {
            val writer = writer ?: throw IllegalStateException("Transport not started")

            val content = encodeMessage(message)
            logger.debug { "Sending: $content" }

            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"

            writer.write(header)
            writer.write(content)
            writer.flush()
        }

    private fun encodeMessage(message: JsonRpcMessage): String {
        val obj =
            when (message) {
                is JsonRpcRequest -> {
                    JsonObject(
                        buildMap {
                            put("jsonrpc", JsonPrimitive("2.0"))
                            put("id", JsonPrimitive(message.id))
                            put("method", JsonPrimitive(message.method))
                            message.params?.let { put("params", it) }
                        },
                    )
                }

                is JsonRpcResponse -> {
                    JsonObject(
                        buildMap {
                            put("jsonrpc", JsonPrimitive("2.0"))
                            put("id", JsonPrimitive(message.id))
                            message.result?.let { put("result", it) }
                            message.error?.let { errorObj ->
                                put(
                                    "error",
                                    json.encodeToJsonElement(
                                        su.kidoz.jetaprog.lsp.protocol.JsonRpcError
                                            .serializer(),
                                        errorObj,
                                    ),
                                )
                            }
                        },
                    )
                }

                is JsonRpcNotification -> {
                    JsonObject(
                        buildMap {
                            put("jsonrpc", JsonPrimitive("2.0"))
                            put("method", JsonPrimitive(message.method))
                            message.params?.let { put("params", it) }
                        },
                    )
                }
            }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    override suspend fun close() {
        logger.info { "Closing language server transport" }
        scope.cancel()
        incomingChannel.close()

        withContext(Dispatchers.IO) {
            try {
                writer?.close()
                reader?.close()
                process?.destroyForcibly()
            } catch (e: Exception) {
                logger.error(e) { "Error closing transport" }
            }
        }
    }
}
