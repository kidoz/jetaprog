package su.kidoz.jetaprog.acp.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpMethod
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.CreateTerminalRequest
import su.kidoz.jetaprog.acp.protocol.CreateTerminalResponse
import su.kidoz.jetaprog.acp.protocol.EnvVariable
import su.kidoz.jetaprog.acp.protocol.KillTerminalRequest
import su.kidoz.jetaprog.acp.protocol.PermissionOption
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.ReleaseTerminalRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.TerminalOutputRequest
import su.kidoz.jetaprog.acp.protocol.TerminalOutputResponse
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitRequest
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitResponse
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest

/**
 * The agent's handle to a single session during a prompt turn.
 *
 * Provides streaming of [SessionUpdate]s back to the client and access to the
 * client's capabilities (permissions and file system).
 */
public class AcpAgentSession internal constructor(
    /** The session identifier. */
    public val sessionId: String,
    private val connection: AcpConnection,
    private val json: Json,
) {
    /**
     * Streams a [SessionUpdate] to the client via `session/update`.
     */
    public suspend fun update(update: SessionUpdate) {
        connection.sendNotification(
            AcpMethod.SESSION_UPDATE,
            json.encodeToJsonElement(SessionNotification.serializer(), SessionNotification(sessionId, update)),
        )
    }

    /**
     * Streams a chunk of user-visible agent text.
     */
    public suspend fun sendMessageChunk(text: String) {
        update(SessionUpdate.AgentMessageChunk(ContentBlock.Text(text)))
    }

    /**
     * Asks the client to authorize a tool call.
     */
    public suspend fun requestPermission(
        toolCall: PermissionToolCall,
        options: List<PermissionOption>,
    ): RequestPermissionResponse {
        val result =
            connection.sendRequest(
                AcpMethod.SESSION_REQUEST_PERMISSION,
                json.encodeToJsonElement(
                    RequestPermissionRequest.serializer(),
                    RequestPermissionRequest(sessionId, toolCall, options),
                ),
            )
        return decode(RequestPermissionResponse.serializer(), result)
    }

    /**
     * Reads a text file through the client's file system.
     */
    public suspend fun readTextFile(
        path: String,
        line: Int? = null,
        limit: Int? = null,
    ): ReadTextFileResponse {
        val result =
            connection.sendRequest(
                AcpMethod.FS_READ_TEXT_FILE,
                json.encodeToJsonElement(
                    ReadTextFileRequest.serializer(),
                    ReadTextFileRequest(sessionId, path, line, limit),
                ),
            )
        return decode(ReadTextFileResponse.serializer(), result)
    }

    /**
     * Writes a text file through the client's file system.
     */
    public suspend fun writeTextFile(
        path: String,
        content: String,
    ) {
        connection.sendRequest(
            AcpMethod.FS_WRITE_TEXT_FILE,
            json.encodeToJsonElement(
                WriteTextFileRequest.serializer(),
                WriteTextFileRequest(sessionId, path, content),
            ),
        )
    }

    /**
     * Creates a terminal through the client.
     */
    public suspend fun createTerminal(
        command: String,
        args: List<String> = emptyList(),
        cwd: String? = null,
        env: List<EnvVariable> = emptyList(),
        outputByteLimit: Int? = null,
    ): CreateTerminalResponse {
        val result =
            connection.sendRequest(
                AcpMethod.TERMINAL_CREATE,
                json.encodeToJsonElement(
                    CreateTerminalRequest.serializer(),
                    CreateTerminalRequest(sessionId, command, args, cwd, env, outputByteLimit),
                ),
            )
        return decode(CreateTerminalResponse.serializer(), result)
    }

    /**
     * Returns the current output of a client terminal.
     */
    public suspend fun terminalOutput(terminalId: String): TerminalOutputResponse {
        val result =
            connection.sendRequest(
                AcpMethod.TERMINAL_OUTPUT,
                json.encodeToJsonElement(
                    TerminalOutputRequest.serializer(),
                    TerminalOutputRequest(sessionId, terminalId),
                ),
            )
        return decode(TerminalOutputResponse.serializer(), result)
    }

    /**
     * Waits for a client terminal to exit.
     */
    public suspend fun waitForTerminalExit(terminalId: String): WaitForTerminalExitResponse {
        val result =
            connection.sendRequest(
                AcpMethod.TERMINAL_WAIT_FOR_EXIT,
                json.encodeToJsonElement(
                    WaitForTerminalExitRequest.serializer(),
                    WaitForTerminalExitRequest(sessionId, terminalId),
                ),
            )
        return decode(WaitForTerminalExitResponse.serializer(), result)
    }

    /**
     * Kills a running client terminal.
     */
    public suspend fun killTerminal(terminalId: String) {
        connection.sendRequest(
            AcpMethod.TERMINAL_KILL,
            json.encodeToJsonElement(KillTerminalRequest.serializer(), KillTerminalRequest(sessionId, terminalId)),
        )
    }

    /**
     * Releases a client terminal and its resources.
     */
    public suspend fun releaseTerminal(terminalId: String) {
        connection.sendRequest(
            AcpMethod.TERMINAL_RELEASE,
            json.encodeToJsonElement(
                ReleaseTerminalRequest.serializer(),
                ReleaseTerminalRequest(sessionId, terminalId),
            ),
        )
    }

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        element: JsonElement?,
    ): T {
        val payload = element ?: throw AcpRequestException(AcpError.INTERNAL_ERROR, "Empty response from client")
        return json.decodeFromJsonElement(serializer, payload)
    }
}
