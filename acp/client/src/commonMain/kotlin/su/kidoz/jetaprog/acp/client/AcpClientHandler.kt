package su.kidoz.jetaprog.acp.client

import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.CreateTerminalRequest
import su.kidoz.jetaprog.acp.protocol.CreateTerminalResponse
import su.kidoz.jetaprog.acp.protocol.KillTerminalRequest
import su.kidoz.jetaprog.acp.protocol.PermissionOutcome
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.ReleaseTerminalRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.TerminalOutputRequest
import su.kidoz.jetaprog.acp.protocol.TerminalOutputResponse
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitRequest
import su.kidoz.jetaprog.acp.protocol.WaitForTerminalExitResponse
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest

/**
 * Implements the client-side capabilities an agent may invoke during a session:
 * file-system access, permission prompts and terminals.
 *
 * The default implementations reject every request; override the methods that
 * correspond to the capabilities advertised in [AcpClient]'s configuration.
 */
public interface AcpClientHandler {
    /**
     * Reads a text file on the agent's behalf.
     */
    public suspend fun readTextFile(request: ReadTextFileRequest): ReadTextFileResponse =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "fs/read_text_file is not supported")

    /**
     * Writes a text file on the agent's behalf.
     */
    public suspend fun writeTextFile(request: WriteTextFileRequest): Unit =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "fs/write_text_file is not supported")

    /**
     * Asks the user to authorize a tool call.
     *
     * The default denies by returning a cancelled outcome.
     */
    public suspend fun requestPermission(request: RequestPermissionRequest): RequestPermissionResponse =
        RequestPermissionResponse(PermissionOutcome.Cancelled)

    /**
     * Creates a terminal on the agent's behalf.
     */
    public suspend fun createTerminal(request: CreateTerminalRequest): CreateTerminalResponse =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "terminal/create is not supported")

    /**
     * Returns the current output of a terminal.
     */
    public suspend fun terminalOutput(request: TerminalOutputRequest): TerminalOutputResponse =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "terminal/output is not supported")

    /**
     * Waits for a terminal to exit.
     */
    public suspend fun waitForTerminalExit(request: WaitForTerminalExitRequest): WaitForTerminalExitResponse =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "terminal/wait_for_exit is not supported")

    /**
     * Kills a running terminal.
     */
    public suspend fun killTerminal(request: KillTerminalRequest): Unit =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "terminal/kill is not supported")

    /**
     * Releases a terminal and its resources.
     */
    public suspend fun releaseTerminal(request: ReleaseTerminalRequest): Unit =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "terminal/release is not supported")
}
