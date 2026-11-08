package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpMethod
import su.kidoz.jetaprog.acp.protocol.AcpNotificationHandler
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpRequestHandler
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AuthenticateRequest
import su.kidoz.jetaprog.acp.protocol.CancelNotification
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.LoadSessionRequest
import su.kidoz.jetaprog.acp.protocol.LoadSessionResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SetSessionBooleanConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigResponse
import su.kidoz.jetaprog.acp.protocol.SetSessionModeRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionModelRequest

/**
 * Serves an [AcpAgent] over an [AcpTransport].
 *
 * Dispatches inbound client requests to the agent and exposes a per-turn
 * [AcpAgentSession] so the agent can stream updates and call back to the client.
 * Boolean configuration changes require the client's initialization capability;
 * boolean options are omitted from all configuration payloads for other clients.
 */
public class AcpAgentServer(
    private val agent: AcpAgent,
    transport: AcpTransport,
    scope: CoroutineScope,
) {
    private val json: Json = AcpConnection.defaultJson
    private val clientCapabilities = MutableStateFlow(ClientCapabilities())

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
                val request = decode(InitializeRequest.serializer(), params)
                val response = agent.initialize(request)
                clientCapabilities.value = request.clientCapabilities
                encode(InitializeResponse.serializer(), response)
            }

            AcpMethod.AUTHENTICATE -> {
                agent.authenticate(decode(AuthenticateRequest.serializer(), params))
                empty()
            }

            AcpMethod.SESSION_NEW -> {
                val response = agent.newSession(decode(NewSessionRequest.serializer(), params))
                encode(
                    NewSessionResponse.serializer(),
                    response.copy(configOptions = response.configOptions?.forClient(clientCapabilities.value)),
                )
            }

            AcpMethod.SESSION_LOAD -> {
                val response = agent.loadSession(decode(LoadSessionRequest.serializer(), params))
                encode(
                    LoadSessionResponse.serializer(),
                    response.copy(configOptions = response.configOptions?.forClient(clientCapabilities.value)),
                )
            }

            AcpMethod.SESSION_PROMPT -> {
                val request = decode(PromptRequest.serializer(), params)
                val session = AcpAgentSession(request.sessionId, connection, json, clientCapabilities.value)
                encode(PromptResponse.serializer(), agent.prompt(request, session))
            }

            AcpMethod.SESSION_SET_MODE -> {
                agent.setMode(decode(SetSessionModeRequest.serializer(), params))
                empty()
            }

            AcpMethod.SESSION_SET_MODEL -> {
                agent.setModel(decode(SetSessionModelRequest.serializer(), params))
                empty()
            }

            AcpMethod.SESSION_SET_CONFIG_OPTION -> {
                val response = setConfigOption(params)
                encode(
                    SetSessionConfigResponse.serializer(),
                    response.copy(configOptions = response.configOptions.forClient(clientCapabilities.value)),
                )
            }

            else -> {
                throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "Method not found: $method")
            }
        }

    private suspend fun setConfigOption(params: JsonElement?): SetSessionConfigResponse {
        val payload = params as? JsonObject ?: invalidConfig("Expected configuration parameters")
        val sessionId = payload.requireString("sessionId")
        val configId = payload.requireString("configId")
        val value = payload["value"] as? JsonPrimitive ?: invalidConfig("Missing or invalid configuration value")
        return when (payload["type"]) {
            null, JsonPrimitive("select") -> {
                if (!value.isString) invalidConfig("Select configuration values must be strings")
                agent.setConfigOption(SetSessionConfigRequest(sessionId, configId, value.content))
            }

            JsonPrimitive("boolean") -> {
                val capabilities = clientCapabilities.value
                if (capabilities.session?.configOptions?.boolean == null) {
                    invalidConfig("Boolean configuration support was not advertised")
                }
                if (value.isString) invalidConfig("Boolean configuration values must be booleans")
                val boolean = value.booleanOrNull ?: invalidConfig("Boolean configuration values must be booleans")
                agent.setConfigOption(SetSessionBooleanConfigRequest(sessionId, configId, boolean))
            }

            else -> {
                invalidConfig("Unsupported configuration type")
            }
        }
    }

    private fun JsonObject.requireString(name: String): String =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalidConfig("Missing or invalid $name")

    private fun invalidConfig(message: String): Nothing = throw AcpRequestException(AcpError.INVALID_PARAMS, message)

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
