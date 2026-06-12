package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable

/**
 * Service for terminal operations.
 */
public interface TerminalService {
    /**
     * The currently active terminal, or null if none.
     */
    public val activeTerminal: Terminal?

    /**
     * All open terminals.
     */
    public val terminals: List<Terminal>

    /**
     * Creates a new terminal.
     * @param options Terminal creation options
     * @return The created terminal
     */
    public suspend fun createTerminal(options: TerminalOptions = TerminalOptions()): Terminal

    /**
     * Registers a listener for terminal creation.
     */
    public fun onDidOpenTerminal(handler: suspend (Terminal) -> Unit): Disposable

    /**
     * Registers a listener for terminal closure.
     */
    public fun onDidCloseTerminal(handler: suspend (Terminal) -> Unit): Disposable

    /**
     * Registers a listener for active terminal changes.
     */
    public fun onDidChangeActiveTerminal(handler: suspend (Terminal?) -> Unit): Disposable

    /**
     * Executes a command in a new or existing terminal.
     * @param command The command to execute
     * @param options Execution options
     * @return The terminal where the command is running
     */
    public suspend fun executeCommand(
        command: String,
        options: ExecuteInTerminalOptions = ExecuteInTerminalOptions(),
    ): Terminal
}

/**
 * Options for creating a terminal.
 */
public data class TerminalOptions(
    /**
     * The name of the terminal.
     */
    val name: String? = null,
    /**
     * The shell path to use.
     */
    val shellPath: String? = null,
    /**
     * Shell arguments.
     */
    val shellArgs: List<String> = emptyList(),
    /**
     * Working directory.
     */
    val cwd: String? = null,
    /**
     * Environment variables.
     */
    val env: Map<String, String> = emptyMap(),
    /**
     * Whether to hide from the user.
     */
    val hideFromUser: Boolean = false,
)

/**
 * Options for executing a command in a terminal.
 */
public data class ExecuteInTerminalOptions(
    /**
     * Whether to create a new terminal or reuse an existing one.
     */
    val newTerminal: Boolean = true,
    /**
     * The name for a new terminal.
     */
    val name: String? = null,
    /**
     * Working directory.
     */
    val cwd: String? = null,
    /**
     * Environment variables.
     */
    val env: Map<String, String> = emptyMap(),
    /**
     * Whether to focus the terminal.
     */
    val focus: Boolean = true,
)

/**
 * Represents a terminal instance.
 */
public interface Terminal {
    /**
     * The name of the terminal.
     */
    public val name: String

    /**
     * The process ID of the terminal shell.
     */
    public val processId: Long?

    /**
     * The exit status of the terminal, if it has exited.
     */
    public val exitStatus: Int?

    /**
     * Flow of output from the terminal.
     */
    public val output: Flow<String>

    /**
     * Sends text to the terminal.
     * @param text The text to send
     * @param addNewLine Whether to add a newline
     */
    public suspend fun sendText(
        text: String,
        addNewLine: Boolean = true,
    )

    /**
     * Shows the terminal.
     * @param preserveFocus Whether to preserve focus on the current element
     */
    public fun show(preserveFocus: Boolean = false)

    /**
     * Hides the terminal.
     */
    public fun hide()

    /**
     * Disposes the terminal.
     */
    public fun dispose()
}
