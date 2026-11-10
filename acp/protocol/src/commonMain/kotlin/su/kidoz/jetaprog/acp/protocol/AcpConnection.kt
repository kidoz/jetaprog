package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
    private val diagnosticHandler: (AcpDiagnostic) -> Unit = {},
) {
    private val mutex = Mutex()
    private val closeMutex = Mutex()
    private var transportClosed = false
    private val requestJob = Job(scope.coroutineContext[Job])
    private val requestScope = CoroutineScope(scope.coroutineContext + requestJob)
    private var notificationJob: Job? = null
    private var incomingJob: Job? = null
    private var nextId = 0
    private var started = false
    private val pending = mutableMapOf<String, CompletableDeferred<IncomingResponse>>()
    private val notifications =
        Channel<IncomingNotification>(MAX_QUEUED_NOTIFICATIONS, onUndeliveredElement = { it.handled.cancel() })
    private val _metrics = MutableStateFlow(AcpConnectionMetrics())

    /** Live payload-free traffic and backlog counters. */
    public val metrics: StateFlow<AcpConnectionMetrics> = _metrics.asStateFlow()

    private var lastNotification: CompletableDeferred<Unit>? = null
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
        if (started || !isOpen) return
        started = true
        // Notifications are consumed sequentially so streamed session updates keep
        // their wire order even when a handler applies backpressure.
        notificationJob =
            scope.launch {
                try {
                    for (notification in notifications) {
                        _metrics.update {
                            it.copy(
                                queuedNotifications = it.queuedNotifications - 1,
                                queuedChars = it.queuedChars - notification.chars,
                            )
                        }
                        try {
                            notificationHandler.handle(notification.method, notification.params)
                            notification.handled.complete(Unit)
                        } catch (e: CancellationException) {
                            notification.handled.cancel(e)
                            throw e
                        } catch (_: Exception) {
                            report(AcpDiagnostic.NOTIFICATION_HANDLER_FAILED)
                            notification.handled.complete(Unit)
                        }
                    }
                } finally {
                    notifications.cancel()
                }
            }
        incomingJob =
            transport.incoming
                .onEach { line -> dispatch(line) }
                .onCompletion { cause ->
                    _isOpen.value = false
                    // A disconnected peer can no longer receive callback results.
                    requestJob.cancel()
                    // Drain already received updates, including those preceding a final
                    // prompt response. Explicit close instead cancels the handlers.
                    notifications.close()
                    // Fail in-flight requests rather than leaving callers suspended forever
                    // when the peer exits or the transport is closed.
                    withContext(NonCancellable) { failAllPending(cause?.message ?: "ACP transport closed") }
                }.catch {
                    // onCompletion already failed pending requests and closed the connection.
                }.launchIn(scope)
    }

    /**
     * Sends a request and suspends until the matching response arrives.
     *
     * The pending entry is removed when the caller is cancelled or the send fails,
     * and is completed exceptionally when the connection closes.
     *
     * @param awaitNotifications wait for notification handlers preceding the response
     *   on the wire to finish. Use for prompt completion and history replay, but not
     *   for requests made inside a notification handler (which would await itself).
     * @return the result payload, or `null` when the peer returned no result.
     * @throws AcpRequestException when the peer responded with an error or the
     *   connection closed before a response arrived.
     */
    public suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
        awaitNotifications: Boolean = false,
    ): JsonElement? {
        val id =
            mutex.withLock {
                checkOpen()
                ++nextId
            }
        val idElement: JsonElement = JsonPrimitive(id)
        val key = idElement.toString()
        val deferred = CompletableDeferred<IncomingResponse>()
        mutex.withLock {
            checkOpen()
            pending[key] = deferred
        }

        try {
            transport.send(json.encodeToString(AcpRequest.serializer(), AcpRequest(idElement, method, params)))
            val response = deferred.await()
            if (awaitNotifications) {
                try {
                    response.notificationsHandled?.await()
                } catch (e: CancellationException) {
                    // A cancelled barrier is a connection failure, not cancellation of
                    // the prompt caller. Preserve genuine caller cancellation separately.
                    currentCoroutineContext().ensureActive()
                    throw AcpRequestException(AcpError.INTERNAL_ERROR, "ACP updates were interrupted before completion")
                }
            }
            return response.result()
        } finally {
            withContext(NonCancellable) { mutex.withLock { pending.remove(key) } }
        }
    }

    /**
     * Sends a notification, which expects no response.
     */
    public suspend fun sendNotification(
        method: String,
        params: JsonElement? = null,
    ) {
        checkOpen()
        transport.send(json.encodeToString(AcpNotification.serializer(), AcpNotification(method, params)))
    }

    /**
     * Closes the underlying transport, fails every in-flight request, and cancels
     * inbound handlers without cancelling the caller-supplied scope.
     *
     * Calling this more than once has no effect. Cleanup also runs when the
     * coroutine calling this method has already been cancelled.
     */
    public suspend fun close() {
        withContext(NonCancellable) {
            closeMutex.withLock {
                if (transportClosed) return@withLock
                _isOpen.value = false
                requestJob.cancel()
                notificationJob?.cancel()
                incomingJob?.cancel()
                notifications.cancel()
                failAllPending("ACP connection closed")
                transportClosed = true
                transport.close()
            }
        }
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
        if (!isOpen || line.isBlank()) return
        _metrics.update {
            it.copy(
                receivedMessages = it.receivedMessages + 1,
                receivedChars =
                    it.receivedChars + line.length,
            )
        }
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
        if (obj == null) {
            report(AcpDiagnostic.MALFORMED_MESSAGE)
            return
        }

        when {
            obj.containsKey("id") && obj.containsKey("method") -> {
                requestScope.launch { handleRequest(obj) }
            }

            obj.containsKey("id") -> {
                handleResponse(obj)
            }

            obj.containsKey("method") -> {
                val method = obj.methodName() ?: return
                if (_metrics.value.queuedChars + line.length > MAX_QUEUED_CHARS ||
                    _metrics.value.queuedNotifications >= MAX_QUEUED_NOTIFICATIONS
                ) {
                    overflow()
                    return
                }
                val notification = IncomingNotification(method, obj["params"], line.length)
                _metrics.update {
                    it.copy(
                        queuedNotifications = it.queuedNotifications + 1,
                        queuedChars = it.queuedChars + line.length,
                        peakQueuedNotifications = maxOf(it.peakQueuedNotifications, it.queuedNotifications + 1),
                    )
                }
                if (notifications.trySend(notification).isFailure) {
                    notification.handled.cancel()
                    overflow()
                } else {
                    lastNotification = notification.handled
                }
            }
        }
    }

    private fun report(diagnostic: AcpDiagnostic) {
        _metrics.update { it.copy(issues = it.issues + 1) }
        diagnosticHandler(diagnostic)
    }

    private suspend fun overflow() {
        report(AcpDiagnostic.INCOMING_OVERFLOW)
        // Never suspend the reader behind notifications: responses and permission requests
        // use this same stream. Fail the connection visibly instead of dropping events.
        failAllPending("ACP notification backlog exceeded the safety limit")
        close()
    }

    private suspend fun handleResponse(obj: JsonObject) {
        val id = obj["id"] ?: return
        val deferred = mutex.withLock { pending.remove(id.toString()) } ?: return
        deferred.complete(IncomingResponse(obj, lastNotification))
    }

    private fun IncomingResponse.result(): JsonElement? {
        val error = payload["error"] ?: return payload["result"]
        val decoded = runCatching { json.decodeFromJsonElement(AcpError.serializer(), error) }.getOrNull()
        if (decoded == null) {
            throw AcpRequestException(AcpError.INTERNAL_ERROR, "Malformed error response")
        }
        throw AcpRequestException(decoded.code, decoded.message, decoded.data)
    }

    private suspend fun handleRequest(obj: JsonObject) {
        val id = obj["id"] ?: return
        val params = obj["params"]

        val response =
            try {
                val method =
                    obj.methodName()
                        ?: throw AcpRequestException(AcpError.INVALID_REQUEST, "Invalid request method")
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

    private fun JsonObject.methodName(): String? = (get("method") as? JsonPrimitive)?.takeIf { it.isString }?.content

    private data class IncomingNotification(
        val method: String,
        val params: JsonElement?,
        val chars: Int,
        val handled: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data class IncomingResponse(
        val payload: JsonObject,
        val notificationsHandled: CompletableDeferred<Unit>?,
    )

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

private const val MAX_QUEUED_NOTIFICATIONS = 2_048
private const val MAX_QUEUED_CHARS = 16_777_216L
