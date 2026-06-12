package su.kidoz.jetaprog.lsp.client.transport

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.lsp.protocol.JsonRpcMessage

/**
 * LSP transport interface for sending and receiving JSON-RPC messages.
 */
public interface LspTransport {
    /**
     * Whether the transport is connected.
     */
    public val isConnected: Boolean

    /**
     * Flow of incoming messages from the language server.
     */
    public val incoming: Flow<JsonRpcMessage>

    /**
     * Send a message to the language server.
     */
    public suspend fun send(message: JsonRpcMessage)

    /**
     * Close the transport connection.
     */
    public suspend fun close()
}

/**
 * Transport configuration.
 */
public sealed interface TransportConfig {
    /**
     * Standard I/O transport using process stdin/stdout.
     */
    public data class Stdio(
        val command: List<String>,
        val workingDirectory: String? = null,
        val environment: Map<String, String> = emptyMap(),
    ) : TransportConfig

    /**
     * TCP socket transport.
     */
    public data class Socket(
        val host: String,
        val port: Int,
    ) : TransportConfig
}
