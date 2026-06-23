package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AgentCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An in-memory transport whose outbound lines feed another transport's inbound channel. */
private class InMemoryTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    var peer: InMemoryTransport? = null

    override val incoming: Flow<String> get() = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        peer?.inbound?.send(line)
    }

    override suspend fun close() {
        inbound.close()
    }
}

private fun linkedTransports(): Pair<InMemoryTransport, InMemoryTransport> {
    val a = InMemoryTransport()
    val b = InMemoryTransport()
    a.peer = b
    b.peer = a
    return a to b
}

/** A trivial agent that echoes the prompt back as a streamed message. */
private class EchoAgent : AcpAgent {
    override suspend fun initialize(request: InitializeRequest): InitializeResponse =
        InitializeResponse(
            agentCapabilities = AgentCapabilities(loadSession = false),
            agentInfo = Implementation("echo-agent", "1.0"),
        )

    override suspend fun newSession(request: NewSessionRequest): NewSessionResponse =
        NewSessionResponse(sessionId = "session-1")

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse {
        val text = request.prompt.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
        session.sendMessageChunk("echo: $text")
        return PromptResponse(StopReason.END_TURN)
    }
}

class AcpRoundTripTest {
    @Test
    fun clientDrivesAgentEndToEnd() =
        runTest {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val (clientTransport, agentTransport) = linkedTransports()

            val server = AcpAgentServer(EchoAgent(), agentTransport, scope)
            server.start()

            val client = AcpClient(clientTransport, scope, clientInfo = Implementation("test-client", "1.0"))

            val initialize = client.initialize()
            assertEquals("echo-agent", initialize.agentInfo?.name)

            val session = client.newSession(cwd = "/tmp/project")
            assertEquals("session-1", session.sessionId)

            val received = CompletableDeferred<String>()
            scope.launch {
                val notification = client.sessionUpdates.first()
                val update = notification.update
                if (update is SessionUpdate.AgentMessageChunk) {
                    val content = update.content
                    if (content is ContentBlock.Text) received.complete(content.text)
                }
            }
            // Ensure the collector is subscribed before the agent emits the chunk.
            testScheduler.runCurrent()

            val response = client.promptText(session.sessionId, "hello")
            assertEquals(StopReason.END_TURN, response.stopReason)
            assertTrue(received.await().contains("echo: hello"))

            scope.cancel()
        }
}
