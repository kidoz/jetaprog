package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles an inbound request from the peer, returning the JSON result payload.
 *
 * Throw [AcpRequestException] to return a structured JSON-RPC error.
 */
public fun interface AcpRequestHandler {
    /**
     * @param method the request method.
     * @param params the request params, or `null`.
     * @return the JSON result payload.
     */
    public suspend fun handle(
        method: String,
        params: JsonElement?,
    ): JsonElement
}

/**
 * Handles an inbound notification from the peer.
 */
public fun interface AcpNotificationHandler {
    /**
     * @param method the notification method.
     * @param params the notification params, or `null`.
     */
    public suspend fun handle(
        method: String,
        params: JsonElement?,
    )
}

/**
 * A symmetric JSON-RPC 2.0 connection over an [AcpTransport].
 *
 * Correlates outbound requests with their responses, dispatches inbound requests
 * and notifications to the supplied handlers, and is shared by both the client
 * and agent sides of the protocol.
 */
public class AcpConnection(
    private val transport: AcpTransport,
    private val scope: CoroutineScope,
    private val json: Json = defaultJson,
    private val requestHandler: AcpRequestHandler =
        AcpRequestHandler { method, _ ->
            throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "Method not found: $method")
        },
    private val notificationHandler: AcpNotificationHandler = AcpNotificationHandler { _, _ -> },
) {
    private val mutex = Mutex()
    private var nextId = 0
    private val pending = mutableMapOf<String, CompletableDeferred<JsonElement?>>()

    /**
     * Starts listening for inbound messages on the transport.
     */
    public fun start() {
        transport.incoming
            .onEach { line -> dispatch(line) }
            .launchIn(scope)
    }

    /**
     * Sends a request and suspends until the matching response arrives.
     *
     * @return the result payload, or `null` when the peer returned no result.
     * @throws AcpRequestException when the peer responded with an error.
     */
    public suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
    ): JsonElement? {
        val id = mutex.withLock { ++nextId }
        val idElement: JsonElement = JsonPrimitive(id)
        val deferred = CompletableDeferred<JsonElement?>()
        mutex.withLock { pending[idElement.toString()] = deferred }

        transport.send(json.encodeToString(AcpRequest.serializer(), AcpRequest(idElement, method, params)))
        return deferred.await()
    }

    /**
     * Sends a notification, which expects no response.
     */
    public suspend fun sendNotification(
        method: String,
        params: JsonElement? = null,
    ) {
        transport.send(json.encodeToString(AcpNotification.serializer(), AcpNotification(method, params)))
    }

    /**
     * Closes the underlying transport.
     */
    public suspend fun close() {
        transport.close()
    }

    private fun dispatch(line: String) {
        if (line.isBlank()) return
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return

        when {
            obj.containsKey("id") && obj.containsKey("method") -> scope.launch { handleRequest(obj) }
            obj.containsKey("id") -> handleResponse(obj)
            obj.containsKey("method") -> scope.launch { handleNotification(obj) }
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val id = obj["id"] ?: return
        val deferred = pending.remove(id.toString()) ?: return
        val error = obj["error"]
        if (error != null) {
            val decoded = json.decodeFromJsonElement(AcpError.serializer(), error)
            deferred.completeExceptionally(AcpRequestException(decoded.code, decoded.message, decoded.data))
        } else {
            deferred.complete(obj["result"])
        }
    }

    private suspend fun handleRequest(obj: JsonObject) {
        val id = obj["id"] ?: return
        val method = obj["method"]?.jsonPrimitive?.content ?: return
        val params = obj["params"]

        val response =
            try {
                val result = requestHandler.handle(method, params)
                AcpResponse(id = id, result = result)
            } catch (e: AcpRequestException) {
                AcpResponse(id = id, error = AcpError(e.code, e.message, e.data))
            } catch (e: Exception) {
                AcpResponse(id = id, error = AcpError(AcpError.INTERNAL_ERROR, e.message ?: "Internal error"))
            }
        transport.send(json.encodeToString(AcpResponse.serializer(), response))
    }

    private suspend fun handleNotification(obj: JsonObject) {
        val method = obj["method"]?.jsonPrimitive?.content ?: return
        notificationHandler.handle(method, obj["params"])
    }

    public companion object {
        /** The shared, lenient JSON configuration used across ACP. */
        public val defaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }
    }
}
