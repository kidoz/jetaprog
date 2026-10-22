package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private var started = false
    private val pending = mutableMapOf<String, CompletableDeferred<JsonElement?>>()
    private val notifications = Channel<Pair<String, JsonElement?>>(Channel.UNLIMITED)
    private val _isOpen = MutableStateFlow(true)

    /**
     * Whether the connection is still able to carry requests.
     *
     * Changes to `false` when [close] is called or when the transport's inbound
     * flow completes (for example, because the agent process exited).
     */
    public val isOpen: Boolean
        get() = _isOpen.value

    /**
     * Starts listening for inbound messages on the transport.
     *
     * Calling this more than once has no effect.
     */
    public fun start() {
        if (started) return
        started = true
        // Notifications are consumed sequentially so streamed session updates keep
        // their wire order even when a handler applies backpressure.
        scope.launch {
            for ((method, params) in notifications) {
                try {
                    notificationHandler.handle(method, params)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A malformed update must not stop subsequent notifications.
                }
            }
        }
        transport.incoming
            .onEach { line -> dispatch(line) }
            .onCompletion { cause ->
                _isOpen.value = false
                notifications.close()
                // Fail in-flight requests rather than leaving callers suspended forever
                // when the peer exits or the transport is closed.
                withContext(NonCancellable) { failAllPending(cause?.message ?: "ACP transport closed") }
            }.launchIn(scope)
    }

    /**
     * Sends a request and suspends until the matching response arrives.
     *
     * The pending entry is removed when the caller is cancelled or the send fails,
     * and is completed exceptionally when the connection closes.
     *
     * @return the result payload, or `null` when the peer returned no result.
     * @throws AcpRequestException when the peer responded with an error or the
     *   connection closed before a response arrived.
     */
    public suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
    ): JsonElement? {
        val id =
            mutex.withLock {
                checkOpen()
                ++nextId
            }
        val idElement: JsonElement = JsonPrimitive(id)
        val key = idElement.toString()
        val deferred = CompletableDeferred<JsonElement?>()
        mutex.withLock {
            checkOpen()
            pending[key] = deferred
        }

        try {
            transport.send(json.encodeToString(AcpRequest.serializer(), AcpRequest(idElement, method, params)))
            return deferred.await()
        } finally {
            mutex.withLock { pending.remove(key) }
        }
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
     * Closes the underlying transport and fails every in-flight request.
     */
    public suspend fun close() {
        _isOpen.value = false
        notifications.close()
        failAllPending("ACP connection closed")
        transport.close()
    }

    private fun checkOpen() {
        if (!_isOpen.value) {
            throw AcpRequestException(AcpError.INTERNAL_ERROR, "ACP connection is closed")
        }
    }

    private suspend fun failAllPending(message: String) {
        val failures =
            mutex.withLock {
                pending.values.toList().also { pending.clear() }
            }
        failures.forEach { deferred ->
            deferred.completeExceptionally(AcpRequestException(AcpError.INTERNAL_ERROR, message))
        }
    }

    private suspend fun dispatch(line: String) {
        if (line.isBlank()) return
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return

        when {
            obj.containsKey("id") && obj.containsKey("method") -> {
                scope.launch { handleRequest(obj) }
            }

            obj.containsKey("id") -> {
                handleResponse(obj)
            }

            obj.containsKey("method") -> {
                val method = obj["method"]?.jsonPrimitive?.content ?: return
                notifications.trySend(method to obj["params"])
            }
        }
    }

    private suspend fun handleResponse(obj: JsonObject) {
        val id = obj["id"] ?: return
        val deferred = mutex.withLock { pending.remove(id.toString()) } ?: return
        val error = obj["error"]
        if (error != null) {
            val decoded = runCatching { json.decodeFromJsonElement(AcpError.serializer(), error) }.getOrNull()
            if (decoded == null) {
                deferred.completeExceptionally(AcpRequestException(AcpError.INTERNAL_ERROR, "Malformed error response"))
            } else {
                deferred.completeExceptionally(AcpRequestException(decoded.code, decoded.message, decoded.data))
            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: AcpRequestException) {
                AcpResponse(id = id, error = AcpError(e.code, e.message, e.data))
            } catch (e: Exception) {
                AcpResponse(id = id, error = AcpError(AcpError.INTERNAL_ERROR, e.message ?: "Internal error"))
            }
        transport.send(json.encodeToString(AcpResponse.serializer(), response))
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
