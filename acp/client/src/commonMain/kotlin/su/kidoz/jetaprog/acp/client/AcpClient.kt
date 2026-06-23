package su.kidoz.jetaprog.acp.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import su.kidoz.jetaprog.acp.protocol.CancelNotification
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.CreateTerminalRequest
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.KillTerminalRequest
import su.kidoz.jetaprog.acp.protocol.McpServer
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReleaseTerminalRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SetSessionModeRequest
import su.kidoz.jetaprog.acp.protocol.TerminalOutputRequest
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitRequest
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest

/**
 * The editor (Client) side of the Agent Client Protocol.
 *
 * Drives an external coding agent over [transport]: negotiates capabilities,
 * manages sessions, sends prompts, and exposes streamed [sessionUpdates]. Agent
 * callbacks (file system, permissions, terminals) are delegated to [handler].
 */
public class AcpClient(
    transport: AcpTransport,
    scope: CoroutineScope,
    private val capabilities: ClientCapabilities = ClientCapabilities(),
    private val clientInfo: Implementation? = null,
    private val handler: AcpClientHandler = object : AcpClientHandler {},
) {
    private val json: Json = AcpConnection.defaultJson

    private val _sessionUpdates = MutableSharedFlow<SessionNotification>(extraBufferCapacity = 64)

    /** Streamed `session/update` notifications from the agent. */
    public val sessionUpdates: SharedFlow<SessionNotification> = _sessionUpdates.asSharedFlow()

    private var agentCapabilities: InitializeResponse? = null

    /** The agent's `initialize` response, available after [initialize]. */
    public val initializeResponse: InitializeResponse?
        get() = agentCapabilities

    private val connection =
        AcpConnection(
            transport = transport,
            scope = scope,
            json = json,
            requestHandler = AcpRequestHandler { method, params -> handleAgentRequest(method, params) },
            notificationHandler = AcpNotificationHandler { method, params -> handleAgentNotification(method, params) },
        )

    /**
     * Starts listening and performs the `initialize` handshake.
     */
    public suspend fun initialize(): InitializeResponse {
        connection.start()
        val request = InitializeRequest(clientCapabilities = capabilities, clientInfo = clientInfo)
        val result = connection.sendRequest(AcpMethod.INITIALIZE, encode(InitializeRequest.serializer(), request))
        val response = decode(InitializeResponse.serializer(), result)
        agentCapabilities = response
        return response
    }

    /**
     * Creates a new session rooted at [cwd].
     */
    public suspend fun newSession(
        cwd: String,
        mcpServers: List<McpServer> = emptyList(),
    ): NewSessionResponse {
        val result =
            connection.sendRequest(
                AcpMethod.SESSION_NEW,
                encode(NewSessionRequest.serializer(), NewSessionRequest(cwd, mcpServers)),
            )
        return decode(NewSessionResponse.serializer(), result)
    }

    /**
     * Sends a prompt and suspends until the turn ends.
     */
    public suspend fun prompt(
        sessionId: String,
        blocks: List<ContentBlock>,
    ): PromptResponse {
        val result =
            connection.sendRequest(
                AcpMethod.SESSION_PROMPT,
                encode(PromptRequest.serializer(), PromptRequest(sessionId, blocks)),
            )
        return decode(PromptResponse.serializer(), result)
    }

    /**
     * Sends a plain-text prompt, a convenience over [prompt].
     */
    public suspend fun promptText(
        sessionId: String,
        text: String,
    ): PromptResponse = prompt(sessionId, listOf(ContentBlock.Text(text)))

    /**
     * Switches the active mode of [sessionId].
     */
    public suspend fun setMode(
        sessionId: String,
        modeId: String,
    ) {
        connection.sendRequest(
            AcpMethod.SESSION_SET_MODE,
            encode(SetSessionModeRequest.serializer(), SetSessionModeRequest(sessionId, modeId)),
        )
    }

    /**
     * Requests cancellation of the in-flight turn for [sessionId].
     */
    public suspend fun cancel(sessionId: String) {
        connection.sendNotification(
            AcpMethod.SESSION_CANCEL,
            encode(CancelNotification.serializer(), CancelNotification(sessionId)),
        )
    }

    /**
     * Closes the connection to the agent.
     */
    public suspend fun close() {
        connection.close()
    }

    private suspend fun handleAgentRequest(
        method: String,
        params: JsonElement?,
    ): JsonElement =
        when (method) {
            AcpMethod.FS_READ_TEXT_FILE -> {
                encodeResult(
                    su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
                        .serializer(),
                    handler.readTextFile(decode(ReadTextFileRequest.serializer(), params)),
                )
            }

            AcpMethod.FS_WRITE_TEXT_FILE -> {
                handler.writeTextFile(decode(WriteTextFileRequest.serializer(), params))
                emptyResult()
            }

            AcpMethod.SESSION_REQUEST_PERMISSION -> {
                encodeResult(
                    su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
                        .serializer(),
                    handler.requestPermission(decode(RequestPermissionRequest.serializer(), params)),
                )
            }

            AcpMethod.TERMINAL_CREATE -> {
                encodeResult(
                    su.kidoz.jetaprog.acp.protocol.CreateTerminalResponse
                        .serializer(),
                    handler.createTerminal(decode(CreateTerminalRequest.serializer(), params)),
                )
            }

            AcpMethod.TERMINAL_OUTPUT -> {
                encodeResult(
                    su.kidoz.jetaprog.acp.protocol.TerminalOutputResponse
                        .serializer(),
                    handler.terminalOutput(decode(TerminalOutputRequest.serializer(), params)),
                )
            }

            AcpMethod.TERMINAL_WAIT_FOR_EXIT -> {
                encodeResult(
                    su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitResponse
                        .serializer(),
                    handler.waitForTerminalExit(decode(WaitForTerminalExitRequest.serializer(), params)),
                )
            }

            AcpMethod.TERMINAL_KILL -> {
                handler.killTerminal(decode(KillTerminalRequest.serializer(), params))
                emptyResult()
            }

            AcpMethod.TERMINAL_RELEASE -> {
                handler.releaseTerminal(decode(ReleaseTerminalRequest.serializer(), params))
                emptyResult()
            }

            else -> {
                throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "Method not found: $method")
            }
        }

    private suspend fun handleAgentNotification(
        method: String,
        params: JsonElement?,
    ) {
        if (method == AcpMethod.SESSION_UPDATE && params != null) {
            _sessionUpdates.emit(decode(SessionNotification.serializer(), params))
        }
    }

    private fun <T> encode(
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
    ): JsonElement = json.encodeToJsonElement(serializer, value)

    private fun <T> encodeResult(
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

    private fun emptyResult(): JsonElement = JsonObject(emptyMap())
}
