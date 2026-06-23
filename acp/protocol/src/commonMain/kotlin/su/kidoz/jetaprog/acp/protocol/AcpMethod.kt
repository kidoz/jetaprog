package su.kidoz.jetaprog.acp.protocol

/**
 * Canonical method and notification names defined by the Agent Client Protocol.
 */
public object AcpMethod {
    /** The current ACP wire protocol version negotiated during [INITIALIZE]. */
    public const val PROTOCOL_VERSION: Int = 1

    // Lifecycle (client -> agent)

    /** Negotiates protocol version and capabilities. */
    public const val INITIALIZE: String = "initialize"

    /** Authenticates the client using one of the agent's advertised methods. */
    public const val AUTHENTICATE: String = "authenticate"

    // Session management (client -> agent)

    /** Creates a new conversation session. */
    public const val SESSION_NEW: String = "session/new"

    /** Loads a previously created session. */
    public const val SESSION_LOAD: String = "session/load"

    /** Sends a user prompt and runs a turn until a stop reason is reached. */
    public const val SESSION_PROMPT: String = "session/prompt"

    /** Switches the active mode of a session. */
    public const val SESSION_SET_MODE: String = "session/set_mode"

    /** Notification: requests cancellation of the in-flight turn. */
    public const val SESSION_CANCEL: String = "session/cancel"

    /** Notification: streams incremental updates from the agent to the client. */
    public const val SESSION_UPDATE: String = "session/update"

    // Client-provided capabilities (agent -> client)

    /** Asks the client to authorize a tool call. */
    public const val SESSION_REQUEST_PERMISSION: String = "session/request_permission"

    /** Reads a text file through the client's file system. */
    public const val FS_READ_TEXT_FILE: String = "fs/read_text_file"

    /** Writes a text file through the client's file system. */
    public const val FS_WRITE_TEXT_FILE: String = "fs/write_text_file"

    /** Creates a terminal managed by the client. */
    public const val TERMINAL_CREATE: String = "terminal/create"

    /** Retrieves the current output of a client terminal. */
    public const val TERMINAL_OUTPUT: String = "terminal/output"

    /** Waits for a client terminal to exit. */
    public const val TERMINAL_WAIT_FOR_EXIT: String = "terminal/wait_for_exit"

    /** Kills a running client terminal. */
    public const val TERMINAL_KILL: String = "terminal/kill"

    /** Releases a client terminal and its resources. */
    public const val TERMINAL_RELEASE: String = "terminal/release"
}
