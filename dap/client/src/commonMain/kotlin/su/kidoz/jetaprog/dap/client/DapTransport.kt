package su.kidoz.jetaprog.dap.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.dap.protocol.DapEvent
import su.kidoz.jetaprog.dap.protocol.DapRequest
import su.kidoz.jetaprog.dap.protocol.DapResponse
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream

/**
 * Transport layer for DAP protocol communication.
 *
 * Handles reading and writing DAP messages over stdin/stdout streams
 * using the DAP base protocol (Content-Length headers).
 */
public class DapTransport(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val scope: CoroutineScope,
) : Disposable {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

    private val writeMutex = Mutex()
    private val _events = MutableSharedFlow<DapEvent>(replay = 10)
    private val _responses = Channel<DapResponse>(Channel.UNLIMITED)

    private var readerJob: Job? = null
    private var disposed = false

    /**
     * Flow of events received from the debug adapter.
     */
    public val events: Flow<DapEvent> = _events.asSharedFlow()

    /**
     * Flow of responses received from the debug adapter.
     */
    public val responses: Flow<DapResponse> = _responses.receiveAsFlow()

    /**
     * Start the transport reader.
     */
    public fun start() {
        check(!disposed) { "Transport has been disposed" }
        check(readerJob == null) { "Transport already started" }

        readerJob =
            scope.launch(Dispatchers.IO) {
                val reader = inputStream.bufferedReader()
                try {
                    while (isActive) {
                        val message = readMessage(reader) ?: break
                        handleMessage(message)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        // Log error but don't crash
                        System.err.println("DapTransport read error: ${e.message}")
                    }
                }
            }
    }

    /**
     * Send a request to the debug adapter.
     */
    public suspend fun sendRequest(request: DapRequest) {
        check(!disposed) { "Transport has been disposed" }
        val content = json.encodeToString(request)
        writeMessage(content)
    }

    private suspend fun writeMessage(content: String) {
        writeMutex.withLock {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            outputStream.write(header.toByteArray(Charsets.UTF_8))
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    private fun readMessage(reader: BufferedReader): JsonObject? {
        // Read headers
        var contentLength = -1
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) break

            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
            }
        }

        if (contentLength <= 0) return null

        // Read body
        val buffer = CharArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = reader.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) return null
            totalRead += read
        }

        val content = String(buffer)
        return json.decodeFromString<JsonObject>(content)
    }

    private suspend fun handleMessage(message: JsonObject) {
        val type = message["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "response" -> {
                val response = parseResponse(message)
                _responses.send(response)
            }

            "event" -> {
                val event = parseEvent(message)
                _events.emit(event)
            }
            // Ignore requests from adapter (reverse requests) for now
        }
    }

    private fun parseResponse(message: JsonObject): DapResponse =
        DapResponse(
            seq = message["seq"]?.jsonPrimitive?.int ?: 0,
            type = "response",
            requestSeq = message["request_seq"]?.jsonPrimitive?.int ?: 0,
            success = message["success"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            command = message["command"]?.jsonPrimitive?.content ?: "",
            message = message["message"]?.jsonPrimitive?.content,
            body = message["body"],
        )

    private fun parseEvent(message: JsonObject): DapEvent =
        DapEvent(
            seq = message["seq"]?.jsonPrimitive?.int ?: 0,
            type = "event",
            event = message["event"]?.jsonPrimitive?.content ?: "",
            body = message["body"],
        )

    override fun dispose() {
        if (disposed) return
        disposed = true

        scope.launch {
            readerJob?.cancelAndJoin()
        }
        _responses.close()

        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
    }
}

/**
 * Result of waiting for a response.
 */
public sealed interface TransportResult<T> {
    /**
     * Response received successfully.
     */
    public data class Success<T>(
        val value: T,
    ) : TransportResult<T>

    /**
     * Error occurred.
     */
    public data class Error<T>(
        val message: String,
    ) : TransportResult<T>

    /**
     * Timeout waiting for response.
     */
    public class Timeout<T> : TransportResult<T>
}
