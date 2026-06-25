package su.kidoz.jetaprog.mcp.server.transport

import kotlinx.serialization.json.JsonObject
import su.kidoz.jetaprog.mcp.server.McpServerConfig

/**
 * A transport that exposes the MCP server to external clients.
 *
 * The transport receives JSON-RPC request objects and delegates them to [handler],
 * relaying the returned response (or nothing, for notifications) back to the client.
 */
public interface McpTransport {
    /** Starts serving, routing each incoming JSON-RPC message through [handler]. */
    public suspend fun start(handler: suspend (JsonObject) -> JsonObject?)

    /** Stops serving and releases resources. */
    public suspend fun stop()

    /** A human-readable description of where the transport is listening (for logging/config). */
    public val endpointDescription: String
}

/**
 * Creates the platform default transport for [config], or `null` when no networked
 * transport is available on this platform.
 */
public expect fun createMcpTransport(config: McpServerConfig): McpTransport?
