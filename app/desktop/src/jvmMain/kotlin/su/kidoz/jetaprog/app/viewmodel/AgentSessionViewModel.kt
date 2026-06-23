package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.client.StdioAcpTransport
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.FileSystemCapability
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.PermissionOption
import su.kidoz.jetaprog.acp.protocol.PermissionOutcome
import su.kidoz.jetaprog.acp.protocol.PlanEntry
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/** The author of an entry in the agent conversation. */
public enum class AgentMessageRole {
    /** A message typed by the user. */
    USER,

    /** User-visible text streamed by the agent. */
    AGENT,

    /** Internal reasoning streamed by the agent. */
    THOUGHT,

    /** A local status or error line. */
    SYSTEM,
}

/** A single entry in the agent conversation transcript. */
public data class AgentMessage(
    /** Who produced the entry. */
    val role: AgentMessageRole,
    /** The textual content. */
    val text: String,
)

/** A tool call surfaced by the agent during a turn. */
public data class AgentToolCallView(
    /** The tool call identifier. */
    val id: String,
    /** A short human-readable title. */
    val title: String,
    /** The tool category, if known. */
    val kind: String?,
    /** The current status, if known. */
    val status: String?,
)

/** A pending permission request awaiting the user's decision. */
public data class PermissionPrompt(
    /** A description of the tool call awaiting authorization. */
    val title: String,
    /** The options the user may choose between. */
    val options: List<PermissionOption>,
)

/** State of the agent tool window. */
public data class AgentSessionState(
    /** The command used to launch the agent. */
    val agentCommand: String = "",
    /** Whether a connection attempt is in progress. */
    val connecting: Boolean = false,
    /** Whether a session is established. */
    val connected: Boolean = false,
    /** The agent's reported name, once connected. */
    val agentName: String? = null,
    /** The active session identifier. */
    val sessionId: String? = null,
    /** Whether a prompt turn is in flight. */
    val isRunning: Boolean = false,
    /** The conversation transcript. */
    val messages: List<AgentMessage> = emptyList(),
    /** Active tool calls for the current turn. */
    val toolCalls: List<AgentToolCallView> = emptyList(),
    /** The agent's current execution plan. */
    val plan: List<PlanEntry> = emptyList(),
    /** A permission request awaiting a decision, if any. */
    val pendingPermission: PermissionPrompt? = null,
    /** The most recent error message, if any. */
    val error: String? = null,
)

/**
 * Drives a single Agent Client Protocol session from the editor side.
 *
 * Connects to an external coding agent, streams its updates into a conversation
 * transcript, and services the agent's file-system and permission callbacks by
 * implementing [AcpClientHandler].
 *
 * @param projectPath the workspace root used as the session working directory.
 * @param fileSystem the file system used to satisfy the agent's file-system requests.
 * @param defaultAgentCommand a pre-filled launch command shown in the UI.
 */
public class AgentSessionViewModel(
    private val projectPath: String,
    private val fileSystem: FileSystem,
    defaultAgentCommand: String = "",
) : Disposable,
    AcpClientHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AgentSessionState(agentCommand = defaultAgentCommand))

    /** The observable tool window state. */
    public val state: StateFlow<AgentSessionState> = _state.asStateFlow()

    private var transport: StdioAcpTransport? = null
    private var client: AcpClient? = null
    private var updatesJob: Job? = null
    private var pendingPermission: CompletableDeferred<RequestPermissionResponse>? = null

    /** Updates the launch command shown in the UI. */
    public fun setAgentCommand(command: String) {
        _state.update { it.copy(agentCommand = command) }
    }

    /** Connects to the configured agent and starts a session. */
    public fun connect() {
        if (_state.value.connecting || _state.value.connected) return
        val command = _state.value.agentCommand.trim()
        if (command.isEmpty()) {
            _state.update { it.copy(error = "Enter an agent command to connect") }
            return
        }

        _state.update { it.copy(connecting = true, error = null) }
        scope.launch { openConnection(command) }
    }

    private suspend fun openConnection(command: String) {
        try {
            val stdioTransport =
                StdioAcpTransport(
                    StdioAgentConfig(
                        command = command.split(Regex("\\s+")),
                        workingDirectory = projectPath,
                    ),
                )
            stdioTransport.start()
            transport = stdioTransport

            val acpClient =
                AcpClient(
                    transport = stdioTransport,
                    scope = scope,
                    capabilities =
                        ClientCapabilities(
                            fs = FileSystemCapability(readTextFile = true, writeTextFile = true),
                            terminal = false,
                        ),
                    clientInfo = Implementation("JetaProg", "1.0"),
                    handler = this,
                )
            client = acpClient

            updatesJob =
                scope.launch {
                    acpClient.sessionUpdates.collect { notification -> applyUpdate(notification.update) }
                }

            val initialize = acpClient.initialize()
            val session = acpClient.newSession(cwd = projectPath)
            _state.update {
                it.copy(
                    connecting = false,
                    connected = true,
                    agentName = initialize.agentInfo?.name,
                    sessionId = session.sessionId,
                    messages = it.messages + AgentMessage(AgentMessageRole.SYSTEM, "Connected to agent."),
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(connecting = false, connected = false, error = e.message ?: "Failed to connect")
            }
        }
    }

    /** Sends a user prompt and runs a turn. */
    public fun sendPrompt(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        val acpClient = client ?: return
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.isRunning) return

        _state.update {
            it.copy(
                isRunning = true,
                plan = emptyList(),
                toolCalls = emptyList(),
                messages = it.messages + AgentMessage(AgentMessageRole.USER, message),
            )
        }

        scope.launch {
            try {
                val response = acpClient.promptText(sessionId, message)
                _state.update {
                    it.copy(
                        isRunning = false,
                        messages =
                            it.messages +
                                AgentMessage(AgentMessageRole.SYSTEM, "Turn ended: ${response.stopReason.value}"),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isRunning = false, error = e.message ?: "Prompt failed")
                }
            }
        }
    }

    /** Requests cancellation of the in-flight turn. */
    public fun cancelTurn() {
        val acpClient = client ?: return
        val sessionId = _state.value.sessionId ?: return
        scope.launch { acpClient.cancel(sessionId) }
    }

    /** Resolves the pending permission request with the chosen option, or cancels it. */
    public fun resolvePermission(optionId: String?) {
        val deferred = pendingPermission ?: return
        pendingPermission = null
        val outcome =
            if (optionId != null) PermissionOutcome.Selected(optionId) else PermissionOutcome.Cancelled
        deferred.complete(RequestPermissionResponse(outcome))
        _state.update { it.copy(pendingPermission = null) }
    }

    /** Disconnects from the agent and clears session state. */
    public fun disconnect() {
        scope.launch {
            updatesJob?.cancel()
            client?.close()
            client = null
            transport = null
            _state.update {
                it.copy(connected = false, sessionId = null, isRunning = false, agentName = null)
            }
        }
    }

    private fun applyUpdate(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                appendChunk(AgentMessageRole.AGENT, update.content)
            }

            is SessionUpdate.AgentThoughtChunk -> {
                appendChunk(AgentMessageRole.THOUGHT, update.content)
            }

            is SessionUpdate.UserMessageChunk -> {
                appendChunk(AgentMessageRole.USER, update.content)
            }

            is SessionUpdate.Plan -> {
                _state.update { it.copy(plan = update.entries) }
            }

            is SessionUpdate.ToolCallStart -> {
                upsertToolCall(
                    AgentToolCallView(update.toolCallId, update.title, update.kind?.value, update.status?.value),
                )
            }

            is SessionUpdate.ToolCallProgress -> {
                updateToolCall(update.toolCallId, update.status?.value, update.title)
            }

            is SessionUpdate.CurrentModeUpdate -> {
                _state.update {
                    it.copy(
                        messages =
                            it.messages + AgentMessage(AgentMessageRole.SYSTEM, "Mode: ${update.currentModeId}"),
                    )
                }
            }

            is SessionUpdate.AvailableCommandsUpdate -> {}
        }
    }

    private fun appendChunk(
        role: AgentMessageRole,
        content: ContentBlock,
    ) {
        val text = (content as? ContentBlock.Text)?.text ?: return
        _state.update { state ->
            val last = state.messages.lastOrNull()
            val merged =
                if (last != null && last.role == role) {
                    state.messages.dropLast(1) + last.copy(text = last.text + text)
                } else {
                    state.messages + AgentMessage(role, text)
                }
            state.copy(messages = merged)
        }
    }

    private fun upsertToolCall(view: AgentToolCallView) {
        _state.update { state ->
            val existing = state.toolCalls.indexOfFirst { it.id == view.id }
            val updated =
                if (existing >= 0) {
                    state.toolCalls.toMutableList().apply { this[existing] = view }
                } else {
                    state.toolCalls + view
                }
            state.copy(toolCalls = updated)
        }
    }

    private fun updateToolCall(
        id: String,
        status: String?,
        title: String?,
    ) {
        _state.update { state ->
            state.copy(
                toolCalls =
                    state.toolCalls.map { call ->
                        if (call.id == id) {
                            call.copy(status = status ?: call.status, title = title ?: call.title)
                        } else {
                            call
                        }
                    },
            )
        }
    }

    // ------------------------------------------------------------------------
    // AcpClientHandler
    // ------------------------------------------------------------------------

    override suspend fun readTextFile(request: ReadTextFileRequest): ReadTextFileResponse {
        val text = fileSystem.readText(request.path).getOrElse { throw it }
        val sliced =
            if (request.line != null || request.limit != null) {
                val lines = text.lines()
                val start = ((request.line ?: 1) - 1).coerceIn(0, lines.size)
                val end = request.limit?.let { (start + it).coerceAtMost(lines.size) } ?: lines.size
                lines.subList(start, end).joinToString("\n")
            } else {
                text
            }
        return ReadTextFileResponse(sliced)
    }

    override suspend fun writeTextFile(request: WriteTextFileRequest) {
        fileSystem.writeText(request.path, request.content).getOrElse { throw it }
    }

    override suspend fun requestPermission(request: RequestPermissionRequest): RequestPermissionResponse {
        val deferred = CompletableDeferred<RequestPermissionResponse>()
        pendingPermission = deferred
        _state.update {
            it.copy(
                pendingPermission =
                    PermissionPrompt(
                        title = request.toolCall.title ?: "Tool call",
                        options = request.options,
                    ),
            )
        }
        return deferred.await()
    }

    override fun dispose() {
        scope.cancel()
    }
}
