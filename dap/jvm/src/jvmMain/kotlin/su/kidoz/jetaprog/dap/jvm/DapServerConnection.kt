package su.kidoz.jetaprog.dap.jvm

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.dap.protocol.DapEvent
import su.kidoz.jetaprog.dap.protocol.DapRequest
import su.kidoz.jetaprog.dap.protocol.DapResponse
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server side of a DAP connection: reads requests from [input] and writes
 * responses and events to [output] using the DAP base protocol
 * (Content-Length framed JSON messages).
 */
public class DapServerConnection(
    private val input: InputStream,
    private val output: OutputStream,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val sequenceNumber = AtomicInteger(0)
    private val writeLock = Any()

    /**
     * Reads the next request from the client.
     *
     * @return The request, or null when the stream is closed.
     */
    public fun readRequest(): DapRequest? {
        while (true) {
            val content = readMessage() ?: return null
            val message = json.parseToJsonElement(content).jsonObject
            if (message["type"]?.jsonPrimitive?.content != "request") continue
            return DapRequest(
                seq = message["seq"]?.jsonPrimitive?.int ?: 0,
                command = message["command"]?.jsonPrimitive?.content ?: "",
                arguments = message["arguments"],
            )
        }
    }

    /**
     * Sends a successful response to [request] with an optional typed [body].
     */
    public fun <T> sendResponse(
        request: DapRequest,
        serializer: SerializationStrategy<T>,
        body: T,
    ) {
        sendResponseElement(request, success = true, message = null, body = json.encodeToJsonElement(serializer, body))
    }

    /**
     * Sends a successful response to [request] without a body.
     */
    public fun sendResponse(request: DapRequest) {
        sendResponseElement(request, success = true, message = null, body = null)
    }

    /**
     * Sends an error response to [request].
     */
    public fun sendErrorResponse(
        request: DapRequest,
        message: String,
    ) {
        sendResponseElement(request, success = false, message = message, body = null)
    }

    /**
     * Sends an event with a typed [body].
     */
    public fun <T> sendEvent(
        event: String,
        serializer: SerializationStrategy<T>,
        body: T,
    ) {
        sendEventElement(event, json.encodeToJsonElement(serializer, body))
    }

    /**
     * Sends an event without a body.
     */
    public fun sendEvent(event: String) {
        sendEventElement(event, body = null)
    }

    private fun sendResponseElement(
        request: DapRequest,
        success: Boolean,
        message: String?,
        body: JsonElement?,
    ) {
        val response =
            DapResponse(
                seq = sequenceNumber.incrementAndGet(),
                requestSeq = request.seq,
                success = success,
                command = request.command,
                message = message,
                body = body,
            )
        writeMessage(json.encodeToString(DapResponse.serializer(), response))
    }

    private fun sendEventElement(
        event: String,
        body: JsonElement?,
    ) {
        val message =
            DapEvent(
                seq = sequenceNumber.incrementAndGet(),
                event = event,
                body = body,
            )
        writeMessage(json.encodeToString(DapEvent.serializer(), message))
    }

    private fun writeMessage(content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            output.write(header)
            output.write(bytes)
            output.flush()
        }
    }

    private fun readMessage(): String? {
        var contentLength = -1
        while (true) {
            val line = readHeaderLine() ?: return null
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
            }
        }
        if (contentLength <= 0) return null

        val buffer = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = input.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) return null
            totalRead += read
        }
        return String(buffer, Charsets.UTF_8)
    }

    private fun readHeaderLine(): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return null
            val char = byte.toChar()
            if (char == '\n') break
            if (char != '\r') line.append(char)
        }
        return line.toString()
    }
}
