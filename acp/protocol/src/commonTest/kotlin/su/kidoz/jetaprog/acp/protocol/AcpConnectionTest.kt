package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A transport whose peer is driven directly by the test. */
private class TestTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var closeCount = 0

    override val incoming: Flow<String> get() = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        sent += line
    }

    override suspend fun close() {
        closeCount++
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AcpConnectionTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun responsesAreCorrelatedWithTheirRequests() =
        runTest {
            val transport = TestTransport()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val connection = AcpConnection(transport, scope)
            try {
                connection.start()

                val first = async { connection.sendRequest("first") }
                val second = async { connection.sendRequest("second") }
                runCurrent()

                val requests = transport.sent.map { json.parseToJsonElement(it).jsonObject }
                val idsByMethod =
                    requests.associate { request ->
                        request.getValue("method").jsonPrimitive.content to request.getValue("id")
                    }
                assertEquals(setOf("first", "second"), idsByMethod.keys)

                // Answer in reverse order to prove correlation does not depend on arrival order.
                respond(transport, idsByMethod.getValue("second"), "second-result")
                respond(transport, idsByMethod.getValue("first"), "first-result")
                advanceUntilIdle()

                assertEquals(JsonPrimitive("first-result"), first.await())
                assertEquals(JsonPrimitive("second-result"), second.await())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun errorResponseIsDeliveredAsAnException() =
        runTest {
            val transport = TestTransport()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val connection = AcpConnection(transport, scope)
            try {
                connection.start()
                val pending = async { runCatching { connection.sendRequest("fail") } }
                runCurrent()

                val id = requestId(transport)
                transport.inbound.send(
                    json.encodeToString(
                        AcpResponse.serializer(),
                        AcpResponse(id = id, error = AcpError(AcpError.INVALID_PARAMS, "bad params")),
                    ),
                )
                advanceUntilIdle()

                val failure = pending.await().exceptionOrNull()
                val exception = assertIs<AcpRequestException>(failure)
                assertEquals(AcpError.INVALID_PARAMS, exception.code)
                assertEquals("bad params", exception.message)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun closingFailsPendingRequests() =
        runTest {
            val transport = TestTransport()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val connection = AcpConnection(transport, scope)
            try {
                connection.start()
                val pending = async { runCatching { connection.sendRequest("pending") } }
                runCurrent()

                connection.close()
                advanceUntilIdle()

                val failure = pending.await().exceptionOrNull()
                assertIs<AcpRequestException>(failure)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun transportCompletionFailsPendingRequests() =
        runTest {
            val transport = TestTransport()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val connection = AcpConnection(transport, scope)
            try {
                connection.start()
                val pending = async { runCatching { connection.sendRequest("pending") } }
                runCurrent()

                // Simulate the agent process exiting: the inbound flow completes.
                transport.inbound.close()
                advanceUntilIdle()

                val failure = pending.await().exceptionOrNull()
                assertIs<AcpRequestException>(failure)
                assertTrue(!connection.isOpen)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun notificationsAreDeliveredInWireOrder() =
        runTest {
            val transport = TestTransport()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val received = mutableListOf<String>()
            val firstGate = CompletableDeferred<Unit>()
            val connection =
                AcpConnection(
                    transport = transport,
                    scope = scope,
                    notificationHandler =
                        AcpNotificationHandler { method, _ ->
                            if (method == "first") firstGate.await()
                            received += method
                        },
                )
            try {
                connection.start()

                transport.inbound.send(notification("first"))
                transport.inbound.send(notification("second"))
                runCurrent()

                // The first handler is suspended; the second must wait its turn.
                assertEquals(emptyList(), received)

                firstGate.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf("first", "second"), received)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun responseWaitsOnlyForNotificationsThatPrecedeIt() =
        runTest {
            val transport = TestTransport()
            val firstGate = CompletableDeferred<Unit>()
            val laterGate = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    notificationHandler =
                        AcpNotificationHandler { method, _ ->
                            if (method == "first") firstGate.await() else laterGate.await()
                            received += method
                        },
                )
            connection.start()
            val request = async { connection.sendRequest("prompt", awaitNotifications = true) }
            runCurrent()

            transport.inbound.send(notification("first"))
            respond(transport, requestId(transport), "done")
            transport.inbound.send(notification("later"))
            runCurrent()
            assertFalse(request.isCompleted)

            firstGate.complete(Unit)
            assertEquals(JsonPrimitive("done"), request.await())
            assertEquals(listOf("first"), received)
            laterGate.complete(Unit)
            connection.close()
        }

    @Test
    fun notificationHandlerCanMakeRequestsWhilePromptWaitsForIt() =
        runTest {
            val transport = TestTransport()
            lateinit var connection: AcpConnection
            val callbackResult = CompletableDeferred<JsonElement?>()
            connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    notificationHandler =
                        AcpNotificationHandler { _, _ ->
                            callbackResult.complete(connection.sendRequest("nested"))
                        },
                )
            connection.start()
            val request = async { connection.sendRequest("prompt", awaitNotifications = true) }
            runCurrent()
            val promptId = requestId(transport)

            transport.inbound.send(notification("update"))
            respond(transport, promptId, "done")
            runCurrent()
            assertFalse(request.isCompleted)

            val nestedId = json.parseToJsonElement(transport.sent.last()).jsonObject.getValue("id")
            respond(transport, nestedId, "nested-result")
            assertEquals(JsonPrimitive("done"), request.await())
            assertEquals(JsonPrimitive("nested-result"), callbackResult.await())
            connection.close()
        }

    @Test
    fun closingCancelsInboundRequestsWithoutCancellingTheOwner() =
        runTest {
            val transport = TestTransport()
            val entered = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    requestHandler =
                        AcpRequestHandler { _, _ ->
                            entered.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                stopped.complete(Unit)
                            }
                        },
                )
            connection.start()
            transport.inbound.send("""{"jsonrpc":"2.0","id":"permission","method":"permission"}""")
            entered.await()

            connection.close()
            runCurrent()

            assertTrue(stopped.isCompleted)
            assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
            assertTrue(transport.sent.isEmpty())
        }

    @Test
    fun transportCompletionCancelsInboundRequests() =
        runTest {
            val transport = TestTransport()
            val entered = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    requestHandler =
                        AcpRequestHandler { _, _ ->
                            entered.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                stopped.complete(Unit)
                            }
                        },
                )
            connection.start()
            transport.inbound.send("""{"jsonrpc":"2.0","id":1,"method":"wait"}""")
            entered.await()

            transport.inbound.close()
            runCurrent()

            assertTrue(stopped.isCompleted)
            assertFalse(connection.isOpen)
            connection.close()
        }

    @Test
    fun closingAbortsBlockedNotificationsAndReleasesResponseWaiters() =
        runTest {
            val transport = TestTransport()
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    notificationHandler =
                        AcpNotificationHandler { method, _ ->
                            gate.await()
                            received += method
                        },
                )
            connection.start()
            val prompt = async { runCatching { connection.sendRequest("prompt", awaitNotifications = true) } }
            runCurrent()
            transport.inbound.send(notification("first"))
            transport.inbound.send(notification("queued"))
            respond(transport, requestId(transport), "done")
            runCurrent()
            assertFalse(prompt.isCompleted)

            connection.close()
            runCurrent()

            assertTrue(prompt.isCompleted)
            assertTrue(prompt.await().isFailure)
            gate.complete(Unit)
            runCurrent()
            assertTrue(received.isEmpty())
        }

    @Test
    fun closedConnectionsRejectNotifications() =
        runTest {
            val transport = TestTransport()
            val connection = AcpConnection(transport, backgroundScope)
            connection.close()

            assertFailsWith<AcpRequestException> { connection.sendNotification("cancel") }
            assertTrue(transport.sent.isEmpty())
        }

    @Test
    fun cancelledCallersCanCloseTheConnectionExactlyOnce() =
        runTest {
            val transport = TestTransport()
            val connection = AcpConnection(transport, backgroundScope)
            connection.start()
            val pending = async { runCatching { connection.sendRequest("pending") } }
            runCurrent()

            val closer =
                launch {
                    coroutineContext[Job]!!.cancel()
                    connection.close()
                }
            closer.join()
            connection.close()

            assertFalse(connection.isOpen)
            assertIs<AcpRequestException>(pending.await().exceptionOrNull())
            assertEquals(1, transport.closeCount)
        }

    @Test
    fun malformedNotificationMethodsDoNotInterruptResponses() =
        runTest {
            val transport = TestTransport()
            val received = mutableListOf<String>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    notificationHandler = AcpNotificationHandler { method, _ -> received += method },
                )
            connection.start()
            val pending = async { runCatching { connection.sendRequest("prompt", awaitNotifications = true) } }
            runCurrent()
            for (method in listOf("{}", "[]", "null", "42", "true")) {
                transport.inbound.send("""{"jsonrpc":"2.0","method":$method}""")
            }
            transport.inbound.send(notification("valid"))
            respond(transport, requestId(transport), "done")

            assertEquals(JsonPrimitive("done"), pending.await().getOrThrow())
            assertEquals(listOf("valid"), received)
            assertTrue(connection.isOpen)
            connection.close()
        }

    @Test
    fun malformedRequestMethodsReturnErrorsWithoutCallingTheHandler() =
        runTest {
            val transport = TestTransport()
            val received = mutableListOf<String>()
            val connection =
                AcpConnection(
                    transport,
                    backgroundScope,
                    requestHandler =
                        AcpRequestHandler { method, _ ->
                            received += method
                            JsonPrimitive("accepted")
                        },
                )
            connection.start()
            val methods = listOf("{}", "[]", "null", "42", "true")
            methods.forEachIndexed { index, method ->
                transport.inbound.send("""{"jsonrpc":"2.0","id":"bad-$index","method":$method}""")
            }
            transport.inbound.send("""{"jsonrpc":"2.0","id":"good","method":"valid"}""")
            runCurrent()

            val responses = transport.sent.map { json.decodeFromString(AcpResponse.serializer(), it) }
            assertEquals(methods.size + 1, responses.size)
            methods.indices.forEach { index ->
                val response = responses.single { it.id == JsonPrimitive("bad-$index") }
                assertEquals(AcpError.INVALID_REQUEST, response.error?.code)
            }
            assertEquals(JsonPrimitive("accepted"), responses.single { it.id == JsonPrimitive("good") }.result)
            assertEquals(listOf("valid"), received)
            assertTrue(connection.isOpen)
            connection.close()
        }

    private fun notification(method: String): String =
        json.encodeToString(AcpNotification.serializer(), AcpNotification(method = method))

    private fun requestId(transport: TestTransport): JsonElement =
        json.parseToJsonElement(transport.sent.single()).jsonObject.getValue("id")

    private suspend fun respond(
        transport: TestTransport,
        id: JsonElement,
        value: String,
    ) {
        transport.inbound.send(
            json.encodeToString(
                AcpResponse.serializer(),
                AcpResponse(id = id, result = JsonPrimitive(value)),
            ),
        )
    }
}
