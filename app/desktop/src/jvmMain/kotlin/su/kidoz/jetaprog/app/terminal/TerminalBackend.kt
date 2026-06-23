package su.kidoz.jetaprog.app.terminal

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable

/**
 * Output emitted by a terminal backend.
 */
public sealed interface TerminalBackendOutput {
    /**
     * Raw decoded terminal output.
     */
    public data class Text(
        val value: String,
    ) : TerminalBackendOutput

    /**
     * Terminal process has exited.
     */
    public data class Exited(
        val exitCode: Int,
    ) : TerminalBackendOutput

    /**
     * Terminal backend failed.
     */
    public data class Error(
        val message: String,
    ) : TerminalBackendOutput
}

/**
 * Backend for an interactive terminal session.
 */
public interface TerminalBackend : Disposable {
    /**
     * Stream of terminal backend output.
     */
    public val output: Flow<TerminalBackendOutput>

    /**
     * True while the terminal process is alive.
     */
    public val isAlive: Boolean

    /**
     * Write raw input bytes to the terminal.
     */
    public suspend fun write(bytes: ByteArray)

    /**
     * Resize the terminal viewport.
     */
    public fun resize(
        columns: Int,
        rows: Int,
    )

    /**
     * Terminate the terminal process.
     */
    public fun kill()
}
