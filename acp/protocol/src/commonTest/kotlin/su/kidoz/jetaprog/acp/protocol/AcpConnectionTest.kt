package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A transport whose peer is driven directly by the test. */
private class TestTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()

    override val incoming: Flow<String> get() = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        sent += line
    }

    override suspend fun close() {
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
