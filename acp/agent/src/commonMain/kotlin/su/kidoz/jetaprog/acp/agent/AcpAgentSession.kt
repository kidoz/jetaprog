package su.kidoz.jetaprog.acp.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpMethod
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.PermissionOption
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
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

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        element: JsonElement?,
    ): T {
        val payload = element ?: throw AcpRequestException(AcpError.INTERNAL_ERROR, "Empty response from client")
        return json.decodeFromJsonElement(serializer, payload)
    }
}
