package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base type for all JSON-RPC 2.0 messages exchanged over the Agent Client Protocol.
 *
 * Unlike the Language Server Protocol, ACP frames messages as newline-delimited
 * JSON over stdio rather than using `Content-Length` headers.
 */
public sealed interface AcpMessage {
    /** The JSON-RPC protocol version. Always `"2.0"`. */
    public val jsonrpc: String
        get() = JSON_RPC_VERSION

    public companion object {
        /** The supported JSON-RPC version string. */
        public const val JSON_RPC_VERSION: String = "2.0"
    }
}

/**
 * A JSON-RPC request that expects a matching [AcpResponse].
 *
 * The [id] is kept as a raw [JsonElement] so request identifiers received from a
 * peer (which may be strings or numbers) can be echoed back verbatim.
 */
@Serializable
public data class AcpRequest(
    /** The request identifier, correlated with the response [AcpResponse.id]. */
    val id: JsonElement,
    /** The method being invoked, e.g. `session/prompt`. */
    val method: String,
    /** The method parameters, or `null` when the method takes none. */
    val params: JsonElement? = null,
) : AcpMessage

/**
 * A JSON-RPC response to a previously issued [AcpRequest].
 */
@Serializable
public data class AcpResponse(
    /** The identifier of the request this response corresponds to. */
    val id: JsonElement,
    /** The successful result payload, mutually exclusive with [error]. */
    val result: JsonElement? = null,
    /** The error payload when the request failed, mutually exclusive with [result]. */
    val error: AcpError? = null,
) : AcpMessage

/**
 * A JSON-RPC notification, a one-way message that expects no response.
 */
@Serializable
public data class AcpNotification(
    /** The notification method, e.g. `session/update`. */
    val method: String,
    /** The notification parameters, or `null` when it takes none. */
    val params: JsonElement? = null,
) : AcpMessage

/**
 * A JSON-RPC error object.
 */
@Serializable
public data class AcpError(
    /** A numeric error code, per the JSON-RPC and ACP error code ranges. */
    val code: Int,
    /** A human-readable error description. */
    val message: String,
    /** Optional structured error data. */
    val data: JsonElement? = null,
) {
    public companion object {
        /** Invalid JSON was received. */
        public const val PARSE_ERROR: Int = -32700

        /** The JSON sent is not a valid request object. */
        public const val INVALID_REQUEST: Int = -32600

        /** The requested method does not exist or is not available. */
        public const val METHOD_NOT_FOUND: Int = -32601

        /** Invalid method parameters. */
        public const val INVALID_PARAMS: Int = -32602

        /** Internal JSON-RPC error. */
        public const val INTERNAL_ERROR: Int = -32603

        /** ACP: authentication is required before the operation can proceed. */
        public const val AUTH_REQUIRED: Int = -32000

        /** ACP: the referenced resource was not found. */
        public const val RESOURCE_NOT_FOUND: Int = -32002
    }
}

/**
 * Exception raised when a JSON-RPC request returns an error response.
 */
public class AcpRequestException(
    /** The JSON-RPC error code. */
    public val code: Int,
    /** The error message. */
    override val message: String,
    /** Optional structured error data. */
    public val data: JsonElement? = null,
) : Exception(message)
