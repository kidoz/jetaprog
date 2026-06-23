package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable

/**
 * Configuration of an MCP server the agent should connect to for the session.
 */
@Serializable
public data class McpServer(
    /** A unique name for the server. */
    val name: String,
    /** The executable to launch. */
    val command: String,
    /** Arguments passed to the executable. */
    val args: List<String> = emptyList(),
    /** Environment variables for the server process. */
    val env: List<EnvVariable> = emptyList(),
)

/**
 * A single environment variable, used by MCP servers and terminals.
 */
@Serializable
public data class EnvVariable(
    /** The variable name. */
    val name: String,
    /** The variable value. */
    val value: String,
)

/**
 * Parameters of the `session/new` request.
 */
@Serializable
public data class NewSessionRequest(
    /** The working directory for the session, as an absolute path. */
    val cwd: String,
    /** MCP servers the agent may use during the session. */
    val mcpServers: List<McpServer> = emptyList(),
)

/**
 * Result of the `session/new` request.
 */
@Serializable
public data class NewSessionResponse(
    /** The newly created session identifier. */
    val sessionId: String,
    /** The initial mode state, when the agent supports modes. */
    val modes: SessionModeState? = null,
)

/**
 * Parameters of the `session/load` request.
 */
@Serializable
public data class LoadSessionRequest(
    /** The session to load. */
    val sessionId: String,
    /** The working directory for the session. */
    val cwd: String,
    /** MCP servers the agent may use during the session. */
    val mcpServers: List<McpServer> = emptyList(),
)

/**
 * Result of the `session/load` request.
 */
@Serializable
public data class LoadSessionResponse(
    /** The current mode state, when the agent supports modes. */
    val modes: SessionModeState? = null,
)

/**
 * The set of available modes and the currently active mode for a session.
 */
@Serializable
public data class SessionModeState(
    /** The identifier of the currently active mode. */
    val currentModeId: String,
    /** All modes the session can switch to. */
    val availableModes: List<SessionMode> = emptyList(),
)

/**
 * A selectable session mode, such as `ask` or `code`.
 */
@Serializable
public data class SessionMode(
    /** Stable mode identifier. */
    val id: String,
    /** Human-readable mode name. */
    val name: String,
    /** Optional description of the mode. */
    val description: String? = null,
)

/**
 * Parameters of the `session/set_mode` request.
 */
@Serializable
public data class SetSessionModeRequest(
    /** The session whose mode should change. */
    val sessionId: String,
    /** The identifier of the mode to activate. */
    val modeId: String,
)

/**
 * Parameters of the `session/prompt` request.
 */
@Serializable
public data class PromptRequest(
    /** The target session. */
    val sessionId: String,
    /** The user's message as a sequence of content blocks. */
    val prompt: List<ContentBlock>,
)

/**
 * Result of the `session/prompt` request, returned when the turn ends.
 */
@Serializable
public data class PromptResponse(
    /** Why the turn stopped. */
    val stopReason: StopReason,
)

/**
 * Parameters of the `session/cancel` notification.
 */
@Serializable
public data class CancelNotification(
    /** The session whose in-flight turn should be cancelled. */
    val sessionId: String,
)
