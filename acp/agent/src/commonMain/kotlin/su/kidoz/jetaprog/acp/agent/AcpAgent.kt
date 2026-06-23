package su.kidoz.jetaprog.acp.agent

import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AuthenticateRequest
import su.kidoz.jetaprog.acp.protocol.CancelNotification
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.LoadSessionRequest
import su.kidoz.jetaprog.acp.protocol.LoadSessionResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SetSessionModeRequest

/**
 * The Agent side of the Agent Client Protocol.
 *
 * Implement this to expose JetaProg (or any logic) as an agent that an external
 * editor can drive. Each method maps to an inbound client request; long-running
 * turns stream progress through the [AcpAgentSession] passed to [prompt].
 */
public interface AcpAgent {
    /**
     * Negotiates protocol version and capabilities.
     */
    public suspend fun initialize(request: InitializeRequest): InitializeResponse

    /**
     * Authenticates the client. The default treats authentication as a no-op.
     */
    public suspend fun authenticate(request: AuthenticateRequest) {
        // No authentication required by default.
    }

    /**
     * Creates a new session.
     */
    public suspend fun newSession(request: NewSessionRequest): NewSessionResponse

    /**
     * Loads a previously created session. The default rejects the request.
     */
    public suspend fun loadSession(request: LoadSessionRequest): LoadSessionResponse =
        throw AcpRequestException(AcpError.METHOD_NOT_FOUND, "session/load is not supported")

    /**
     * Runs a prompt turn, streaming updates through [session], and returns the
     * stop reason once the turn ends.
     */
    public suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse

    /**
     * Switches the active mode of a session. The default does nothing.
     */
    public suspend fun setMode(request: SetSessionModeRequest) {
        // Modes are optional.
    }

    /**
     * Handles a cancellation request for an in-flight turn. The default does nothing.
     */
    public suspend fun cancel(notification: CancelNotification) {
        // Cancellation is cooperative and optional.
    }
}
