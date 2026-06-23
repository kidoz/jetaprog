package su.kidoz.jetaprog.acp.protocol

import kotlinx.coroutines.flow.Flow

/**
 * A bidirectional, newline-delimited JSON transport for the Agent Client Protocol.
 *
 * Implementations carry one JSON document per emitted/accepted [String]; framing
 * (the trailing newline) is the transport's responsibility.
 */
public interface AcpTransport {
    /** A stream of inbound JSON documents, one per element. */
    public val incoming: Flow<String>

    /**
     * Sends a single JSON document.
     *
     * @param line the serialized JSON, without a trailing newline.
     */
    public suspend fun send(line: String)

    /** Closes the transport and releases its resources. */
    public suspend fun close()
}
