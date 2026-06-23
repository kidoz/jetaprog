package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpMethod
import su.kidoz.jetaprog.acp.protocol.AcpNotificationHandler
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpRequestHandler
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AuthenticateRequest
import su.kidoz.jetaprog.acp.protocol.CancelNotification
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.LoadSessionRequest
import su.kidoz.jetaprog.acp.protocol.LoadSessionResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SetSessionModeRequest

/**
 * Serves an [AcpAgent] over an [AcpTransport].
 *
 * Dispatches inbound client requests to the agent and exposes a per-turn
 * [AcpAgentSession] so the agent can stream updates and call back to the client.
 */
public class AcpAgentServer(
    private val agent: AcpAgent,
    transport: AcpTransport,
    scope: CoroutineScope,
) {
    private val json: Json = AcpConnection.defaultJson

    private val connection =
        AcpConnection(
            transport = transport,
            scope = scope,
            json = json,
            requestHandler = AcpRequestHandler { method, params -> handleRequest(method, params) },
            notificationHandler = AcpNotificationHandler { method, params -> handleNotification(method, params) },
        )

    /**
     * Starts serving inbound messages.
     */
    public fun start() {
        connection.start()
    }

    /**
     * Stops the server and closes the transport.
     */
    public suspend fun close() {
        connection.close()
    }

    private suspend fun handleRequest(
        method: String,
        params: JsonElement?,
    ): JsonElement =
        when (method) {
            AcpMethod.INITIALIZE -> {
                encode(
                    InitializeResponse.serializer(),
                    agent.initialize(decode(InitializeRequest.serializer(), params)),
                )
            }

            AcpMethod.AUTHENTICATE -> {
                agent.authenticate(decode(AuthenticateRequest.serializer(), params))
                empty()
            }

            AcpMethod.SESSION_NEW -> {
                encode(
                    NewSessionResponse.serializer(),
                    agent.newSession(decode(NewSessionRequest.serializer(), params)),
                )
            }

            AcpMethod.SESSION_LOAD -> {
                encode(
                    LoadSessionResponse.serializer(),
                    agent.loadSession(decode(LoadSessionRequest.serializer(), params)),
                )
            }

            AcpMethod.SESSION_PROMPT -> {
                val request = decode(PromptRequest.serializer(), params)
                val session = AcpAgentSession(request.sessionId, connection, json)
                encode(PromptResponse.serializer(), agent.prompt(request, session))
            }

            AcpMethod.SESSION_SET_MODE -> {
                agent.setMode(decode(SetSessionModeRequest.serializer(), params))
                empty()
            }

            else -> {
                throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "Method not found: $method")
            }
        }

    private suspend fun handleNotification(
        method: String,
        params: JsonElement?,
    ) {
        if (method == AcpMethod.SESSION_CANCEL && params != null) {
            agent.cancel(decode(CancelNotification.serializer(), params))
        }
    }

    private fun <T> encode(
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
    ): JsonElement = json.encodeToJsonElement(serializer, value)

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        element: JsonElement?,
    ): T {
        val payload = element ?: throw AcpRequestException(AcpError.INVALID_PARAMS, "Missing params")
        return json.decodeFromJsonElement(serializer, payload)
    }

    private fun empty(): JsonElement = JsonObject(emptyMap())
}
