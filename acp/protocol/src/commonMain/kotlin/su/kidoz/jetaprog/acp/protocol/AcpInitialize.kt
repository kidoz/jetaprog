package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable

/**
 * Identifies an implementation (client or agent) by name and version.
 */
@Serializable
public data class Implementation(
    /** The implementation name. */
    val name: String,
    /** The implementation version, if known. */
    val version: String? = null,
)

/**
 * Parameters of the `initialize` request sent by the client.
 */
@Serializable
public data class InitializeRequest(
    /** The protocol version the client wishes to use. */
    val protocolVersion: Int = AcpMethod.PROTOCOL_VERSION,
    /** Capabilities the client provides to the agent. */
    val clientCapabilities: ClientCapabilities = ClientCapabilities(),
    /** Optional information about the client implementation. */
    val clientInfo: Implementation? = null,
)

/**
 * Result of the `initialize` request returned by the agent.
 */
@Serializable
public data class InitializeResponse(
    /** The protocol version the agent will use. */
    val protocolVersion: Int = AcpMethod.PROTOCOL_VERSION,
    /** Capabilities the agent supports. */
    val agentCapabilities: AgentCapabilities = AgentCapabilities(),
    /** Authentication methods the agent accepts, if any. */
    val authMethods: List<AuthMethod> = emptyList(),
    /** Optional information about the agent implementation. */
    val agentInfo: Implementation? = null,
)

/**
 * Capabilities the client advertises to the agent.
 */
@Serializable
public data class ClientCapabilities(
    /** File-system operations the client can perform on the agent's behalf. */
    val fs: FileSystemCapability = FileSystemCapability(),
    /** Whether the client can host terminals for the agent. */
    val terminal: Boolean = false,
)

/**
 * File-system capabilities provided by the client.
 */
@Serializable
public data class FileSystemCapability(
    /** Whether `fs/read_text_file` is supported. */
    val readTextFile: Boolean = false,
    /** Whether `fs/write_text_file` is supported. */
    val writeTextFile: Boolean = false,
)

/**
 * Capabilities the agent advertises to the client.
 */
@Serializable
public data class AgentCapabilities(
    /** Whether the agent supports loading previously created sessions. */
    val loadSession: Boolean = false,
    /** Prompt content types the agent accepts. */
    val promptCapabilities: PromptCapabilities = PromptCapabilities(),
)

/**
 * Content types the agent accepts in `session/prompt` requests.
 */
@Serializable
public data class PromptCapabilities(
    /** Whether [ContentBlock.Image] is supported in prompts. */
    val image: Boolean = false,
    /** Whether [ContentBlock.Audio] is supported in prompts. */
    val audio: Boolean = false,
    /** Whether [ContentBlock.Resource] embedded context is supported in prompts. */
    val embeddedContext: Boolean = false,
)

/**
 * An authentication method the agent supports.
 */
@Serializable
public data class AuthMethod(
    /** Stable identifier passed to `authenticate`. */
    val id: String,
    /** Human-readable name. */
    val name: String,
    /** Optional description of the method. */
    val description: String? = null,
)

/**
 * Parameters of the `authenticate` request.
 */
@Serializable
public data class AuthenticateRequest(
    /** The identifier of the chosen [AuthMethod]. */
    val methodId: String,
)
