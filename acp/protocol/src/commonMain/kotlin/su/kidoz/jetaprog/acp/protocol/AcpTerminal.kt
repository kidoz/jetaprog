package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable

/**
 * Parameters of the `terminal/create` request.
 */
@Serializable
public data class CreateTerminalRequest(
    /** The session the terminal belongs to. */
    val sessionId: String,
    /** The executable to run. */
    val command: String,
    /** Arguments passed to the executable. */
    val args: List<String> = emptyList(),
    /** Working directory for the command. */
    val cwd: String? = null,
    /** Environment variables for the command. */
    val env: List<EnvVariable> = emptyList(),
    /** Maximum number of output bytes to retain. */
    val outputByteLimit: Int? = null,
)

/**
 * Result of the `terminal/create` request.
 */
@Serializable
public data class CreateTerminalResponse(
    /** The identifier of the created terminal. */
    val terminalId: String,
)

/**
 * Parameters of the `terminal/output` request.
 */
@Serializable
public data class TerminalOutputRequest(
    /** The session the terminal belongs to. */
    val sessionId: String,
    /** The terminal to read. */
    val terminalId: String,
)

/**
 * Result of the `terminal/output` request.
 */
@Serializable
public data class TerminalOutputResponse(
    /** The output captured so far. */
    val output: String,
    /** Whether the output was truncated to the byte limit. */
    val truncated: Boolean = false,
    /** The exit status, when the command has finished. */
    val exitStatus: TerminalExitStatus? = null,
)

/**
 * The exit status of a terminal command.
 */
@Serializable
public data class TerminalExitStatus(
    /** The process exit code, when it exited normally. */
    val exitCode: Int? = null,
    /** The terminating signal, when killed by a signal. */
    val signal: String? = null,
)

/**
 * Parameters of the `terminal/wait_for_exit` request.
 */
@Serializable
public data class WaitForTerminalExitRequest(
    /** The session the terminal belongs to. */
    val sessionId: String,
    /** The terminal to wait on. */
    val terminalId: String,
)

/**
 * Result of the `terminal/wait_for_exit` request.
 */
@Serializable
public data class WaitForTerminalExitResponse(
    /** The process exit code, when it exited normally. */
    val exitCode: Int? = null,
    /** The terminating signal, when killed by a signal. */
    val signal: String? = null,
)

/**
 * Parameters of the `terminal/kill` request.
 */
@Serializable
public data class KillTerminalRequest(
    /** The session the terminal belongs to. */
    val sessionId: String,
    /** The terminal to kill. */
    val terminalId: String,
)

/**
 * Parameters of the `terminal/release` request.
 */
@Serializable
public data class ReleaseTerminalRequest(
    /** The session the terminal belongs to. */
    val sessionId: String,
    /** The terminal to release. */
    val terminalId: String,
)
