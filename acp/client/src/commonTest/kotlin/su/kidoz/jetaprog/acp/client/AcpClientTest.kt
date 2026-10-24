package su.kidoz.jetaprog.acp.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A peer using independent JSON fixtures, not the agent implementation or its serializers. */
private class ClientTestTransport(
    private val initializeResult: String = """{"protocolVersion":1,"agentCapabilities":{"loadSession":true}}""",
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<JsonObject>()
    var closed = false

    override val incoming: Flow<String> = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        // A strict peer rejects invalid envelopes, even if both internal endpoints would accept them.
        assertEquals(JsonPrimitive("2.0"), request["jsonrpc"])
        sent += request
        if (request["method"]?.jsonPrimitive?.content == "initialize") respond(initializeResult)
    }

    suspend fun respond(result: String) {
        inbound.send("""{"jsonrpc":"2.0","id":${sent.last().getValue("id")},"result":$result}""")
    }

    suspend fun failPrompt() {
        inbound.send(
            """{"jsonrpc":"2.0","id":${sent.last().getValue("id")},"error":{"code":-32603,"message":"turn failed"}}""",
        )
    }

    suspend fun update(text: String) {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"$text"}}}}""",
        )
    }

    override suspend fun close() {
        closed = true
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AcpClientTest {
    @Test
    fun initializesWithAnIndependentPeer() =
        runTest {
            val transport = ClientTestTransport()
            val client = AcpClient(transport, backgroundScope)

            val response = client.initialize()

            assertEquals(1, response.protocolVersion)
            assertTrue(response.agentCapabilities.loadSession)
            assertEquals(
                JsonPrimitive(1),
                transport.sent
                    .single()
                    .getValue("params")
                    .jsonObject["protocolVersion"],
            )
            assertTrue(client.isOpen)
            client.close()
        }

    @Test
    fun unsupportedProtocolVersionClosesTheConnection() =
        runTest {
            val transport = ClientTestTransport("""{"protocolVersion":2}""")
            val client = AcpClient(transport, backgroundScope)

            val error = assertFailsWith<AcpRequestException> { client.initialize() }

            assertTrue(error.message.contains("Unsupported ACP protocol version 2"))
            assertTrue(transport.closed)
            assertFalse(client.isOpen)
            assertNull(client.initializeResponse)
            assertFailsWith<AcpRequestException> { client.newSession("/workspace") }
        }

    @Test
    fun missingProtocolVersionClosesTheConnection() =
        runTest {
            val transport = ClientTestTransport("{}")
            val client = AcpClient(transport, backgroundScope)

            assertFailsWith<SerializationException> { client.initialize() }

            assertTrue(transport.closed)
            assertFalse(client.isOpen)
            assertNull(client.initializeResponse)
        }

    @Test
    fun burstOfUpdatesIsAppliedBeforePromptCompletesEvenWhenPeerExits() =
        runTest {
            val transport = ClientTestTransport()
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val client = AcpClient(transport, backgroundScope, handler = delayedHandler(gate, received))
            client.initialize()
            val prompt = async { client.promptText("s", "hello") }
            runCurrent()

            repeat(256) { transport.update("chunk-$it") }
            transport.respond("""{"stopReason":"end_turn"}""")
            transport.inbound.close()
            runCurrent()
            assertFalse(prompt.isCompleted)
            assertTrue(received.isEmpty())

            gate.complete(Unit)
            assertEquals(StopReason.END_TURN, prompt.await().stopReason)
            assertEquals(List(256) { "chunk-$it" }, received)
            client.close()
        }

    @Test
    fun errorResponseWaitsForFinalUpdates() =
        runTest {
            val transport = ClientTestTransport()
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val client = AcpClient(transport, backgroundScope, handler = delayedHandler(gate, received))
            client.initialize()
            val prompt = async { runCatching { client.promptText("s", "hello") } }
            runCurrent()

            transport.update("partial answer")
            transport.failPrompt()
            runCurrent()
            assertFalse(prompt.isCompleted)

            gate.complete(Unit)
            assertIs<AcpRequestException>(prompt.await().exceptionOrNull())
            assertEquals(listOf("partial answer"), received)
            client.close()
        }

    @Test
    fun loadWaitsForHistoryReplayAndSendsEmptyMcpServers() =
        runTest {
            val transport = ClientTestTransport()
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val client = AcpClient(transport, backgroundScope, handler = delayedHandler(gate, received))
            client.initialize()
            val load = async { client.loadSession("s", "/workspace") }
            runCurrent()
            assertEquals(
                AcpConnection.defaultJson.parseToJsonElement(
                    """{"sessionId":"s","cwd":"/workspace","mcpServers":[]}""",
                ),
                transport.sent.last()["params"],
            )

            transport.update("previous answer")
            transport.respond("{}")
            runCurrent()
            assertFalse(load.isCompleted)

            gate.complete(Unit)
            load.await()
            assertEquals(listOf("previous answer"), received)
            client.close()
        }

    @Test
    fun blockedOptionalCollectorDoesNotStallPromptAfterBufferFills() =
        runTest {
            val transport = ClientTestTransport()
            val received = mutableListOf<String>()
            val handler =
                object : AcpClientHandler {
                    override suspend fun sessionUpdate(notification: SessionNotification) {
                        val update = assertIs<SessionUpdate.AgentMessageChunk>(notification.update)
                        received += assertIs<ContentBlock.Text>(update.content).text
                    }
                }
            val client = AcpClient(transport, backgroundScope, handler = handler)
            client.initialize()
            val collectorBlocked = CompletableDeferred<Unit>()
            val collectorStarted = CompletableDeferred<Unit>()
            backgroundScope.launch {
                collectorStarted.complete(Unit)
                client.sessionUpdates.collect { collectorBlocked.await() }
            }
            collectorStarted.await()
            runCurrent()
            val prompt = async { client.promptText("s", "hello") }
            runCurrent()

            repeat(128) { transport.update("chunk-$it") }
            transport.respond("""{"stopReason":"end_turn"}""")

            assertEquals(StopReason.END_TURN, prompt.await().stopReason)
            assertEquals(List(128) { "chunk-$it" }, received)
            assertFalse(collectorBlocked.isCompleted)
            client.close()
        }

    private fun delayedHandler(
        gate: CompletableDeferred<Unit>,
        received: MutableList<String>,
    ): AcpClientHandler =
        object : AcpClientHandler {
            override suspend fun sessionUpdate(notification: SessionNotification) {
                gate.await()
                val update = assertIs<SessionUpdate.AgentMessageChunk>(notification.update)
                received += assertIs<ContentBlock.Text>(update.content).text
            }
        }
}
