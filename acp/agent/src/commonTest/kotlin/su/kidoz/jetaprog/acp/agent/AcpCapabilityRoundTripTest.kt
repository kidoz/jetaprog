package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AgentCapabilities
import su.kidoz.jetaprog.acp.protocol.AuthenticateRequest
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.CreateTerminalRequest
import su.kidoz.jetaprog.acp.protocol.CreateTerminalResponse
import su.kidoz.jetaprog.acp.protocol.EnvVariable
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.KillTerminalRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.ReleaseTerminalRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionModelRequest
import su.kidoz.jetaprog.acp.protocol.StopReason
import su.kidoz.jetaprog.acp.protocol.TerminalExitStatus
import su.kidoz.jetaprog.acp.protocol.TerminalOutputRequest
import su.kidoz.jetaprog.acp.protocol.TerminalOutputResponse
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitRequest
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitResponse
import kotlin.test.Test
import kotlin.test.assertEquals

private class CapabilityTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    var peer: CapabilityTransport? = null

    override val incoming: Flow<String> get() = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        peer?.inbound?.send(line)
    }

    override suspend fun close() {
        inbound.close()
    }
}

private fun linkedCapabilityTransports(): Pair<CapabilityTransport, CapabilityTransport> {
    val client = CapabilityTransport()
    val agent = CapabilityTransport()
    client.peer = agent
    agent.peer = client
    return client to agent
}

private open class CapabilityAgent : AcpAgent {
    var authenticateRequest: AuthenticateRequest? = null
    var setModelRequest: SetSessionModelRequest? = null

    override suspend fun initialize(request: InitializeRequest): InitializeResponse =
        InitializeResponse(agentCapabilities = AgentCapabilities())

    override suspend fun authenticate(request: AuthenticateRequest) {
        authenticateRequest = request
    }

    override suspend fun newSession(request: NewSessionRequest): NewSessionResponse =
        NewSessionResponse(sessionId = "session-1")

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse = PromptResponse(StopReason.END_TURN)

    override suspend fun setModel(request: SetSessionModelRequest) {
        setModelRequest = request
    }
}

private class TerminalAgent : CapabilityAgent() {
    var outputResponse: TerminalOutputResponse? = null
    var exitResponse: WaitForTerminalExitResponse? = null

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse {
        val created =
            session.createTerminal(
                command = "echo",
                args = listOf("hello"),
                cwd = "/tmp/project",
                env = listOf(EnvVariable("MODE", "test")),
                outputByteLimit = 1024,
            )
        outputResponse = session.terminalOutput(created.terminalId)
        exitResponse = session.waitForTerminalExit(created.terminalId)
        session.killTerminal(created.terminalId)
        session.releaseTerminal(created.terminalId)
        return PromptResponse(StopReason.END_TURN)
    }
}

private class RecordingTerminalHandler : AcpClientHandler {
    val requests = mutableListOf<Any>()

    override suspend fun createTerminal(request: CreateTerminalRequest): CreateTerminalResponse {
        requests += request
        return CreateTerminalResponse("terminal-1")
    }

    override suspend fun terminalOutput(request: TerminalOutputRequest): TerminalOutputResponse {
        requests += request
        return TerminalOutputResponse(
            output = "hello\n",
            truncated = true,
            exitStatus = TerminalExitStatus(exitCode = 0),
        )
    }

    override suspend fun waitForTerminalExit(request: WaitForTerminalExitRequest): WaitForTerminalExitResponse {
        requests += request
        return WaitForTerminalExitResponse(exitCode = 0)
    }

    override suspend fun killTerminal(request: KillTerminalRequest) {
        requests += request
    }

    override suspend fun releaseTerminal(request: ReleaseTerminalRequest) {
        requests += request
    }
}

class AcpCapabilityRoundTripTest {
    @Test
    fun authenticationAndModelSelectionRoundTrip() =
        runTest {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val (clientTransport, agentTransport) = linkedCapabilityTransports()
            val agent = CapabilityAgent()
            val server = AcpAgentServer(agent, agentTransport, scope)
            server.start()
            val client = AcpClient(clientTransport, scope)
            client.initialize()

            client.authenticate("token")
            client.setModel("session-1", "model-2")

            assertEquals(AuthenticateRequest("token"), agent.authenticateRequest)
            assertEquals(SetSessionModelRequest("session-1", "model-2"), agent.setModelRequest)
            scope.cancel()
        }

    @Test
    fun allTerminalOperationsRoundTrip() =
        runTest {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val (clientTransport, agentTransport) = linkedCapabilityTransports()
            val agent = TerminalAgent()
            val handler = RecordingTerminalHandler()
            val server = AcpAgentServer(agent, agentTransport, scope)
            server.start()
            val client =
                AcpClient(
                    clientTransport,
                    scope,
                    capabilities = ClientCapabilities(terminal = true),
                    handler = handler,
                )
            client.initialize()

            val response = client.promptText("session-1", "run terminal")

            assertEquals(StopReason.END_TURN, response.stopReason)
            assertEquals(
                listOf(
                    CreateTerminalRequest(
                        sessionId = "session-1",
                        command = "echo",
                        args = listOf("hello"),
                        cwd = "/tmp/project",
                        env = listOf(EnvVariable("MODE", "test")),
                        outputByteLimit = 1024,
                    ),
                    TerminalOutputRequest("session-1", "terminal-1"),
                    WaitForTerminalExitRequest("session-1", "terminal-1"),
                    KillTerminalRequest("session-1", "terminal-1"),
                    ReleaseTerminalRequest("session-1", "terminal-1"),
                ),
                handler.requests,
            )
            assertEquals("hello\n", agent.outputResponse?.output)
            assertEquals(true, agent.outputResponse?.truncated)
            assertEquals(0, agent.outputResponse?.exitStatus?.exitCode)
            assertEquals(0, agent.exitResponse?.exitCode)
            scope.cancel()
        }
}
