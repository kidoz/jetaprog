package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpMethod
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AgentCapabilities
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ClientSessionCapabilities
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.LoadSessionRequest
import su.kidoz.jetaprog.acp.protocol.LoadSessionResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SessionConfigBooleanCapability
import su.kidoz.jetaprog.acp.protocol.SessionConfigCapabilities
import su.kidoz.jetaprog.acp.protocol.SessionConfigOption
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.SetSessionBooleanConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigResponse
import su.kidoz.jetaprog.acp.protocol.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val configJson = AcpConnection.defaultJson
private val booleanCapabilities =
    ClientCapabilities(session = ClientSessionCapabilities(SessionConfigCapabilities(SessionConfigBooleanCapability)))

private class ConfigTransport : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<JsonObject>()
    lateinit var peer: ConfigTransport

    override val incoming: Flow<String> = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        sent += configJson.parseToJsonElement(line).jsonObject
        peer.inbound.send(line)
    }

    override suspend fun close() {
        inbound.close()
    }
}

private class ConfigPeer(
    scope: CoroutineScope,
    agent: AcpAgent,
) {
    val clientTransport = ConfigTransport()
    val serverTransport = ConfigTransport()
    val server = AcpAgentServer(agent, serverTransport, scope)

    init {
        clientTransport.peer = serverTransport
        serverTransport.peer = clientTransport
        server.start()
    }
}

private open class ConfigBaseAgent : AcpAgent {
    override suspend fun initialize(request: InitializeRequest): InitializeResponse =
        InitializeResponse(agentCapabilities = AgentCapabilities(loadSession = true))

    override suspend fun newSession(request: NewSessionRequest): NewSessionResponse = NewSessionResponse("s1")

    override suspend fun loadSession(request: LoadSessionRequest): LoadSessionResponse = LoadSessionResponse()

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse = PromptResponse(StopReason.END_TURN)
}

private open class ConfigurableAgent : ConfigBaseAgent() {
    private var model = "small"
    private var compact = false
    val requests = mutableListOf<Any>()

    fun configuration(): List<SessionConfigOption> =
        buildList {
            add(
                SessionConfigOption(
                    id = "model",
                    name = "Model",
                    type = "select",
                    currentValue = JsonPrimitive(model),
                    category = "model",
                    options =
                        configJson.parseToJsonElement(
                            """[{"group":"local","name":"Local","options":[{"value":"small","name":"Small"},{"value":"large","name":"Large"}]}]""",
                        ),
                ),
            )
            if (model == "large") {
                add(
                    SessionConfigOption(
                        id = "effort",
                        name = "Effort",
                        type = "select",
                        currentValue = JsonPrimitive("high"),
                        options = configJson.parseToJsonElement("""[{"value":"high","name":"High"}]"""),
                    ),
                )
            }
            add(SessionConfigOption("compact", "Compact", "boolean", JsonPrimitive(compact)))
        }

    override suspend fun newSession(request: NewSessionRequest): NewSessionResponse =
        NewSessionResponse("s1", configOptions = configuration())

    override suspend fun loadSession(request: LoadSessionRequest): LoadSessionResponse =
        LoadSessionResponse(configOptions = configuration())

    override suspend fun setConfigOption(request: SetSessionConfigRequest): SetSessionConfigResponse {
        validateSession(request.sessionId)
        if (request.configId != "model" || request.value !in listOf("small", "large")) {
            throw AcpRequestException(AcpError.INVALID_PARAMS, "Unknown select setting or value")
        }
        requests += request
        model = request.value
        return SetSessionConfigResponse(configuration())
    }

    override suspend fun setConfigOption(request: SetSessionBooleanConfigRequest): SetSessionConfigResponse {
        validateSession(request.sessionId)
        if (request.configId != "compact") {
            throw AcpRequestException(AcpError.INVALID_PARAMS, "Unknown boolean setting")
        }
        requests += request
        compact = request.value
        return SetSessionConfigResponse(configuration())
    }

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse {
        compact = true
        session.update(SessionUpdate.ConfigOptionUpdate(configuration()))
        session.sendMessageChunk("Configuration updated")
        return PromptResponse(StopReason.END_TURN)
    }

    private fun validateSession(sessionId: String) {
        if (sessionId != "s1") throw AcpRequestException(AcpError.RESOURCE_NOT_FOUND, "Unknown session")
    }
}

class AcpSessionConfigRoundTripTest {
    @Test
    fun selectChangesReturnCompleteConfigurationIncludingDependentChanges() =
        runTest {
            val agent = ConfigurableAgent()
            val peer = ConfigPeer(backgroundScope, agent)
            val client = AcpClient(peer.clientTransport, backgroundScope, capabilities = booleanCapabilities)
            client.initialize()
            assertEquals(agent.configuration(), client.newSession("/project").configOptions)

            val expanded = client.setConfigOption("s1", "model", "large")
            assertEquals(agent.configuration(), expanded.configOptions)
            assertEquals(listOf("model", "effort", "compact"), expanded.configOptions.map { it.id })
            assertEquals(JsonPrimitive("large"), expanded.configOptions.first().currentValue)
            assertEquals(agent.configuration(), client.loadSession("s1", "/project").configOptions)

            val contracted = client.setConfigOption("s1", "model", "small")
            assertEquals(listOf("model", "compact"), contracted.configOptions.map { it.id })
            assertEquals(agent.configuration(), contracted.configOptions)
            assertEquals(
                listOf<Any>(
                    SetSessionConfigRequest("s1", "model", "large"),
                    SetSessionConfigRequest("s1", "model", "small"),
                ),
                agent.requests,
            )
            client.close()
            peer.server.close()
        }

    @Test
    fun booleanChangesPreserveNativeTrueAndFalseValues() =
        runTest {
            val agent = ConfigurableAgent()
            val peer = ConfigPeer(backgroundScope, agent)
            val client = AcpClient(peer.clientTransport, backgroundScope, capabilities = booleanCapabilities)
            client.initialize()
            client.newSession("/project")

            for (value in listOf(true, false)) {
                val response = client.setConfigOption("s1", "compact", value)
                assertEquals(agent.configuration(), response.configOptions)
                assertEquals(JsonPrimitive(value), response.configOptions.last().currentValue)
                val params =
                    peer.clientTransport.sent
                        .last()
                        .getValue("params")
                        .jsonObject
                assertEquals(JsonPrimitive("boolean"), params["type"])
                assertEquals(JsonPrimitive(value), params["value"])
            }
            assertEquals(
                listOf<Any>(
                    SetSessionBooleanConfigRequest("s1", "compact", true),
                    SetSessionBooleanConfigRequest("s1", "compact", false),
                ),
                agent.requests,
            )
            client.close()
            peer.server.close()
        }

    @Test
    fun booleanOptionsAreGatedOnEveryOutboundConfigurationPath() =
        runTest {
            val variants =
                listOf(
                    ClientCapabilities(),
                    ClientCapabilities(session = ClientSessionCapabilities()),
                    ClientCapabilities(session = ClientSessionCapabilities(SessionConfigCapabilities())),
                    booleanCapabilities,
                )
            for (capabilities in variants) {
                val agent = ConfigurableAgent()
                val updates = mutableListOf<SessionNotification>()
                val peer = ConfigPeer(backgroundScope, agent)
                val client =
                    AcpClient(
                        peer.clientTransport,
                        backgroundScope,
                        capabilities = capabilities,
                        handler =
                            object : AcpClientHandler {
                                override suspend fun sessionUpdate(notification: SessionNotification) {
                                    updates += notification
                                }
                            },
                    )
                client.initialize()
                val supportsBoolean = capabilities == booleanCapabilities

                fun expected(): List<SessionConfigOption> =
                    agent.configuration().filter { supportsBoolean || it.type != "boolean" }

                assertEquals(expected(), client.newSession("/project").configOptions)
                assertEquals(expected(), client.loadSession("s1", "/project").configOptions)
                val changed = client.setConfigOption("s1", "model", "large")
                assertEquals(expected(), changed.configOptions)
                assertEquals(StopReason.END_TURN, client.promptText("s1", "update").stopReason)
                val update = assertIs<SessionUpdate.ConfigOptionUpdate>(updates.first().update)
                assertEquals("s1", updates.first().sessionId)
                assertEquals(expected(), update.configOptions)
                assertIs<SessionUpdate.AgentMessageChunk>(updates.last().update)
                // Capability filtering never changes the agent's own effective settings.
                assertEquals(JsonPrimitive(true), agent.configuration().last().currentValue)
                client.close()
                peer.server.close()
            }
        }

    @Test
    fun invalidSettingsPropagateAgentErrorsAndLeaveConfirmedStateAvailableForRetry() =
        runTest {
            val agent = ConfigurableAgent()
            val peer = ConfigPeer(backgroundScope, agent)
            val client = AcpClient(peer.clientTransport, backgroundScope, capabilities = booleanCapabilities)
            client.initialize()
            val initial = client.newSession("/project").configOptions

            assertEquals(
                AcpError.INVALID_PARAMS,
                assertFailsWith<AcpRequestException> { client.setConfigOption("s1", "model", "invalid") }.code,
            )
            assertEquals(
                AcpError.INVALID_PARAMS,
                assertFailsWith<AcpRequestException> { client.setConfigOption("s1", "unknown", "small") }.code,
            )
            assertEquals(
                AcpError.INVALID_PARAMS,
                assertFailsWith<AcpRequestException> { client.setConfigOption("s1", "unknown", true) }.code,
            )
            assertEquals(
                AcpError.RESOURCE_NOT_FOUND,
                assertFailsWith<AcpRequestException> { client.setConfigOption("missing", "model", "large") }.code,
            )
            assertEquals(initial, client.loadSession("s1", "/project").configOptions)
            assertTrue(agent.requests.isEmpty())
            val retried = client.setConfigOption("s1", "model", "large")
            assertEquals(agent.configuration(), retried.configOptions)
            assertEquals(JsonPrimitive("large"), agent.configuration().first().currentValue)
            client.close()
            peer.server.close()
        }

    @Test
    fun agentsWithoutSettingsRejectBothOverloadsAndKeepLegacySessionsWorking() =
        runTest {
            val peer = ConfigPeer(backgroundScope, ConfigBaseAgent())
            val client = AcpClient(peer.clientTransport, backgroundScope, capabilities = booleanCapabilities)
            client.initialize()
            assertNull(client.newSession("/project").configOptions)
            assertNull(client.loadSession("s1", "/project").configOptions)

            assertEquals(
                AcpError.METHOD_NOT_FOUND,
                assertFailsWith<AcpRequestException> { client.setConfigOption("s1", "model", "small") }.code,
            )
            assertEquals(
                AcpError.METHOD_NOT_FOUND,
                assertFailsWith<AcpRequestException> { client.setConfigOption("s1", "compact", false) }.code,
            )
            assertEquals(StopReason.END_TURN, client.promptText("s1", "hello").stopReason)
            client.close()
            peer.server.close()
        }

    @Test
    fun emptyConfigurationResponsesAreAuthoritative() =
        runTest {
            val agent =
                object : ConfigBaseAgent() {
                    override suspend fun setConfigOption(request: SetSessionConfigRequest): SetSessionConfigResponse =
                        SetSessionConfigResponse(emptyList())
                }
            val peer = ConfigPeer(backgroundScope, agent)
            val client = AcpClient(peer.clientTransport, backgroundScope)
            client.initialize()
            client.newSession("/project")

            assertEquals(emptyList(), client.setConfigOption("s1", "model", "small").configOptions)
            val result =
                peer.serverTransport.sent
                    .last()
                    .getValue("result")
                    .jsonObject
            assertEquals(configJson.parseToJsonElement("[]"), result["configOptions"])
            client.close()
            peer.server.close()
        }

    @Test
    fun configurationCanChangeDuringAnActivePrompt() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val agent =
                object : ConfigurableAgent() {
                    override suspend fun prompt(
                        request: PromptRequest,
                        session: AcpAgentSession,
                    ): PromptResponse {
                        entered.complete(Unit)
                        finish.await()
                        return PromptResponse(StopReason.END_TURN)
                    }
                }
            val peer = ConfigPeer(backgroundScope, agent)
            val client = AcpClient(peer.clientTransport, backgroundScope, capabilities = booleanCapabilities)
            client.initialize()
            client.newSession("/project")
            val prompt = async { client.promptText("s1", "wait") }
            entered.await()

            val changed = client.setConfigOption("s1", "model", "large")
            assertEquals(agent.configuration(), changed.configOptions)
            assertFalse(prompt.isCompleted)
            finish.complete(Unit)
            assertEquals(StopReason.END_TURN, prompt.await().stopReason)
            client.close()
            peer.server.close()
        }

    @Test
    fun malformedRequestsAreRejectedBeforeDispatchAndTheConnectionRemainsUsable() =
        runTest {
            val agent = ConfigurableAgent()
            val peer = ConfigPeer(backgroundScope, agent)
            val connection = AcpConnection(peer.clientTransport, backgroundScope)
            connection.start()
            connection.sendRequest(
                AcpMethod.INITIALIZE,
                configJson.parseToJsonElement(
                    """{"protocolVersion":1,"clientCapabilities":{"session":{"configOptions":{"boolean":{}}}}}""",
                ),
            )
            val invalid =
                listOf(
                    "null",
                    "[]",
                    "{}",
                    """{"sessionId":"s1","configId":"model"}""",
                    """{"sessionId":7,"configId":"model","value":"large"}""",
                    """{"sessionId":"s1","configId":false,"value":"large"}""",
                    """{"sessionId":"s1","configId":"model","value":true}""",
                    """{"sessionId":"s1","configId":"model","value":42}""",
                    """{"sessionId":"s1","configId":"model","value":null}""",
                    """{"sessionId":"s1","configId":"model","value":[]}""",
                    """{"sessionId":"s1","configId":"model","value":"large","type":"unknown"}""",
                    """{"sessionId":"s1","configId":"model","value":"large","type":null}""",
                    """{"sessionId":"s1","configId":"compact","value":"true","type":"boolean"}""",
                    """{"sessionId":"s1","configId":"compact","value":1,"type":"boolean"}""",
                    """{"sessionId":"s1","configId":"compact","value":null,"type":"boolean"}""",
                    """{"sessionId":"s1","configId":"compact","value":false}""",
                )
            for (params in invalid) {
                val error =
                    assertFailsWith<AcpRequestException>(params) {
                        connection.sendRequest(
                            AcpMethod.SESSION_SET_CONFIG_OPTION,
                            configJson.parseToJsonElement(params),
                        )
                    }
                assertEquals(AcpError.INVALID_PARAMS, error.code, params)
            }
            assertTrue(agent.requests.isEmpty())
            connection.sendRequest(
                AcpMethod.SESSION_SET_CONFIG_OPTION,
                configJson.parseToJsonElement(
                    """{"sessionId":"s1","configId":"model","value":"large","type":"select"}""",
                ),
            )
            assertEquals(listOf<Any>(SetSessionConfigRequest("s1", "model", "large")), agent.requests)
            connection.close()
            peer.server.close()
        }

    @Test
    fun rawBooleanRequestsCannotBypassCapabilityNegotiation() =
        runTest {
            for (capabilities in listOf("{}", """{"session":{}}""", """{"session":{"configOptions":{}}}""")) {
                val agent = ConfigurableAgent()
                val peer = ConfigPeer(backgroundScope, agent)
                val connection = AcpConnection(peer.clientTransport, backgroundScope)
                connection.start()
                connection.sendRequest(
                    AcpMethod.INITIALIZE,
                    configJson.parseToJsonElement("""{"protocolVersion":1,"clientCapabilities":$capabilities}"""),
                )
                val error =
                    assertFailsWith<AcpRequestException> {
                        connection.sendRequest(
                            AcpMethod.SESSION_SET_CONFIG_OPTION,
                            configJson.parseToJsonElement(
                                """{"sessionId":"s1","configId":"compact","type":"boolean","value":false}""",
                            ),
                        )
                    }
                assertEquals(AcpError.INVALID_PARAMS, error.code)
                assertTrue(agent.requests.isEmpty())
                connection.close()
                peer.server.close()
            }
        }
}
