package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.client.StdioAcpTransport
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AvailableCommand
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.FileSystemCapability
import su.kidoz.jetaprog.acp.protocol.HttpHeader
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.McpServer
import su.kidoz.jetaprog.acp.protocol.PermissionOption
import su.kidoz.jetaprog.acp.protocol.PermissionOptionKind
import su.kidoz.jetaprog.acp.protocol.PermissionOutcome
import su.kidoz.jetaprog.acp.protocol.PermissionToolCall
import su.kidoz.jetaprog.acp.protocol.PlanEntry
import su.kidoz.jetaprog.acp.protocol.ReadTextFileRequest
import su.kidoz.jetaprog.acp.protocol.ReadTextFileResponse
import su.kidoz.jetaprog.acp.protocol.RequestPermissionRequest
import su.kidoz.jetaprog.acp.protocol.RequestPermissionResponse
import su.kidoz.jetaprog.acp.protocol.SessionMode
import su.kidoz.jetaprog.acp.protocol.SessionModeState
import su.kidoz.jetaprog.acp.protocol.SessionModelState
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.ToolCallContent
import su.kidoz.jetaprog.acp.protocol.ToolCallStatus
import su.kidoz.jetaprog.acp.protocol.ToolKind
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.app.agent.AgentBringOutcome
import su.kidoz.jetaprog.app.agent.AgentBroughtChange
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentFileSnapshots
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.agent.AgentMessageCheckpoints
import su.kidoz.jetaprog.app.agent.AgentPrefs
import su.kidoz.jetaprog.app.agent.AgentPrefsStore
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.agent.AgentWorktreeLauncher
import su.kidoz.jetaprog.app.agent.CheckpointSkip
import su.kidoz.jetaprog.app.agent.FileSnapshot
import su.kidoz.jetaprog.app.agent.checkpointNotice
import su.kidoz.jetaprog.app.agent.toModelOption
import su.kidoz.jetaprog.app.ui.agent.AGENTS_FILE
import su.kidoz.jetaprog.app.ui.agent.AgentCommand
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentUiState
import su.kidoz.jetaprog.app.ui.agent.ApprovalChoice
import su.kidoz.jetaprog.app.ui.agent.ApprovalChoiceRole
import su.kidoz.jetaprog.app.ui.agent.BRING_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_DIRTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_FAILED_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_FROM_WORKTREE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_MAIN_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_PENDING_NOTICE
import su.kidoz.jetaprog.app.ui.agent.BRING_UNAVAILABLE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.Block
import su.kidoz.jetaprog.app.ui.agent.ComposerAttachment
import su.kidoz.jetaprog.app.ui.agent.DiffDecision
import su.kidoz.jetaprog.app.ui.agent.DiffLine
import su.kidoz.jetaprog.app.ui.agent.DiffLineKind
import su.kidoz.jetaprog.app.ui.agent.EditorSnippet
import su.kidoz.jetaprog.app.ui.agent.GOAL_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_CAP_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_FAILED_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_PLAN_NOTICE
import su.kidoz.jetaprog.app.ui.agent.GOAL_ROUND_LIMIT
import su.kidoz.jetaprog.app.ui.agent.GoalCommand
import su.kidoz.jetaprog.app.ui.agent.MentionMatch
import su.kidoz.jetaprog.app.ui.agent.ModeOption
import su.kidoz.jetaprog.app.ui.agent.ModelOption
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.app.ui.agent.PlanStep
import su.kidoz.jetaprog.app.ui.agent.PlanStepStatus
import su.kidoz.jetaprog.app.ui.agent.Presence
import su.kidoz.jetaprog.app.ui.agent.QueuedFollowUp
import su.kidoz.jetaprog.app.ui.agent.REVIEW_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.REVIEW_EMPTY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.SessionRestoration
import su.kidoz.jetaprog.app.ui.agent.ToolStatus
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_ALREADY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_OPENING_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_UNAVAILABLE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.bringableRelativePaths
import su.kidoz.jetaprog.app.ui.agent.chipLabel
import su.kidoz.jetaprog.app.ui.agent.elapsedMillis
import su.kidoz.jetaprog.app.ui.agent.goalCheckLabel
import su.kidoz.jetaprog.app.ui.agent.goalCheckPrompt
import su.kidoz.jetaprog.app.ui.agent.goalContinueLabel
import su.kidoz.jetaprog.app.ui.agent.goalContinuePrompt
import su.kidoz.jetaprog.app.ui.agent.goalEvidence
import su.kidoz.jetaprog.app.ui.agent.goalRefusal
import su.kidoz.jetaprog.app.ui.agent.goalResumeLabel
import su.kidoz.jetaprog.app.ui.agent.goalResumePrompt
import su.kidoz.jetaprog.app.ui.agent.goalWorkPrompt
import su.kidoz.jetaprog.app.ui.agent.instructionChip
import su.kidoz.jetaprog.app.ui.agent.instructionText
import su.kidoz.jetaprog.app.ui.agent.isPlanMode
import su.kidoz.jetaprog.app.ui.agent.isSafeRelative
import su.kidoz.jetaprog.app.ui.agent.judgeGoal
import su.kidoz.jetaprog.app.ui.agent.mentionQuery
import su.kidoz.jetaprog.app.ui.agent.parseBringCommand
import su.kidoz.jetaprog.app.ui.agent.parseGoalCommand
import su.kidoz.jetaprog.app.ui.agent.parseReviewCommand
import su.kidoz.jetaprog.app.ui.agent.parseWorktreeCommand
import su.kidoz.jetaprog.app.ui.agent.persistedGoal
import su.kidoz.jetaprog.app.ui.agent.promptWithContext
import su.kidoz.jetaprog.app.ui.agent.promptWithInstructions
import su.kidoz.jetaprog.app.ui.agent.restoredGoal
import su.kidoz.jetaprog.app.ui.agent.restoredGoalNotice
import su.kidoz.jetaprog.app.ui.agent.reviewFiles
import su.kidoz.jetaprog.app.ui.agent.sessionTitle
import su.kidoz.jetaprog.app.ui.agent.summarizeSession
import su.kidoz.jetaprog.app.ui.agent.withoutMentionToken
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.WorkspacePathException
import su.kidoz.jetaprog.platform.filesystem.WorkspacePathGuard
import java.io.File
import java.util.UUID

/**
 * Drives the AI agent surface from a single Agent Client Protocol session.
 *
 * Connects to an external coding agent on demand, maps its streamed updates into
 * the surface's [Block] model (text, tool-call, inline-diff and approval cards),
 * services the agent's file-system and permission callbacks via [AcpClientHandler],
 * and persists per-project preferences.
 *
 * @param projectPath the workspace root used as the session working directory.
 * @param fileSystem the file system used to satisfy the agent's file requests.
 * @param ideMcpEndpoint supplies the IDE's embedded MCP server so the agent can use the
 *   IDE's own tools; returns null when the server is not running.
 * @param defaultAgentCommand the command used to launch the agent process.
 * @param transportFactory launches the configured agent transport; injectable for protocol testing.
 * @param editorContext reads the active editor file and selection when the user attaches context.
 * @param mentionSearch finds project files for an `@` query. Blank queries are not passed.
 * @param openInIdeDiff opens an absolute project path in the version-control diff viewer.
 * @param isEditorDirty reports an unsaved editor buffer for an absolute path. Those files are skipped
 *   by a checkpoint restore.
 * @param onWorkspaceFilesChanged tells the editor and Git panel that these absolute paths were rewritten.
 * @param checkout Whether this session edits the main checkout or a linked worktree.
 * @param worktreeLauncher Creates the linked checkout for `/worktree`. Null refuses the command.
 * @param onWorktreeReady Tells the host to show the second session. Called again to switch back to it.
 * @param onBringBack Copies this worktree's files into the main checkout. Null refuses `/bring`.
 */
public class AgentSessionViewModel(
    private val projectPath: String,
    private val fileSystem: FileSystem,
    private val ideMcpEndpoint: () -> IdeMcpEndpoint? = { null },
    defaultAgentCommand: String = DEFAULT_CLAUDE_CODE_COMMAND,
    private val transportFactory: suspend (StdioAgentConfig) -> AcpTransport = { config ->
        StdioAcpTransport(config).also { it.start() }
    },
    private val editorContext: () -> EditorSnippet? = { null },
    private val mentionSearch: suspend (String) -> List<MentionMatch> = { emptyList() },
    private val openInIdeDiff: (String) -> Unit = {},
    private val isEditorDirty: (String) -> Boolean = { false },
    private val onWorkspaceFilesChanged: (List<String>) -> Unit = {},
    private val checkout: AgentCheckout = AgentCheckout.MAIN,
    private val worktreeLauncher: AgentWorktreeLauncher? = null,
    private val onWorktreeReady: (AgentWorktree) -> Unit = {},
    private val onBringBack: (suspend (List<AgentBroughtChange>) -> AgentBringOutcome)? = null,
) : Disposable,
    AcpClientHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val agentCommand = defaultAgentCommand
    private val prefsStore = AgentPrefsStore(fileSystem, projectPath)
    private val historyStore = AgentHistoryStore(fileSystem, projectPath)
    private val pathGuard = WorkspacePathGuard { projectPath }

    private val _state = MutableStateFlow(AgentUiState())

    /** The observable agent surface state. */
    public val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var client: AcpClient? = null
    private var sessionId: String? = null

    /**
     * Plan, mode, and commands that arrived before [sessionId] was known.
     * Flushed once `session/new` returns that session.
     */
    private var pendingSurface: PendingSurface? = null

    /** The transcript's agent session, retained when its connection closes. */
    private var conversationSessionId: String? = null
    private var recordId: String? = null
    private var sessionChangeJob: Job? = null
    private var seq = 0
    private val pendingApprovals = mutableMapOf<String, PendingApproval>()

    /** Serializes the claim of a new turn without blocking cancellation intents. */
    private val sendMutex = Mutex()

    /** Keeps overlapping history writes in the order they were captured. */
    private val historyMutex = Mutex()

    /** Invalidates completions belonging to an abandoned turn. */
    private var turnGeneration = 0

    /** Drops mention results that belong to an older `@` query. */
    private var mentionGeneration = 0

    /** When true, [endTurn] sends the queued follow-up. Stop clears this. */
    private var deliverQueuedPrompt = false

    /** When true, the turn that just ended was the goal check rather than a work round. */
    private var goalChecking = false

    /** Automatic continuation rounds since the goal was set or resumed. */
    private var goalAutoRounds = 0

    /** Turns before this index are not evidence for the current objective. */
    private var goalBaseline = 0

    /** The linked checkout this main session already opened. */
    private var linkedWorktree: AgentWorktree? = null

    /** True while [worktreeLauncher] is creating the checkout. */
    private var worktreeOpening = false

    /** When true, cancel the running turn once no tool call is still running. */
    private var steerArmed = false

    /** Ends a stopped turn if the agent never answers the cancellation. */
    private var stopFallbackJob: Job? = null

    /** Pre-write file contents used to revert a single agent edit. */
    private val fileSnapshots = AgentFileSnapshots()

    /** File baselines taken at each user message. Not persisted. */
    private val checkpoints = AgentMessageCheckpoints()

    /** Paths whose edit permission was already granted for an upcoming write. */
    private val approvedEditPaths = mutableMapOf<String, Int>()

    /** Runs process teardown off the UI thread after [dispose]. */
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The model the user selected, persisted across sessions and agents. */
    private var preferredModelId: String? = null

    /** Completes once the on-disk preferences have been applied. */
    private val prefsLoaded = CompletableDeferred<AgentPrefs>()

    init {
        scope.launch {
            val prefs = prefsStore.load()
            preferredModelId = prefs.modelId
            _state.update {
                it.copy(
                    permissions = prefs.permissions,
                    docked = prefs.docked,
                )
            }
            prefsLoaded.complete(prefs)
        }
    }

    /** Dispatches a user [intent]. */
    public fun dispatch(intent: AgentIntent) {
        if (intent is AgentIntent.RestoreSession || intent == AgentIntent.NewChat) {
            sessionChangeJob?.cancel()
            sessionChangeJob = scope.launch { sendMutex.withLock { handle(intent) } }
        } else {
            scope.launch { handle(intent) }
        }
    }

    private suspend fun handle(intent: AgentIntent) {
        when (intent) {
            AgentIntent.EnsureConnected -> {
                sendMutex.withLock {
                    if (ensureConnected() && _state.value.restoration !is SessionRestoration.TranscriptOnly) {
                        client?.let { ensureSession(it) }
                    }
                }
            }

            is AgentIntent.Send -> {
                submit(intent.prompt, intent.context, steer = false)
            }

            is AgentIntent.Steer -> {
                submit(intent.prompt, emptyList(), steer = true)
            }

            AgentIntent.Stop -> {
                handleStop()
            }

            AgentIntent.NewChat -> {
                startNewChat()
            }

            AgentIntent.ShowHistory -> {
                _state.update { it.copy(sessionHistory = historyStore.list(), historyVisible = true) }
            }

            AgentIntent.HideHistory -> {
                _state.update { it.copy(historyVisible = false) }
            }

            is AgentIntent.RestoreSession -> {
                restoreSession(intent.recordId)
            }

            is AgentIntent.DeleteSession -> {
                deleteSession(intent.recordId)
            }

            is AgentIntent.ToggleToolCall -> {
                toggleTool(intent.blockId)
            }

            is AgentIntent.AcceptDiff -> {
                decideDiff(intent.blockId, accept = true)
            }

            is AgentIntent.RejectDiff -> {
                decideDiff(intent.blockId, accept = false)
            }

            is AgentIntent.OpenDiff -> {
                openDiff(intent.blockId)
            }

            is AgentIntent.RestoreCheckpoint -> {
                restoreCheckpoint(intent.turnId)
            }

            AgentIntent.AcceptAll -> {
                decideAll(accept = true)
            }

            AgentIntent.RevertAll -> {
                decideAll(accept = false)
            }

            is AgentIntent.SelectApproval -> {
                resolveApproval(intent.callId, intent.optionId)
            }

            is AgentIntent.SetPermission -> {
                _state.update { it.copy(permissions = it.permissions + (intent.kind to intent.policy)) }
                persist()
            }

            is AgentIntent.SetModel -> {
                setModel(intent.modelId)
            }

            is AgentIntent.SetMode -> {
                setMode(intent.modeId)
            }

            AgentIntent.ExpandToPerspective -> {
                _state.update { it.copy(docked = false) }
                persist()
            }

            AgentIntent.DockToToolWindow -> {
                _state.update { it.copy(docked = true) }
                persist()
            }

            is AgentIntent.SetComposerInput -> {
                updateComposerInput(intent.text)
            }

            AgentIntent.AttachCurrentFile -> {
                attachFromEditor(selection = false)
            }

            AgentIntent.AttachCurrentSelection -> {
                attachFromEditor(selection = true)
            }

            is AgentIntent.AttachMention -> {
                attachMention(intent.path, intent.label)
            }

            is AgentIntent.RemoveAttachment -> {
                removeAttachment(intent.path, intent.selection)
            }

            AgentIntent.RemoveQueuedPrompt -> {
                clearQueuedPrompt()
            }

            AgentIntent.PauseGoal -> {
                pauseGoal()
            }

            AgentIntent.ResumeGoal -> {
                resumeGoal(clearComposer = false)
            }

            AgentIntent.ClearGoal -> {
                clearGoal(clearComposer = false)
            }

            AgentIntent.ReplaceGoal -> {
                _state.update { it.copy(composerInput = "/goal ", goalNotice = null) }
            }

            AgentIntent.HideReview -> {
                _state.update { it.copy(reviewVisible = false) }
            }

            is AgentIntent.AcceptReviewFile -> {
                decideReviewFile(intent.key, accept = true)
            }

            is AgentIntent.DismissReviewFile -> {
                decideReviewFile(intent.key, accept = false)
            }
        }
    }

    // ------------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------------

    private val connectionMutex = Mutex()

    private suspend fun ensureConnected(): Boolean =
        connectionMutex.withLock {
            val current = client
            if (current != null && current.isOpen && _state.value.connection == AgentConnection.CONNECTED) return true
            if (current != null) resetConnection()
            if (agentCommand.isBlank()) {
                _state.update { it.copy(connection = AgentConnection.ERROR, error = "No agent command configured") }
                return false
            }
            _state.update { it.copy(connection = AgentConnection.CONNECTING, error = null) }
            try {
                openConnection()
                true
            } catch (e: CancellationException) {
                withContext(NonCancellable) { resetConnection() }
                _state.update { it.copy(connection = AgentConnection.DISCONNECTED) }
                throw e
            } catch (e: Exception) {
                // Keep the connection failure visible even if shutting down the transport also fails.
                resetConnection()
                _state.update {
                    it.copy(connection = AgentConnection.ERROR, error = e.message ?: "Failed to connect to agent")
                }
                false
            }
        }

    private suspend fun resetConnection() {
        disarmQueue()
        val openClient = client
        client = null
        sessionId = null
        clearSessionSurface()
        if (openClient != null) runCatching { openClient.close() }
    }

    private suspend fun openConnection() {
        val stdioTransport =
            transportFactory(
                StdioAgentConfig(
                    command = agentCommand.split(Regex("\\s+")),
                    workingDirectory = projectPath,
                ),
            )

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

        val initialize =
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { acpClient.initialize() }
                ?: throw IllegalStateException("The agent did not respond to initialize")
        _state.update {
            it.copy(
                connection = AgentConnection.CONNECTED,
                agentName = initialize.agentInfo?.name,
                error = null,
            )
        }
    }

    /** Returns the active session, creating one after a chat reset or restore. */
    private suspend fun ensureSession(acpClient: AcpClient): String? {
        sessionId?.let { return it }
        if (_state.value.turns.isNotEmpty() && _state.value.restoration !is SessionRestoration.TranscriptOnly) {
            val record = conversationRecord()
            if (record != null) restoreAgentSession(acpClient, record)
            return sessionId
        }
        return try {
            val session =
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    acpClient.newSession(
                        cwd = projectPath,
                        mcpServers =
                            ideMcpServers(
                                acpClient.initializeResponse
                                    ?.agentCapabilities
                                    ?.mcpCapabilities
                                    ?.http == true,
                            ),
                    )
                } ?: throw IllegalStateException("The agent did not create a session")
            sessionId = session.sessionId
            conversationSessionId = session.sessionId
            if (_state.value.restoration is SessionRestoration.TranscriptOnly) {
                // A fresh agent context must have its own transcript and saved record.
                recordId = null
                clearFileMemory()
                _state.update { it.copy(turns = emptyList(), restoration = null, activeRecordId = null) }
            }
            applySessionModels(session.models)
            applySessionModes(session.modes)
            flushPendingSurface(session.sessionId, applyMode = session.modes == null)
            applyPreferredModel(acpClient, session.sessionId)
            session.sessionId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val closed = !acpClient.isOpen
            if (closed) resetConnection()
            _state.update {
                it.copy(
                    connection = if (closed) AgentConnection.ERROR else it.connection,
                    error = e.message ?: "Could not start an agent session",
                )
            }
            null
        }
    }

    // ------------------------------------------------------------------------
    // Turns
    // ------------------------------------------------------------------------

    /**
     * Sends [prompt] immediately, or queues it when a turn is already running.
     *
     * [steer] cancels that turn at the next tool boundary so the follow-up is delivered sooner.
     * Composer chips are included only when [prompt] is the current draft.
     */
    private suspend fun submit(
        prompt: String,
        contextLabels: List<String>,
        steer: Boolean,
    ) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        if (_state.value.restoration is SessionRestoration.Loading) return
        val goalCommand = parseGoalCommand(text)
        if (goalCommand != null) {
            applyGoalCommand(goalCommand, clearComposer = _state.value.composerInput.trim() == text)
            return
        }
        val clearComposer = _state.value.composerInput.trim() == text
        if (parseReviewCommand(text)) {
            showReview(clearComposer)
            return
        }
        if (parseWorktreeCommand(text)) {
            openWorktree(clearComposer)
            return
        }
        if (parseBringCommand(text)) {
            bringChanges(clearComposer)
            return
        }
        val attachments = attachmentsFor(text, contextLabels)
        if (_state.value.isStreaming) {
            queueFollowUp(text, attachments, steer, clearComposer)
        } else {
            handleSend(text, attachments, clearComposer)
        }
    }

    private fun attachmentsFor(
        text: String,
        contextLabels: List<String>,
    ): List<ComposerAttachment> {
        val current = _state.value
        if (current.composerInput.trim() == text && current.composerAttachments.isNotEmpty()) {
            return current.composerAttachments
        }
        return contextLabels.filter { it.isNotBlank() }.map { label ->
            ComposerAttachment(
                label = label.substringAfterLast('/').substringAfterLast('\\').ifBlank { label },
                path = label,
            )
        }
    }

    /**
     * @return false when the turn could not be claimed, so a queued follow-up can be put back.
     */
    private suspend fun handleSend(
        prompt: String,
        attachments: List<ComposerAttachment>,
        clearComposer: Boolean,
        wirePrompt: String? = null,
    ): Boolean {
        val text = prompt.trim()
        if (text.isEmpty()) return false
        val instructions = readProjectInstructions()
        val claim = claimTurn(text, attachments, clearComposer, instructions != null) ?: return false
        try {
            val body = wirePrompt ?: promptWithContext(text, attachments)
            claim.client.promptText(claim.sessionId, promptWithInstructions(instructions, body))
            if (claim.generation == turnGeneration) endTurn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (claim.generation != turnGeneration) return true
            val closed = !claim.client.isOpen
            if (closed) resetConnection()
            _state.update {
                it.copy(
                    connection = if (closed) AgentConnection.ERROR else it.connection,
                    error = e.message ?: "The agent run failed",
                )
            }
            endTurn()
        }
        return true
    }

    /**
     * Reads `AGENTS.md` from this checkout's root.
     *
     * Nested instruction files are not read, and `@import` lines are not expanded.
     * A missing or blank file adds nothing to the turn.
     */
    private suspend fun readProjectInstructions(): String? {
        val root = projectPath.trimEnd('/', '\\')
        if (root.isEmpty()) return null
        val path = "$root/$AGENTS_FILE"
        if (!fileSystem.exists(path)) return null
        val raw = fileSystem.readText(path).getOrNull() ?: return null
        return instructionText(raw)
    }

    /**
     * Claims the composer for a new turn and appends the user and agent turns.
     *
     * The mutex covers only the claim, so Stop and New chat stay dispatchable
     * while the prompt itself is in flight.
     */
    private suspend fun claimTurn(
        text: String,
        attachments: List<ComposerAttachment>,
        clearComposer: Boolean,
        includeInstructions: Boolean = false,
    ): TurnClaim? {
        if (!sendMutex.tryLock()) return null
        try {
            if (_state.value.isStreaming) return null
            if (!ensureConnected()) return null
            val acpClient = client ?: return null
            val activeSession = ensureSession(acpClient) ?: return null
            val generation = ++turnGeneration
            if (clearComposer) mentionGeneration++
            val userId = nextId("usr")
            checkpoints.begin(userId)
            val activeId = recordId ?: UUID.randomUUID().toString().also { recordId = it }
            val context =
                attachments.map(::chipLabel) +
                    listOfNotNull(instructionChip(attachments, includeInstructions))
            _state.update {
                it.copy(
                    activeRecordId = activeId,
                    turns =
                        it.turns +
                            Turn.User(userId, text, "now", context) +
                            Turn.Agent(nextId("agt"), "now", emptyList()),
                    isStreaming = true,
                    error = null,
                    goalNotice = null,
                    presence = Presence(action = "Thinking", fileName = null, startedAtEpochMillis = now()),
                    composerInput = if (clearComposer) "" else it.composerInput,
                    composerAttachments = if (clearComposer) emptyList() else it.composerAttachments,
                    mentionFiles = if (clearComposer) emptyList() else it.mentionFiles,
                )
            }
            return TurnClaim(acpClient, activeSession, generation)
        } finally {
            sendMutex.unlock()
        }
    }

    private suspend fun handleStop() {
        disarmQueue()
        freezeGoalPaused(notice = null, keepNotice = true)
        _state.update { st ->
            val queued = st.queuedPrompt
            if (queued == null || !queued.steer) st else st.copy(queuedPrompt = queued.copy(steer = false))
        }
        val acpClient = client ?: return
        val activeSession = sessionId ?: return
        if (!_state.value.isStreaming) return

        try {
            acpClient.cancel(activeSession)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val closed = !acpClient.isOpen
            if (closed) resetConnection()
            invalidateTurn()
            _state.update {
                it.copy(
                    connection = if (closed) AgentConnection.ERROR else it.connection,
                    error = e.message ?: "Could not stop the agent",
                )
            }
            endTurn()
            return
        }

        _state.update { st ->
            if (st.isStreaming) st.copy(presence = st.presence?.copy(action = "Stopping")) else st
        }
        stopFallbackJob?.cancel()
        stopFallbackJob =
            scope.launch {
                delay(STOP_GRACE_PERIOD_MS)
                if (_state.value.isStreaming) {
                    stopFallbackJob = null
                    invalidateTurn()
                    endTurn()
                }
            }
    }

    /**
     * Invalidates the current turn so a late prompt completion cannot end or
     * archive a newer one, then resolves any outstanding approvals.
     */
    private fun invalidateTurn() {
        turnGeneration++
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cancelPendingApprovals()
        approvedEditPaths.clear()
    }

    private suspend fun endTurn() {
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cancelPendingApprovals()
        approvedEditPaths.clear()
        val queued = queuedForDelivery()
        _state.update { st ->
            val turns =
                st.turns.map { turn ->
                    if (turn is Turn.Agent) {
                        turn.copy(blocks = turn.blocks.map { if (it is Block.Text) it.copy(streaming = false) else it })
                    } else {
                        turn
                    }
                }
            st.copy(
                isStreaming = false,
                presence = null,
                turns = turns,
                queuedPrompt = if (queued != null) null else st.queuedPrompt,
            )
        }
        // Capture before another history action can replace the visible conversation.
        persistConversation()
        if (queued != null) {
            val sent = handleSend(queued.prompt, queued.attachments, clearComposer = false)
            if (!sent && !_state.value.isStreaming && _state.value.queuedPrompt == null) {
                _state.update { it.copy(queuedPrompt = queued.copy(steer = false)) }
            }
            return
        }
        // The check can mark the goal met or paused after the snapshot above.
        continueGoal()
        persistConversation()
    }

    /** Writes the visible conversation, including the goal, without blocking the turn. */
    private fun persistConversation() {
        val record = conversationRecord() ?: return
        scope.launch {
            historyMutex.withLock { historyStore.save(record) }
        }
    }

    /** Returns the follow-up to send, and disarms delivery so Stop cannot be overtaken. */
    private fun queuedForDelivery(): QueuedFollowUp? {
        val queued = _state.value.queuedPrompt
        if (!deliverQueuedPrompt || queued == null) return null
        deliverQueuedPrompt = false
        steerArmed = false
        return queued
    }

    private suspend fun queueFollowUp(
        text: String,
        attachments: List<ComposerAttachment>,
        steer: Boolean,
        clearComposer: Boolean,
    ) {
        deliverQueuedPrompt = true
        steerArmed = steer
        if (clearComposer) mentionGeneration++
        _state.update { st ->
            st.copy(
                queuedPrompt = QueuedFollowUp(prompt = text, attachments = attachments, steer = steer),
                composerInput = if (clearComposer) "" else st.composerInput,
                composerAttachments = if (clearComposer) emptyList() else st.composerAttachments,
                mentionFiles = if (clearComposer) emptyList() else st.mentionFiles,
            )
        }
        if (steer) steerCancelIfIdle()
    }

    /**
     * Cancels the running turn when steering and no tool call is still in progress.
     *
     * The prompt call stays in flight until the agent returns. [endTurn] then sends the queue.
     */
    private suspend fun steerCancelIfIdle() {
        if (!steerArmed || !deliverQueuedPrompt) return
        if (hasRunningTool()) return
        val acpClient = client ?: return
        val activeSession = sessionId ?: return
        steerArmed = false
        try {
            acpClient.cancel(activeSession)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The prompt return path still ends the turn and delivers the follow-up.
        }
    }

    private fun hasRunningTool(): Boolean {
        val turn = _state.value.turns.lastOrNull { it is Turn.Agent } as? Turn.Agent ?: return false
        return turn.blocks.any { it is Block.Tool && it.status == ToolStatus.RUNNING }
    }

    private fun disarmQueue() {
        deliverQueuedPrompt = false
        steerArmed = false
    }

    private fun clearQueuedPrompt() {
        disarmQueue()
        _state.update { it.copy(queuedPrompt = null) }
    }

    /**
     * Applies a `/goal` command locally.
     *
     * The command is not sent to the agent. Setting an objective starts a work round.
     * Pause, resume, and clear adjust the loop.
     */
    private suspend fun applyGoalCommand(
        command: GoalCommand,
        clearComposer: Boolean,
    ) {
        val refusal = goalRefusal(command, _state.value.isStreaming, _state.value.mode)
        if (refusal != null) {
            _state.update {
                it.copy(
                    goalNotice = refusal,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
            return
        }
        when (command) {
            GoalCommand.Show -> {
                _state.update {
                    it.copy(
                        goalNotice = if (it.goal == null) GOAL_EMPTY_NOTICE else null,
                        composerInput = if (clearComposer) "" else it.composerInput,
                    )
                }
            }

            is GoalCommand.Set -> {
                beginGoal(command.objective, clearComposer)
            }

            GoalCommand.Pause -> {
                if (clearComposer) _state.update { it.copy(composerInput = "") }
                pauseGoal()
            }

            GoalCommand.Resume -> {
                resumeGoal(clearComposer)
            }

            GoalCommand.Clear -> {
                clearGoal(clearComposer)
            }
        }
    }

    /**
     * Opens the session review.
     *
     * The command is local. It does not send a prompt, so the agent cannot edit from the review.
     */
    private fun showReview(clearComposer: Boolean) {
        val files = reviewFiles(_state.value.turns)
        _state.update {
            it.copy(
                reviewVisible = files.isNotEmpty(),
                reviewNotice = if (files.isEmpty()) REVIEW_EMPTY_NOTICE else null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
    }

    /** Accepts or dismisses every still-proposed hunk of the reviewed file [key]. */
    private suspend fun decideReviewFile(
        key: String,
        accept: Boolean,
    ) {
        if (_state.value.isStreaming) {
            _state.update { it.copy(reviewNotice = REVIEW_BUSY_NOTICE) }
            return
        }
        val file = reviewFiles(_state.value.turns).firstOrNull { it.key == key } ?: return
        file.pendingBlockIds.forEach { blockId -> decideDiff(blockId, accept) }
    }

    /**
     * Opens a second session in a new Git worktree.
     *
     * The main checkout stays as it is. A later `/worktree` switches back to the session
     * already created.
     */
    private suspend fun openWorktree(clearComposer: Boolean) {
        fun notice(message: String) {
            _state.update {
                it.copy(
                    worktreeNotice = message,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
        }
        if (checkout == AgentCheckout.WORKTREE) {
            notice(WORKTREE_ALREADY_NOTICE)
            return
        }
        if (_state.value.isStreaming) {
            notice(WORKTREE_BUSY_NOTICE)
            return
        }
        val launcher = worktreeLauncher
        if (launcher == null) {
            notice(WORKTREE_UNAVAILABLE_NOTICE)
            return
        }
        val existing = linkedWorktree
        if (existing != null) {
            _state.update {
                it.copy(
                    worktreeNotice = null,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
            onWorktreeReady(existing)
            return
        }
        if (worktreeOpening) {
            notice(WORKTREE_OPENING_NOTICE)
            return
        }
        worktreeOpening = true
        val created = launcher.create(projectPath)
        created.onSuccess { tree -> linkedWorktree = tree }
        worktreeOpening = false
        created.fold(
            onSuccess = { tree ->
                _state.update {
                    it.copy(
                        worktreeNotice = null,
                        composerInput = if (clearComposer) "" else it.composerInput,
                    )
                }
                onWorktreeReady(tree)
            },
            onFailure = { error ->
                notice(error.message ?: "Could not create a worktree.")
            },
        )
    }

    /**
     * Copies this worktree's changes into the main checkout and opens them for review.
     *
     * The command is local. It does not send a prompt, commit, or merge.
     */
    private suspend fun bringChanges(clearComposer: Boolean) {
        fun notice(message: String) {
            _state.update {
                it.copy(
                    worktreeNotice = message,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
        }
        if (checkout != AgentCheckout.WORKTREE) {
            notice(BRING_FROM_WORKTREE_NOTICE)
            return
        }
        if (_state.value.isStreaming) {
            notice(BRING_BUSY_NOTICE)
            return
        }
        val relatives = bringableRelativePaths(projectPath, _state.value.turns)
        if (relatives.isEmpty()) {
            notice(BRING_EMPTY_NOTICE)
            return
        }
        val importer = onBringBack
        if (importer == null) {
            notice(BRING_UNAVAILABLE_NOTICE)
            return
        }
        val changes = mutableListOf<AgentBroughtChange>()
        for (relative in relatives) {
            val path = runCatching { pathGuard.resolve(relative) }.getOrNull()
            if (path == null) {
                notice(BRING_FAILED_NOTICE)
                return
            }
            val exists = fileSystem.exists(path)
            val content =
                if (exists) {
                    fileSystem.readText(path).getOrElse { error ->
                        notice(error.message ?: BRING_FAILED_NOTICE)
                        return
                    }
                } else {
                    null
                }
            changes += AgentBroughtChange(relativePath = relative, exists = exists, content = content)
        }
        val outcome = importer(changes)
        val refusal = outcome.refusal
        if (refusal != null) {
            notice(refusal)
            return
        }
        if (outcome.imported == 0) {
            val message =
                if (outcome.skippedDirty > 0) {
                    BRING_DIRTY_NOTICE
                } else if (outcome.skippedPending > 0) {
                    BRING_PENDING_NOTICE
                } else {
                    BRING_EMPTY_NOTICE
                }
            notice(message)
            return
        }
        _state.update {
            it.copy(
                worktreeNotice = null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
    }

    /**
     * Writes [changes] into this checkout and opens the review on the files that changed.
     *
     * A running turn refuses the copy. An unsaved editor, or a file already waiting in
     * this review, stays as it is. Accept keeps a copied file. Dismiss restores the text
     * this checkout had before the copy.
     */
    internal suspend fun importBroughtChanges(changes: List<AgentBroughtChange>): AgentBringOutcome {
        if (_state.value.isStreaming) {
            return AgentBringOutcome(refusal = BRING_MAIN_BUSY_NOTICE)
        }
        val proposed =
            allDiffs()
                .filter { it.decision == DiffDecision.PROPOSED }
                .mapNotNull { resolvedPath(it.absolutePath) }
                .toSet()
        val blocks = mutableListOf<Block>()
        val written = mutableListOf<String>()
        var skippedDirty = 0
        var skippedPending = 0
        var index = 0
        for (change in changes) {
            if (!isSafeRelative(change.relativePath)) continue
            val path = runCatching { pathGuard.resolve(change.relativePath) }.getOrNull() ?: continue
            if (isEditorDirty(path)) {
                skippedDirty++
                continue
            }
            if (path in proposed) {
                skippedPending++
                continue
            }
            val existed = fileSystem.exists(path)
            val current =
                if (!existed) {
                    null
                } else {
                    fileSystem.readText(path).getOrElse { error ->
                        return publishBroughtChanges(
                            blocks,
                            written,
                            skippedDirty,
                            skippedPending,
                            error.message ?: BRING_FAILED_NOTICE,
                        )
                    }
                }
            val incoming = if (change.exists) change.content.orEmpty() else null
            val unchanged = if (change.exists) existed && current == incoming else !existed
            if (unchanged) continue
            val result =
                if (change.exists) {
                    fileSystem.writeText(path, incoming.orEmpty())
                } else {
                    fileSystem.delete(path)
                }
            if (result.isFailure) {
                return publishBroughtChanges(
                    blocks,
                    written,
                    skippedDirty,
                    skippedPending,
                    result.exceptionOrNull()?.message ?: BRING_FAILED_NOTICE,
                )
            }
            fileSnapshots.push(path, FileSnapshot(existed, current))
            val (lines, added, removed) = computeDiff(current, incoming.orEmpty())
            blocks +=
                Block.Diff(
                    id = "bring-$index:diff:0",
                    fileName = change.relativePath.substringAfterLast('/'),
                    path = change.relativePath.substringBeforeLast('/', missingDelimiterValue = ""),
                    added = added,
                    removed = removed,
                    lines = lines,
                    decision = DiffDecision.PROPOSED,
                    absolutePath = path,
                    oldText = current,
                )
            written += path
            index++
        }
        return publishBroughtChanges(blocks, written, skippedDirty, skippedPending, failure = null)
    }

    private fun publishBroughtChanges(
        blocks: List<Block>,
        written: List<String>,
        skippedDirty: Int,
        skippedPending: Int,
        failure: String?,
    ): AgentBringOutcome {
        if (blocks.isEmpty()) {
            return AgentBringOutcome(
                skippedDirty = skippedDirty,
                skippedPending = skippedPending,
                refusal = failure,
            )
        }
        val notice =
            failure
                ?: if (skippedDirty > 0) {
                    BRING_DIRTY_NOTICE
                } else if (skippedPending > 0) {
                    BRING_PENDING_NOTICE
                } else {
                    null
                }
        val textId = nextId("bring-text")
        val turnId = nextId("bring")
        _state.update { state ->
            state.copy(
                turns =
                    state.turns +
                        Turn.Agent(
                            id = turnId,
                            timeLabel = "now",
                            blocks = listOf(Block.Text(id = textId, text = "Brought from the worktree.")) + blocks,
                        ),
                reviewVisible = true,
                reviewNotice = notice,
                error = null,
            )
        }
        if (written.isNotEmpty()) onWorkspaceFilesChanged(written)
        return AgentBringOutcome(
            imported = blocks.size,
            skippedDirty = skippedDirty,
            skippedPending = skippedPending,
        )
    }

    /** Replaces any current objective and starts the first work round. */
    private suspend fun beginGoal(
        objective: String,
        clearComposer: Boolean,
    ) {
        val started = now()
        goalChecking = false
        goalAutoRounds = 0
        goalBaseline = _state.value.turns.size
        _state.update {
            it.copy(
                goal =
                    AgentGoal(
                        objective = objective,
                        status = AgentGoalStatus.ACTIVE,
                        startedAtEpochMillis = started,
                        elapsedMillis = 0,
                        runningSinceEpochMillis = started,
                        iteration = 1,
                    ),
                goalNotice = null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
        runGoalPrompt(visible = objective, wire = goalWorkPrompt(objective), checking = false)
    }

    /** Holds the loop. A running turn is stopped so the next round does not start. */
    private suspend fun pauseGoal() {
        freezeGoalPaused(notice = null, keepNotice = false)
        if (_state.value.isStreaming) handleStop()
    }

    /** Continues a paused goal. Plan mode cannot resume the loop. */
    private suspend fun resumeGoal(clearComposer: Boolean) {
        val goal = _state.value.goal ?: return
        if (goal.status == AgentGoalStatus.MET) return
        if (_state.value.isStreaming) {
            _state.update {
                it.copy(
                    goalNotice = GOAL_BUSY_NOTICE,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
            return
        }
        if (isPlanMode(_state.value.mode)) {
            _state.update {
                it.copy(
                    goalNotice = GOAL_PLAN_NOTICE,
                    composerInput = if (clearComposer) "" else it.composerInput,
                )
            }
            return
        }
        val resumedAt = now()
        goalAutoRounds = 0
        _state.update {
            it.copy(
                goal =
                    goal.copy(
                        status = AgentGoalStatus.ACTIVE,
                        runningSinceEpochMillis = resumedAt,
                    ),
                goalNotice = null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
        runGoalPrompt(
            visible = goalResumeLabel(goal.iteration),
            wire = goalResumePrompt(goal.objective),
            checking = false,
        )
    }

    /** Drops the objective. A running turn is stopped with it. */
    private suspend fun clearGoal(clearComposer: Boolean) {
        resetGoalLoop()
        _state.update {
            it.copy(
                goal = null,
                goalNotice = null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
        if (_state.value.isStreaming) handleStop()
    }

    /**
     * After a work round, asks for a separate check. After an unmet check, starts
     * the next round. A met check, a pause, or the round limit stops the loop.
     */
    private suspend fun continueGoal() {
        val goal = _state.value.goal ?: return
        if (goal.status != AgentGoalStatus.ACTIVE) return
        if (!_state.value.error.isNullOrBlank()) {
            freezeGoalPaused(notice = GOAL_FAILED_NOTICE, keepNotice = false)
            return
        }
        if (goalChecking) {
            val verdict =
                judgeGoal(
                    lastAgentReply(),
                    goalEvidence(_state.value.turns.drop(goalBaseline), _state.value.plan),
                )
            if (verdict.met) {
                markGoalMet()
                return
            }
            if (goalAutoRounds >= GOAL_ROUND_LIMIT) {
                freezeGoalPaused(notice = GOAL_CAP_NOTICE, keepNotice = false)
                return
            }
            goalAutoRounds++
            val iteration = goal.iteration + 1
            _state.update { st ->
                val current = st.goal ?: return@update st
                st.copy(goal = current.copy(iteration = iteration))
            }
            val objective = _state.value.goal?.objective ?: return
            runGoalPrompt(
                visible = goalContinueLabel(iteration),
                wire = goalContinuePrompt(objective, verdict.nextAction),
                checking = false,
            )
        } else {
            runGoalPrompt(
                visible = goalCheckLabel(goal.iteration),
                wire = goalCheckPrompt(goal.objective),
                checking = true,
            )
        }
    }

    private suspend fun runGoalPrompt(
        visible: String,
        wire: String,
        checking: Boolean,
    ) {
        goalChecking = checking
        val sent = handleSend(visible, emptyList(), clearComposer = false, wirePrompt = wire)
        val goal = _state.value.goal
        if (!sent && goal != null && goal.status == AgentGoalStatus.ACTIVE && !_state.value.isStreaming) {
            freezeGoalPaused(notice = GOAL_FAILED_NOTICE, keepNotice = false)
        }
    }

    private fun markGoalMet() {
        val markedAt = now()
        goalChecking = false
        _state.update { st ->
            val goal = st.goal ?: return@update st
            st.copy(
                goal =
                    goal.copy(
                        status = AgentGoalStatus.MET,
                        elapsedMillis = elapsedMillis(goal, markedAt),
                        runningSinceEpochMillis = null,
                    ),
                goalNotice = null,
            )
        }
    }

    /**
     * Freezes an active goal as paused.
     *
     * [keepNotice] leaves the current explanation in place. Stop uses that so a
     * refusal already on screen stays. A null [notice] clears it.
     */
    private fun freezeGoalPaused(
        notice: String?,
        keepNotice: Boolean,
    ) {
        goalChecking = false
        val frozenAt = now()
        _state.update { st ->
            val goal = st.goal ?: return@update st
            if (goal.status == AgentGoalStatus.MET) return@update st
            st.copy(
                goal =
                    goal.copy(
                        status = AgentGoalStatus.PAUSED,
                        elapsedMillis = elapsedMillis(goal, frozenAt),
                        runningSinceEpochMillis = null,
                    ),
                goalNotice = if (keepNotice) st.goalNotice else notice,
            )
        }
    }

    private fun resetGoalLoop() {
        goalChecking = false
        goalAutoRounds = 0
        goalBaseline = 0
    }

    private fun lastAgentReply(): String {
        val turn = _state.value.turns.lastOrNull { it is Turn.Agent } as? Turn.Agent ?: return ""
        return turn.blocks
            .filterIsInstance<Block.Text>()
            .filter { !it.thought }
            .joinToString("\n") { it.text }
    }

    private fun updateComposerInput(text: String) {
        val query = mentionQuery(text)
        _state.update { st ->
            st.copy(
                composerInput = text,
                mentionFiles = if (query.isNullOrEmpty()) emptyList() else st.mentionFiles,
            )
        }
        if (query.isNullOrEmpty()) {
            mentionGeneration++
            return
        }
        val generation = ++mentionGeneration
        scope.launch {
            val matches =
                try {
                    mentionSearch(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            if (generation != mentionGeneration) return@launch
            if (mentionQuery(_state.value.composerInput) != query) return@launch
            _state.update { st ->
                st.copy(mentionFiles = matches.distinctBy { it.path }.take(MENTION_RESULT_LIMIT))
            }
        }
    }

    private fun attachFromEditor(selection: Boolean) {
        val snippet = editorContext() ?: return
        val excerpt = if (selection) snippet.selection?.takeIf { it.isNotBlank() } ?: return else null
        if (snippet.path.isBlank()) return
        acceptAttachment(
            ComposerAttachment(
                label = snippet.label.ifBlank { snippet.path.substringAfterLast('/') },
                path = snippet.path,
                excerpt = excerpt,
            ),
        )
    }

    private fun attachMention(
        path: String,
        label: String,
    ) {
        if (path.isBlank()) return
        acceptAttachment(
            ComposerAttachment(
                label = label.ifBlank { path.substringAfterLast('/') },
                path = path,
            ),
        )
    }

    private fun acceptAttachment(attachment: ComposerAttachment) {
        mentionGeneration++
        _state.update { st ->
            st.copy(
                composerAttachments = addAttachment(st.composerAttachments, attachment),
                composerInput = withoutMentionToken(st.composerInput),
                mentionFiles = emptyList(),
            )
        }
    }

    private fun addAttachment(
        current: List<ComposerAttachment>,
        attachment: ComposerAttachment,
    ): List<ComposerAttachment> {
        val duplicate =
            current.any { it.path == attachment.path && (it.excerpt != null) == (attachment.excerpt != null) }
        return if (duplicate) current else current + attachment
    }

    private fun removeAttachment(
        path: String,
        selection: Boolean,
    ) {
        _state.update { st ->
            st.copy(
                composerAttachments =
                    st.composerAttachments.filterNot { it.path == path && (it.excerpt != null) == selection },
            )
        }
    }

    private fun cancelPendingApprovals() {
        if (pendingApprovals.isEmpty()) return
        val pending = pendingApprovals.values.toList()
        pendingApprovals.clear()
        pending.forEach { approval ->
            approval.deferred.complete(RequestPermissionResponse(PermissionOutcome.Cancelled))
        }
        mutateBlocks { block -> if (block is Block.Approval) block.copy(resolved = true) else block }
    }

    // ------------------------------------------------------------------------
    // Session history
    // ------------------------------------------------------------------------

    /**
     * Persists the conversation transcript (user messages plus the agent's
     * text blocks; tool calls, diffs and approvals are not restorable and are
     * skipped). Creates the record on first use and upserts afterwards.
     */
    private suspend fun archiveConversation() {
        conversationRecord()?.let { historyStore.save(it) }
    }

    /** Captures only live conversations; viewing saved history must not rewrite it. */
    private fun conversationRecord(): AgentSessionRecord? {
        val restoration = _state.value.restoration
        if (restoration is SessionRestoration.Loading || restoration is SessionRestoration.TranscriptOnly) return null
        val messages =
            _state.value.turns
                .flatMap { turn ->
                    when (turn) {
                        is Turn.User -> {
                            listOf(AgentHistoryMessage(AgentHistoryRole.USER, turn.text))
                        }

                        is Turn.Agent -> {
                            turn.blocks
                                .filterIsInstance<Block.Text>()
                                .filter { !it.thought }
                                .map { AgentHistoryMessage(AgentHistoryRole.AGENT, it.text) }
                        }
                    }
                }.filter { it.text.isNotBlank() }
        if (messages.none { it.role == AgentHistoryRole.USER }) return null

        val id = recordId ?: UUID.randomUUID().toString().also { recordId = it }
        if (_state.value.activeRecordId != id) {
            _state.update { it.copy(activeRecordId = id) }
        }
        val summary =
            summarizeSession(
                turns = _state.value.turns,
                isStreaming = false,
                error = _state.value.error,
                live = false,
            )
        return AgentSessionRecord(
            id = id,
            title = sessionTitle(_state.value.turns, TITLE_MAX_CHARS) ?: "Agent session",
            projectPath = projectPath,
            savedAtEpochMillis = System.currentTimeMillis(),
            agentSessionId = conversationSessionId,
            messages = messages,
            addedLines = summary.addedLines,
            removedLines = summary.removedLines,
            status = summary.status,
            goal = _state.value.goal?.let { persistedGoal(it, now()) },
        )
    }

    /** Starts a fresh chat, cancelling the current turn and abandoning its ACP session. */
    private suspend fun startNewChat() {
        clearQueuedPrompt()
        freezeGoalPaused(notice = null, keepNotice = true)
        val acpClient = client
        val previousSession = sessionId
        val wasStreaming = _state.value.isStreaming
        invalidateTurn()
        if (wasStreaming && acpClient != null && previousSession != null) {
            runCatching { acpClient.cancel(previousSession) }
        }
        archiveConversation()
        clearFileMemory()
        sessionId = null
        conversationSessionId = null
        clearSessionSurface()
        recordId = null
        resetGoalLoop()
        _state.update {
            it.copy(
                turns = emptyList(),
                isStreaming = false,
                presence = null,
                error = null,
                restoration = null,
                activeRecordId = null,
                goal = null,
                goalNotice = null,
                reviewVisible = false,
                reviewNotice = null,
            )
        }
    }

    /** Deletes a persisted conversation, clearing it when it is the active one. */
    private suspend fun deleteSession(recordIdToDelete: String) {
        if (recordIdToDelete == recordId) sessionChangeJob?.cancelAndJoin()
        sendMutex.withLock { deleteSessionRecord(recordIdToDelete) }
    }

    private suspend fun deleteSessionRecord(recordIdToDelete: String) {
        historyStore.remove(recordIdToDelete)
        if (recordIdToDelete != recordId) {
            _state.update { it.copy(sessionHistory = historyStore.list()) }
            return
        }

        clearQueuedPrompt()
        val acpClient = client
        val previousSession = sessionId
        val wasStreaming = _state.value.isStreaming
        invalidateTurn()
        if (wasStreaming && acpClient != null && previousSession != null) {
            runCatching { acpClient.cancel(previousSession) }
        }
        clearFileMemory()
        sessionId = null
        conversationSessionId = null
        clearSessionSurface()
        recordId = null
        resetGoalLoop()
        _state.update {
            it.copy(
                turns = emptyList(),
                sessionHistory = historyStore.list(),
                isStreaming = false,
                presence = null,
                restoration = null,
                activeRecordId = null,
                goal = null,
                goalNotice = null,
                reviewVisible = false,
                reviewNotice = null,
            )
        }
    }

    /**
     * Rebuilds the surface turns from a persisted transcript and reloads the
     * agent's session when the record carries its identifier.
     */
    private suspend fun restoreSession(recordIdToRestore: String) {
        val record = historyStore.list().firstOrNull { it.id == recordIdToRestore } ?: return
        clearQueuedPrompt()
        val acpClient = client
        val previousSession = sessionId
        val wasStreaming = _state.value.isStreaming
        invalidateTurn()
        clearFileMemory()
        _state.update { it.copy(isStreaming = false, presence = null) }
        if (wasStreaming && acpClient != null && previousSession != null) {
            runCatching { acpClient.cancel(previousSession) }
        }
        archiveConversation()

        // Drop updates while loading so the replayed history cannot duplicate the
        // restored transcript.
        sessionId = null
        conversationSessionId = record.agentSessionId
        clearSessionSurface()
        recordId = record.id
        resetGoalLoop()

        var counter = 0
        val turns =
            record.messages.map { message ->
                val id = "restored-${record.id}-${counter++}"
                when (message.role) {
                    AgentHistoryRole.USER -> {
                        Turn.User(id = id, text = message.text, timeLabel = "")
                    }

                    AgentHistoryRole.AGENT -> {
                        Turn.Agent(
                            id = id,
                            timeLabel = "",
                            blocks = listOf(Block.Text(id = "$id-text", text = message.text)),
                        )
                    }
                }
            }
        _state.update {
            it.copy(
                turns = turns,
                historyVisible = false,
                error = null,
                presence = null,
                isStreaming = false,
                restoration = SessionRestoration.Loading,
                activeRecordId = record.id,
                goal = record.goal?.let(::restoredGoal),
                goalNotice = restoredGoalNotice(record.goal),
                reviewVisible = false,
                reviewNotice = null,
            )
        }
        if (record.agentSessionId == null) {
            transcriptOnly("This saved conversation has no agent session to resume.")
        } else if (ensureConnected()) {
            client?.let { restoreAgentSession(it, record) }
        } else {
            transcriptOnly("Could not reconnect to the agent.")
        }
    }

    /** Reloads context without creating an unrelated session or duplicating replayed messages. */
    private suspend fun restoreAgentSession(
        acpClient: AcpClient,
        record: AgentSessionRecord,
    ) {
        val savedSession = record.agentSessionId
        if (savedSession == null) {
            transcriptOnly("This saved conversation has no agent session to resume.")
            return
        }
        if (acpClient.initializeResponse?.agentCapabilities?.loadSession != true) {
            transcriptOnly("This agent does not support resuming saved conversations.")
            return
        }
        _state.update { it.copy(restoration = SessionRestoration.Loading) }
        try {
            val loaded =
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    acpClient.loadSession(
                        sessionId = savedSession,
                        cwd = projectPath,
                        mcpServers =
                            ideMcpServers(
                                acpClient.initializeResponse
                                    ?.agentCapabilities
                                    ?.mcpCapabilities
                                    ?.http == true,
                            ),
                    )
                }
            if (loaded == null) {
                resetConnection()
                _state.update { it.copy(connection = AgentConnection.DISCONNECTED) }
                transcriptOnly("The agent did not finish restoring this conversation.")
                return
            }
            sessionId = savedSession
            conversationSessionId = savedSession
            applySessionModels(loaded.models)
            applySessionModes(loaded.modes)
            flushPendingSurface(savedSession, applyMode = loaded.modes == null)
            _state.update { it.copy(restoration = SessionRestoration.Resumed, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!acpClient.isOpen) {
                resetConnection()
                _state.update { it.copy(connection = AgentConnection.DISCONNECTED) }
            }
            transcriptOnly(
                "Could not resume this conversation: ${e.message ?: "the agent rejected the saved session"}.",
            )
        }
    }

    private fun transcriptOnly(reason: String) {
        sessionId = null
        clearSessionSurface()
        _state.update { it.copy(restoration = SessionRestoration.TranscriptOnly(reason)) }
    }

    // ------------------------------------------------------------------------
    // ACP session updates -> blocks
    // ------------------------------------------------------------------------

    private fun applyUpdate(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                appendText(textOf(update.content), thought = false)
            }

            is SessionUpdate.AgentThoughtChunk -> {
                appendText(textOf(update.content), thought = true)
            }

            is SessionUpdate.ToolCallStart -> {
                applyToolCall(
                    id = update.toolCallId,
                    title = update.title,
                    kind = update.kind,
                    status = update.status,
                    content = update.content,
                    location = update.locations.firstOrNull()?.path,
                    rawInput = update.rawInput,
                )
            }

            is SessionUpdate.ToolCallProgress -> {
                applyToolCall(
                    id = update.toolCallId,
                    title = update.title,
                    kind = null,
                    status = update.status,
                    content = update.content,
                    location = null,
                    rawInput = null,
                )
            }

            is SessionUpdate.Plan -> {
                _state.update { it.copy(plan = update.entries.map(::toPlanStep)) }
            }

            is SessionUpdate.AvailableCommandsUpdate -> {
                _state.update { it.copy(commands = update.availableCommands.mapNotNull(::toAgentCommand)) }
            }

            is SessionUpdate.CurrentModeUpdate -> {
                applyCurrentMode(update.currentModeId)
            }

            is SessionUpdate.UserMessageChunk -> {
                // History replay echoes the user's own messages. The transcript already has them.
            }
        }
    }

    private fun appendText(
        text: String,
        thought: Boolean,
    ) {
        if (text.isEmpty()) return
        mutateAgentTurn { blocks ->
            val last = blocks.lastOrNull()
            if (last is Block.Text && last.thought == thought && last.streaming) {
                blocks.dropLast(1) + last.copy(text = last.text + text)
            } else {
                stopStreaming(blocks) + Block.Text(nextId("txt"), text, thought = thought, streaming = true)
            }
        }
    }

    private fun applyToolCall(
        id: String,
        title: String?,
        kind: ToolKind?,
        status: ToolCallStatus?,
        content: List<ToolCallContent>,
        location: String?,
        rawInput: JsonElement?,
    ) {
        val resultText =
            content
                .filter { it.type != "diff" }
                .mapNotNull {
                    it.content?.let(
                        ::textOf,
                    )
                }.joinToString("\n")
        val toolStatus = toolStatusOf(status)
        var createdExecute = false
        mutateAgentTurn { blocks ->
            val stopped = stopStreaming(blocks)
            val index = stopped.indexOfFirst { it is Block.Tool && it.id == id }
            val updated =
                if (index >= 0) {
                    val existing = stopped[index] as Block.Tool
                    stopped.toMutableList().also {
                        it[index] =
                            existing.copy(
                                name = title ?: existing.name,
                                status = toolStatus,
                                result = mergeResult(existing.result, resultText),
                            )
                    }
                } else {
                    if (kind == ToolKind.EXECUTE) createdExecute = true
                    stopped +
                        Block.Tool(
                            id = id,
                            name = title ?: id,
                            kind = kind?.value ?: ToolKind.OTHER.value,
                            args = summarizeArgs(rawInput),
                            status = toolStatus,
                            result = resultText.ifBlank { null },
                            expanded = false,
                        )
                }
            content.filter { it.type == "diff" }.foldIndexed(updated) { index, acc, diff ->
                upsertDiff(acc, id, index, diff)
            }
        }
        if (createdExecute) {
            checkpoints.noteCommand()
            publishCheckpointFlags()
        }
        updatePresence(kind, toolStatus, location)
    }

    private fun upsertDiff(
        blocks: List<Block>,
        toolCallId: String,
        index: Int,
        content: ToolCallContent,
    ): List<Block> {
        val path = content.path ?: return blocks
        val diffId = "$toolCallId:diff:$index"
        val (lines, added, removed) = computeDiff(content.oldText, content.newText.orEmpty())
        val blockIndex = blocks.indexOfFirst { it is Block.Diff && it.id == diffId }
        val existing = if (blockIndex >= 0) blocks[blockIndex] as Block.Diff else null
        val block =
            Block.Diff(
                id = diffId,
                fileName = path.substringAfterLast('/'),
                path = path.substringBeforeLast('/', missingDelimiterValue = ""),
                added = added,
                removed = removed,
                lines = lines,
                // A later update for the same hunk must not resurrect a decision
                // the user already made.
                decision = existing?.decision ?: DiffDecision.PROPOSED,
                absolutePath = path,
                oldText = content.oldText,
            )
        return if (blockIndex >= 0) {
            blocks.toMutableList().also { it[blockIndex] = block }
        } else {
            blocks + block
        }
    }

    private fun updatePresence(
        kind: ToolKind?,
        status: ToolStatus,
        location: String?,
    ) {
        if (status != ToolStatus.RUNNING) return
        _state.update { st ->
            if (!st.isStreaming) {
                st
            } else {
                st.copy(
                    presence =
                        Presence(
                            action = presenceAction(kind),
                            fileName = location?.substringAfterLast('/') ?: st.presence?.fileName,
                            startedAtEpochMillis = st.presence?.startedAtEpochMillis ?: now(),
                        ),
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // Card actions
    // ------------------------------------------------------------------------

    private fun toggleTool(blockId: String) {
        mutateBlock(blockId) { block ->
            if (block is Block.Tool) block.copy(expanded = !block.expanded) else block
        }
    }

    private suspend fun decideDiff(
        blockId: String,
        accept: Boolean,
    ) {
        val diff = allDiffs().firstOrNull { it.id == blockId } ?: return
        if (diff.decision != DiffDecision.PROPOSED) return
        // The agent supplies this path, so never write to it before containment
        // is verified (the diffs carry hunks, not trusted file operations).
        val path = runCatching { pathGuard.resolve(diff.absolutePath) }.getOrNull()

        if (accept) {
            // The write already happened; dropping the snapshot commits it.
            path?.let { fileSnapshots.pop(it) }
            setDiffsDecision(diff, DiffDecision.ACCEPTED)
            return
        }

        if (path == null) {
            _state.update {
                it.copy(error = "Cannot revert ${diff.fileName}: the path is outside the project")
            }
            return
        }

        val snapshot = fileSnapshots.pop(path)
        if (snapshot == null) {
            _state.update {
                it.copy(error = "Cannot revert ${diff.fileName}: no saved pre-edit content")
            }
            return
        }

        val result =
            if (snapshot.existed) {
                fileSystem.writeText(path, snapshot.content.orEmpty())
            } else {
                fileSystem.delete(path)
            }
        result.onFailure { error ->
            // Keep the snapshot so the user can retry after fixing the cause.
            fileSnapshots.push(path, snapshot)
            _state.update {
                it.copy(error = "Could not revert ${diff.fileName}: ${error.message ?: "file operation failed"}")
            }
            return
        }
        checkpoints.noteDisk(path, snapshot)
        setDiffsDecision(diff, DiffDecision.REJECTED)
    }

    /**
     * Opens [blockId] in the version-control diff. The path must stay inside the project.
     */
    private fun openDiff(blockId: String) {
        val diff = allDiffs().firstOrNull { it.id == blockId } ?: return
        val path = runCatching { pathGuard.resolve(diff.absolutePath) }.getOrNull()
        if (path == null) {
            _state.update { it.copy(error = "Cannot open ${diff.fileName}: the path is outside the project") }
            return
        }
        openInIdeDiff(path)
    }

    /**
     * Restores files to the checkpoint on [turnId] and leaves every message in place.
     */
    private suspend fun restoreCheckpoint(turnId: String) {
        if (_state.value.isStreaming) {
            _state.update { it.copy(error = "Wait until the agent finishes before restoring files.") }
            return
        }
        val paths = checkpoints.candidatePaths(turnId) ?: return
        val current = mutableMapOf<String, FileSnapshot?>()
        paths.forEach { path -> current[path] = currentSnapshot(path) }
        val plan = checkpoints.plan(turnId, current, isEditorDirty) ?: return
        val restored = mutableListOf<String>()
        val restoredPaths = mutableListOf<String>()
        val skipped = plan.skipped.map { (path, reason) -> fileName(path) to reason }.toMutableList()
        plan.restore.forEach { (path, snapshot) ->
            val result =
                if (snapshot.existed) {
                    fileSystem.writeText(path, snapshot.content.orEmpty())
                } else {
                    fileSystem.delete(path)
                }
            result
                .onSuccess {
                    checkpoints.noteDisk(path, snapshot)
                    fileSnapshots.drop(path)
                    restored += fileName(path)
                    restoredPaths += path
                }.onFailure {
                    skipped += fileName(path) to CheckpointSkip.FAILED
                }
        }
        markDiffsRestored(turnId, restoredPaths.toSet())
        val notice = checkpointNotice(restored, skipped, plan.commandsRan).ifBlank { null }
        _state.update { st ->
            st.copy(
                error = null,
                turns =
                    st.turns.map { turn ->
                        if (turn is Turn.User && turn.id == turnId) turn.copy(restoreNotice = notice) else turn
                    },
            )
        }
        if (restoredPaths.isNotEmpty()) onWorkspaceFilesChanged(restoredPaths)
    }

    /** The file as it is on disk, or null when the read fails. */
    private suspend fun currentSnapshot(path: String): FileSnapshot? {
        if (!fileSystem.exists(path)) return FileSnapshot(existed = false, content = null)
        return fileSystem.readText(path).fold(
            onSuccess = { FileSnapshot(existed = true, content = it) },
            onFailure = { null },
        )
    }

    /** Marks diffs for [paths] that follow [turnId] as reverted. The transcript stays. */
    private fun markDiffsRestored(
        turnId: String,
        paths: Set<String>,
    ) {
        if (paths.isEmpty()) return
        val start = _state.value.turns.indexOfFirst { it.id == turnId }
        if (start < 0) return
        _state.update { st ->
            st.copy(
                turns =
                    st.turns.mapIndexed { index, turn ->
                        if (index <= start || turn !is Turn.Agent) {
                            turn
                        } else {
                            turn.copy(
                                blocks =
                                    turn.blocks.map { block ->
                                        if (block is Block.Diff && resolvedPath(block.absolutePath) in paths) {
                                            block.copy(decision = DiffDecision.REJECTED)
                                        } else {
                                            block
                                        }
                                    },
                            )
                        }
                    },
            )
        }
    }

    private fun resolvedPath(path: String): String? = runCatching { pathGuard.resolve(path) }.getOrNull()

    private fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

    /** Shows Restore on every user message whose checkpoint can still roll files back. */
    private fun publishCheckpointFlags() {
        val ids = checkpoints.restorableTurnIds()
        _state.update { st ->
            st.copy(
                turns =
                    st.turns.map { turn ->
                        if (turn is Turn.User && checkpoints.has(turn.id) && turn.canRestore != (turn.id in ids)) {
                            turn.copy(canRestore = turn.id in ids)
                        } else {
                            turn
                        }
                    },
            )
        }
    }

    private fun clearFileMemory() {
        fileSnapshots.clear()
        checkpoints.clear()
    }

    private suspend fun decideAll(accept: Boolean) {
        // Newest first so repeated edits to one file pop snapshots in order.
        allDiffs()
            .filter { it.decision == DiffDecision.PROPOSED }
            .asReversed()
            .forEach { decideDiff(it.id, accept) }
    }

    /**
     * Applies [decision] to every hunk produced by the same tool call: an ACP
     * write is atomic, so accepting or reverting one hunk applies to all of them.
     */
    private fun setDiffsDecision(
        diff: Block.Diff,
        decision: DiffDecision,
    ) {
        val prefix = "${diff.id.substringBefore(":diff:")}:diff:"
        mutateBlocks { block ->
            if (block is Block.Diff && block.id.startsWith(prefix)) block.copy(decision = decision) else block
        }
    }

    private fun allDiffs(): List<Block.Diff> =
        _state.value.turns
            .flatMap { turn -> (turn as? Turn.Agent)?.blocks.orEmpty() }
            .filterIsInstance<Block.Diff>()

    /**
     * Completes the pending permission for [callId] with [optionId].
     *
     * An id the card did not offer, and a missing card, complete as cancelled so the
     * agent request cannot stay open.
     */
    private fun resolveApproval(
        callId: String,
        optionId: String,
    ) {
        val pending = pendingApprovals.remove(callId) ?: return
        val known = pending.choices.any { it.optionId == optionId }
        val outcome = if (known) PermissionOutcome.Selected(optionId) else PermissionOutcome.Cancelled
        pending.deferred.complete(RequestPermissionResponse(outcome))
        mutateBlock(callId) { block ->
            if (block is Block.Approval) block.copy(resolved = true) else block
        }
    }

    // ------------------------------------------------------------------------
    // AcpClientHandler
    // ------------------------------------------------------------------------

    override suspend fun sessionUpdate(notification: SessionNotification) {
        // Apply updates before the prompt response can end the turn and archive it.
        // Message and tool updates from abandoned sessions or stopped turns stay ignored.
        // Plan, mode, and slash commands belong to the session, so they also apply
        // outside a turn and while a saved session is still being restored.
        val update = notification.update
        if (isSessionSurface(update)) {
            val active = notification.sessionId == sessionId
            val restoring = sessionId == null && notification.sessionId == conversationSessionId
            when {
                active || restoring -> {
                    forgetPendingField(notification.sessionId, update)
                    applyUpdate(update)
                }

                sessionId == null -> {
                    notePendingSurface(notification.sessionId, update)
                }
            }
            return
        }
        if (notification.sessionId == sessionId && _state.value.isStreaming) {
            applyUpdate(update)
            if (update is SessionUpdate.ToolCallStart || update is SessionUpdate.ToolCallProgress) {
                steerCancelIfIdle()
            }
        }
    }

    override suspend fun readTextFile(request: ReadTextFileRequest): ReadTextFileResponse {
        val path = guardedPath(request.path)
        when (_state.value.permissions[PermissionKind.READ] ?: PermissionPolicy.ASK) {
            PermissionPolicy.AUTO -> {
                // No approval needed.
            }

            PermissionPolicy.NEVER -> {
                throw AcpRequestException(
                    code = AcpError.INVALID_REQUEST,
                    message = "The user denied reading ${File(path).name}",
                )
            }

            PermissionPolicy.ASK -> {
                if (!approveDirectRead(path)) {
                    throw AcpRequestException(
                        code = AcpError.INVALID_REQUEST,
                        message = "The user denied reading ${File(path).name}",
                    )
                }
            }
        }

        val text = fileSystem.readText(path).getOrElse { throw it }
        val sliced =
            if (request.line != null || request.limit != null) {
                val lines = text.lines()
                val start = ((request.line ?: 1) - 1).coerceIn(0, lines.size)
                val end = request.limit?.let { (start + it.coerceAtLeast(0)).coerceAtMost(lines.size) } ?: lines.size
                lines.subList(start, end).joinToString("\n")
            } else {
                text
            }
        return ReadTextFileResponse(sliced)
    }

    override suspend fun writeTextFile(request: WriteTextFileRequest) {
        val path = guardedPath(request.path)
        // A permission request for this edit was already granted, in which case the
        // fs callback must not ask a second time. Otherwise apply the Edit policy as
        // a backstop for agents that write without asking.
        if (!consumeApprovedEdit(path) && !approveDirectWrite(path)) {
            throw AcpRequestException(
                code = AcpError.INVALID_REQUEST,
                message = "The user denied writing ${File(path).name}",
            )
        }
        val before = captureSnapshot(path)
        fileSystem.writeText(path, request.content).getOrElse { error ->
            // The write did not happen, so the snapshot must not outlive it.
            fileSnapshots.pop(path)
            throw error
        }
        checkpoints.noteWrite(path, before, request.content)
        publishCheckpointFlags()
    }

    /**
     * Resolves an agent-supplied path inside the workspace, refusing anything outside it.
     */
    private fun guardedPath(path: String): String =
        try {
            pathGuard.resolve(path)
        } catch (exception: WorkspacePathException) {
            throw AcpRequestException(
                code = AcpError.INVALID_PARAMS,
                message = exception.message ?: "Path is outside the open project",
            )
        }

    /**
     * Saves the current contents of [path] before an agent write so a rejected
     * diff can restore the whole file rather than a single hunk.
     */
    private suspend fun captureSnapshot(path: String): FileSnapshot {
        val existed = fileSystem.exists(path)
        val content =
            if (existed) {
                fileSystem.readText(path).getOrElse { error ->
                    throw AcpRequestException(
                        code = AcpError.INTERNAL_ERROR,
                        message = "Cannot save ${File(path).name} before writing: ${error.message ?: "read failed"}",
                    )
                }
            } else {
                null
            }
        val snapshot = FileSnapshot(existed, content)
        fileSnapshots.push(path, snapshot)
        return snapshot
    }

    /**
     * Applies the Edit permission policy to a direct `fs/write_text_file` call, prompting
     * the user when the policy is [PermissionPolicy.ASK].
     */
    private suspend fun approveDirectWrite(path: String): Boolean {
        val fileName = File(path).name
        return when (_state.value.permissions[PermissionKind.EDIT] ?: PermissionPolicy.ASK) {
            PermissionPolicy.AUTO -> {
                true
            }

            PermissionPolicy.NEVER -> {
                false
            }

            PermissionPolicy.ASK -> {
                approveDirectAccess("write", "Write $fileName", path)
            }
        }
    }

    /** Applies the Read permission policy to a direct `fs/read_text_file` call. */
    private suspend fun approveDirectRead(path: String): Boolean =
        approveDirectAccess("read", "Read ${File(path).name}", path)

    /**
     * Raises an approval card for an IDE-initiated file access and waits for it.
     */
    private suspend fun approveDirectAccess(
        prefix: String,
        title: String,
        detail: String?,
    ): Boolean {
        // Without an active turn there is no card to accept; deny instead of
        // suspending a request that the user can never answer.
        if (_state.value.turns.none { it is Turn.Agent }) return false
        val deferred = CompletableDeferred<RequestPermissionResponse>()
        val callId = "$prefix:$detail:${seq++}"
        val choices = directApprovalChoices()
        pendingApprovals[callId] = PendingApproval(deferred, choices)
        mutateAgentTurn { blocks ->
            blocks + Block.Approval(callId, title, detail, choices = choices)
        }
        val outcome = deferred.await().outcome
        return outcome is PermissionOutcome.Selected && outcome.optionId == ALLOW_OPTION_ID
    }

    override suspend fun requestPermission(request: RequestPermissionRequest): RequestPermissionResponse {
        val kind = AgentPermissionPolicy.kindFor(request.toolCall)
        // Unknown kinds are never auto-approved: fall back to asking the user.
        val policy = kind?.let { _state.value.permissions[it] } ?: PermissionPolicy.ASK
        val allow =
            request.options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE }?.optionId
                ?: request.options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }?.optionId
        val reject =
            request.options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ONCE }?.optionId
                ?: request.options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ALWAYS }?.optionId

        return when (policy) {
            PermissionPolicy.AUTO -> {
                if (kind == PermissionKind.EDIT && allow != null) recordApprovedEdit(request.toolCall)
                RequestPermissionResponse(outcomeFor(allow))
            }

            PermissionPolicy.NEVER -> {
                RequestPermissionResponse(outcomeFor(reject))
            }

            PermissionPolicy.ASK -> {
                val deferred = CompletableDeferred<RequestPermissionResponse>()
                val callId = request.toolCall.toolCallId
                val choices = approvalChoices(request.options)
                pendingApprovals[callId] = PendingApproval(deferred, choices)
                mutateAgentTurn { blocks ->
                    blocks +
                        Block.Approval(
                            id = callId,
                            title = request.toolCall.title ?: "Tool call",
                            command = commandFor(request.toolCall),
                            choices = choices,
                        )
                }
                val response = deferred.await()
                val selected = (response.outcome as? PermissionOutcome.Selected)?.optionId
                if (kind == PermissionKind.EDIT && optionAllowsEdit(request.options, selected)) {
                    recordApprovedEdit(request.toolCall)
                }
                response
            }
        }
    }

    /** Records that the upcoming write for this tool call has already been approved. */
    private fun recordApprovedEdit(toolCall: PermissionToolCall) {
        val rawPath = AgentPermissionPolicy.pathOf(toolCall) ?: return
        val path = runCatching { pathGuard.resolve(rawPath) }.getOrNull() ?: return
        approvedEditPaths[path] = (approvedEditPaths[path] ?: 0) + 1
    }

    /** Consumes a previously granted edit approval for [path], if one exists. */
    private fun consumeApprovedEdit(path: String): Boolean {
        val remaining = approvedEditPaths[path] ?: return false
        if (remaining <= 1) {
            approvedEditPaths.remove(path)
        } else {
            approvedEditPaths[path] = remaining - 1
        }
        return true
    }

    override fun dispose() {
        // The agent is a child process: close it off the caller's thread so the UI
        // does not block on process teardown during a project switch.
        val openClient = client
        client = null
        sessionId = null
        invalidateTurn()
        clearFileMemory()
        if (openClient != null) {
            shutdownScope.launch { runCatching { openClient.close() } }
        }
        scope.cancel()
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private suspend fun persist() {
        // Never overwrite the stored model with null before the initial load
        // has populated the preference.
        prefsLoaded.await()
        val st = _state.value
        prefsStore.save(AgentPrefs(st.permissions, preferredModelId ?: st.model?.id, st.docked))
    }

    /**
     * Selects [modelId] for the active session and remembers it for future
     * sessions. The ACP call is authoritative; state changes only after the
     * agent accepts it.
     */
    private suspend fun setModel(modelId: String) {
        val option = _state.value.availableModels.firstOrNull { it.id == modelId } ?: return
        val acpClient = client
        val activeSession = sessionId
        if (acpClient == null || activeSession == null || !acpClient.isOpen) {
            preferredModelId = modelId
            _state.update { it.copy(model = option, error = null) }
            persist()
            return
        }

        val applied =
            try {
                withTimeoutOrNull(MODEL_SELECT_TIMEOUT_MS) {
                    acpClient.setModel(activeSession, modelId)
                    true
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not select ${option.displayName}") }
                return
            }
        if (!applied) {
            _state.update { it.copy(error = "The agent did not confirm the model change") }
            return
        }
        preferredModelId = modelId
        _state.update { it.copy(model = option, error = null) }
        persist()
    }

    /**
     * Selects [modeId] for the active session.
     *
     * The ACP call is authoritative and the conversation is left in place.
     * State changes only after the agent accepts the mode.
     */
    private suspend fun setMode(modeId: String) {
        val option = _state.value.availableModes.firstOrNull { it.id == modeId } ?: return
        if (_state.value.mode?.id == modeId) return
        val acpClient = client
        val activeSession = sessionId
        if (acpClient == null || activeSession == null || !acpClient.isOpen) return
        val applied =
            try {
                withTimeoutOrNull(MODE_SELECT_TIMEOUT_MS) {
                    acpClient.setMode(activeSession, modeId)
                    true
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not switch to ${option.name}") }
                return
            }
        if (!applied) {
            _state.update { it.copy(error = "The agent did not confirm the mode change") }
            return
        }
        _state.update { it.copy(mode = option, error = null) }
        if (isPlanMode(option) && _state.value.goal?.status == AgentGoalStatus.ACTIVE) {
            freezeGoalPaused(notice = GOAL_PLAN_NOTICE, keepNotice = false)
        }
    }

    /** Applies the mode state reported by the agent's session response. A null report leaves the current mode. */
    private fun applySessionModes(sessionModes: SessionModeState?) {
        if (sessionModes == null) return
        val options = sessionModes.availableModes.map { it.toModeOption() }
        val current =
            options.firstOrNull { it.id == sessionModes.currentModeId }
                ?: ModeOption(id = sessionModes.currentModeId, name = sessionModes.currentModeId)
        _state.update {
            it.copy(availableModes = options.ifEmpty { listOf(current) }, mode = current)
        }
    }

    /** Points the mode chip at [modeId] without changing the selectable list. */
    private fun applyCurrentMode(modeId: String) {
        val options = _state.value.availableModes
        val current = options.firstOrNull { it.id == modeId } ?: ModeOption(id = modeId, name = modeId)
        _state.update {
            it.copy(
                availableModes = if (options.isEmpty()) listOf(current) else options,
                mode = current,
            )
        }
    }

    /** Applies the model state reported by the agent's session response. */
    private fun applySessionModels(sessionModels: SessionModelState?) {
        if (sessionModels == null) {
            clearSessionModels()
            return
        }
        val options = sessionModels.availableModels.map { it.toModelOption() }
        val current =
            options.firstOrNull { it.id == sessionModels.currentModelId }
                ?: ModelOption(
                    id = sessionModels.currentModelId,
                    displayName = sessionModels.currentModelId,
                    shortName = sessionModels.currentModelId,
                )
        _state.update { it.copy(availableModels = options, model = current) }
    }

    /** Selects the persisted model when the agent offers it. */
    private suspend fun applyPreferredModel(
        acpClient: AcpClient,
        activeSession: String,
    ) {
        val preferred = preferredModelId ?: prefsLoaded.await().modelId ?: return
        preferredModelId = preferred
        val option = _state.value.availableModels.firstOrNull { it.id == preferred } ?: return
        if (_state.value.model?.id == preferred) return
        val applied =
            try {
                withTimeoutOrNull(MODEL_SELECT_TIMEOUT_MS) {
                    acpClient.setModel(activeSession, preferred)
                    true
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        if (applied) {
            _state.update { it.copy(model = option) }
        }
        // Otherwise keep the agent's current model when it rejects the preference.
    }

    /** Clears the session-scoped model list, keeping the user's preference. */
    private fun clearSessionModels() {
        _state.update { it.copy(availableModels = emptyList(), model = null) }
    }

    /** Clears the session-scoped model, mode, plan, and commands, keeping the user's model preference. */
    private fun clearSessionSurface() {
        pendingSurface = null
        _state.update {
            it.copy(
                availableModels = emptyList(),
                model = null,
                availableModes = emptyList(),
                mode = null,
                plan = emptyList(),
                commands = emptyList(),
            )
        }
    }

    private fun notePendingSurface(
        session: String,
        update: SessionUpdate,
    ) {
        val base = pendingSurface?.takeIf { it.sessionId == session } ?: PendingSurface(session)
        pendingSurface =
            when (update) {
                is SessionUpdate.Plan -> {
                    base.copy(plan = update.entries.map(::toPlanStep))
                }

                is SessionUpdate.AvailableCommandsUpdate -> {
                    base.copy(commands = update.availableCommands.mapNotNull(::toAgentCommand))
                }

                is SessionUpdate.CurrentModeUpdate -> {
                    base.copy(modeId = update.currentModeId)
                }

                else -> {
                    base
                }
            }
    }

    /** Drops a stashed field once the same update has been applied to the live session. */
    private fun forgetPendingField(
        session: String,
        update: SessionUpdate,
    ) {
        val pending = pendingSurface?.takeIf { it.sessionId == session } ?: return
        val remaining =
            when (update) {
                is SessionUpdate.Plan -> pending.copy(plan = null)
                is SessionUpdate.AvailableCommandsUpdate -> pending.copy(commands = null)
                is SessionUpdate.CurrentModeUpdate -> pending.copy(modeId = null)
                else -> pending
            }
        pendingSurface = remaining.takeUnless { it.isEmpty() }
    }

    /**
     * Applies surface updates that arrived before this session id was assigned.
     *
     * @param applyMode Whether a stashed mode id should win. The session response's
     * mode list is authoritative when the agent sent one.
     */
    private fun flushPendingSurface(
        session: String,
        applyMode: Boolean,
    ) {
        val pending = pendingSurface
        pendingSurface = null
        if (pending == null || pending.sessionId != session) return
        _state.update { state ->
            state.copy(
                plan = pending.plan ?: state.plan,
                commands = pending.commands ?: state.commands,
            )
        }
        if (applyMode) pending.modeId?.let(::applyCurrentMode)
    }

    private fun mutateAgentTurn(transform: (List<Block>) -> List<Block>) {
        _state.update { st ->
            val index = st.turns.indexOfLast { it is Turn.Agent }
            if (index < 0) {
                st
            } else {
                val turn = st.turns[index] as Turn.Agent
                st.copy(
                    turns = st.turns.toMutableList().also { it[index] = turn.copy(blocks = transform(turn.blocks)) },
                )
            }
        }
    }

    /** Updates a block by id across every turn, so older cards stay actionable. */
    private fun mutateBlock(
        blockId: String,
        transform: (Block) -> Block,
    ) {
        mutateBlocks { block -> if (block.id == blockId) transform(block) else block }
    }

    /** Updates every block in every agent turn. */
    private fun mutateBlocks(transform: (Block) -> Block) {
        _state.update { st ->
            st.copy(
                turns =
                    st.turns.map { turn ->
                        if (turn is Turn.Agent) turn.copy(blocks = turn.blocks.map(transform)) else turn
                    },
            )
        }
    }

    private fun nextId(prefix: String): String = "$prefix-${seq++}"

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        private const val MAX_RESULT_CHARS = 4000
        private const val MAX_DIFF_BODY_LINES = 10
        private const val CONTEXT_LINES = 2

        /** How long the agent has to complete initialize and session creation. */
        private const val CONNECT_TIMEOUT_MS = 120_000L

        /** How long a stopped turn waits for the agent before it is abandoned. */
        private const val STOP_GRACE_PERIOD_MS = 5_000L

        /** How long a `session/set_model` request may take. */
        private const val MODEL_SELECT_TIMEOUT_MS = 15_000L

        /** How long a `session/set_mode` request may take. */
        private const val MODE_SELECT_TIMEOUT_MS = 15_000L

        /** How many `@` file matches the composer menu keeps. */
        private const val MENTION_RESULT_LIMIT = 8

        /** Name the agent sees for the IDE's embedded MCP server. */
        private const val IDE_MCP_SERVER_NAME = "jetaprog"

        /** Option ids used for approvals the IDE raises itself (direct file access). */
        private const val ALLOW_OPTION_ID = "allow"

        /** Max characters of a persisted session title. */
        const val TITLE_MAX_CHARS = 40
        private const val REJECT_OPTION_ID = "reject"

        /**
         * Default launch command driving Claude Code over ACP via the Zed adapter.
         * `npx -y` resolves and runs the published adapter without an interactive
         * install prompt, reusing the user's existing Claude Code authentication.
         */
        const val DEFAULT_CLAUDE_CODE_COMMAND: String = "npx -y @zed-industries/claude-code-acp"

        fun textOf(content: ContentBlock): String = (content as? ContentBlock.Text)?.text.orEmpty()

        fun isSessionSurface(update: SessionUpdate): Boolean =
            update is SessionUpdate.Plan ||
                update is SessionUpdate.AvailableCommandsUpdate ||
                update is SessionUpdate.CurrentModeUpdate

        fun toPlanStep(entry: PlanEntry): PlanStep =
            PlanStep(
                content = entry.content,
                status =
                    when (entry.status) {
                        "pending" -> PlanStepStatus.PENDING
                        "in_progress" -> PlanStepStatus.IN_PROGRESS
                        "completed" -> PlanStepStatus.COMPLETED
                        else -> PlanStepStatus.UNKNOWN
                    },
            )

        fun toAgentCommand(command: AvailableCommand): AgentCommand? {
            val name = command.name.trim()
            if (name.isEmpty()) return null
            return AgentCommand(name = name, description = command.description?.takeIf { it.isNotBlank() })
        }

        fun stopStreaming(blocks: List<Block>): List<Block> =
            blocks.map { if (it is Block.Text && it.streaming) it.copy(streaming = false) else it }

        fun mergeResult(
            existing: String?,
            delta: String,
        ): String? {
            if (delta.isBlank()) return existing
            val combined = if (existing.isNullOrBlank()) delta else "$existing\n$delta"
            return combined.take(MAX_RESULT_CHARS)
        }

        fun toolStatusOf(status: ToolCallStatus?): ToolStatus =
            when (status) {
                ToolCallStatus.COMPLETED -> ToolStatus.OK
                ToolCallStatus.FAILED -> ToolStatus.FAILED
                else -> ToolStatus.RUNNING
            }

        fun presenceAction(kind: ToolKind?): String =
            when (kind) {
                ToolKind.READ, ToolKind.SEARCH -> "Reading"
                ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE -> "Editing"
                ToolKind.EXECUTE -> "Running"
                ToolKind.FETCH -> "Fetching"
                else -> "Working"
            }

        fun outcomeFor(optionId: String?): PermissionOutcome =
            if (optionId != null) PermissionOutcome.Selected(optionId) else PermissionOutcome.Cancelled

        /** Buttons for an IDE-raised file read or write. These ids are not ACP kinds. */
        fun directApprovalChoices(): List<ApprovalChoice> =
            listOf(
                ApprovalChoice(ALLOW_OPTION_ID, "Approve & run", ApprovalChoiceRole.ALLOW_ONCE),
                ApprovalChoice(REJECT_OPTION_ID, "Deny", ApprovalChoiceRole.REJECT),
            )

        /**
         * One button per allow and reject kind the agent actually offered.
         *
         * Allow once stays the primary action when both allows are present. A single
         * reject stays labeled Deny. Both reject kinds stay separate so each id can be sent.
         */
        fun approvalChoices(options: List<PermissionOption>): List<ApprovalChoice> {
            val allowOnce = options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE }
            val allowAlways = options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }
            val rejectOnce = options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ONCE }
            val rejectAlways = options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ALWAYS }
            val bothRejects = rejectOnce != null && rejectAlways != null
            return buildList {
                if (allowOnce != null) {
                    add(ApprovalChoice(allowOnce.optionId, "Allow once", ApprovalChoiceRole.ALLOW_ONCE))
                }
                if (allowAlways != null) {
                    add(ApprovalChoice(allowAlways.optionId, "Allow always", ApprovalChoiceRole.ALLOW_ALWAYS))
                }
                if (rejectOnce != null) {
                    add(
                        ApprovalChoice(
                            rejectOnce.optionId,
                            if (bothRejects) "Reject once" else "Deny",
                            ApprovalChoiceRole.REJECT,
                        ),
                    )
                }
                if (rejectAlways != null) {
                    add(
                        ApprovalChoice(
                            rejectAlways.optionId,
                            if (bothRejects) "Reject always" else "Deny",
                            ApprovalChoiceRole.REJECT,
                        ),
                    )
                }
            }
        }

        /** True when [optionId] is an allow-once or allow-always option on this request. */
        fun optionAllowsEdit(
            options: List<PermissionOption>,
            optionId: String?,
        ): Boolean {
            val kind = options.firstOrNull { it.optionId == optionId }?.kind
            return kind == PermissionOptionKind.ALLOW_ONCE || kind == PermissionOptionKind.ALLOW_ALWAYS
        }

        fun commandFor(toolCall: PermissionToolCall): String? =
            stringField(toolCall.rawInput, "command", "cmd") ?: toolCall.title

        fun summarizeArgs(raw: JsonElement?): String {
            val obj = raw as? JsonObject ?: return ""
            for (key in listOf("path", "file_path", "filePath", "abs_path")) {
                stringValue(obj[key])?.let { return it.substringAfterLast('/') }
            }
            for (key in listOf("command", "cmd", "pattern", "query", "url")) {
                stringValue(obj[key])?.let { return it }
            }
            return ""
        }

        fun stringField(
            raw: JsonElement?,
            vararg keys: String,
        ): String? {
            val obj = raw as? JsonObject ?: return null
            for (key in keys) stringValue(obj[key])?.let { return it }
            return null
        }

        fun stringValue(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

        fun computeDiff(
            oldText: String?,
            newText: String,
        ): Triple<List<DiffLine>, Int, Int> {
            val oldLines = oldText?.split("\n").orEmpty()
            val newLines = newText.split("\n")
            var prefix = 0
            while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
            var suffix = 0
            while (
                suffix < oldLines.size - prefix &&
                suffix < newLines.size - prefix &&
                oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
            ) {
                suffix++
            }
            val removed = oldLines.subList(prefix, oldLines.size - suffix).take(MAX_DIFF_BODY_LINES)
            val added = newLines.subList(prefix, newLines.size - suffix).take(MAX_DIFF_BODY_LINES)
            val before = oldLines.subList(maxOf(0, prefix - CONTEXT_LINES), prefix)

            val lines = mutableListOf<DiffLine>()
            var oldNo = prefix - before.size + 1
            var newNo = prefix - before.size + 1
            before.forEach {
                lines += DiffLine(newNo, ' ', it, DiffLineKind.CONTEXT)
                oldNo++
                newNo++
            }
            removed.forEach { lines += DiffLine(oldNo++, '-', it, DiffLineKind.REMOVED) }
            added.forEach { lines += DiffLine(newNo++, '+', it, DiffLineKind.ADDED) }
            return Triple(lines, added.size, removed.size)
        }
    }

    private data class PendingApproval(
        val deferred: CompletableDeferred<RequestPermissionResponse>,
        val choices: List<ApprovalChoice>,
    )

    private data class TurnClaim(
        val client: AcpClient,
        val sessionId: String,
        val generation: Int,
    )

    /**
     * The IDE's embedded MCP server, described for the agent's own MCP client.
     *
     * Empty when the server is not running, in which case the agent falls back to
     * plain file access.
     */
    private fun ideMcpServers(supportsHttp: Boolean): List<McpServer> {
        if (!supportsHttp) return emptyList()
        val endpoint = ideMcpEndpoint() ?: return emptyList()
        return listOf(
            McpServer.Http(
                name = IDE_MCP_SERVER_NAME,
                url = endpoint.url,
                headers =
                    endpoint.authToken
                        ?.let { listOf(HttpHeader(name = "Authorization", value = "Bearer $it")) }
                        .orEmpty(),
            ),
        )
    }
}

/** Plan, commands, or mode id observed before the matching session id is assigned. */
private data class PendingSurface(
    val sessionId: String,
    val plan: List<PlanStep>? = null,
    val commands: List<AgentCommand>? = null,
    val modeId: String? = null,
) {
    /** Whether every stashed field has already been applied or was never set. */
    fun isEmpty(): Boolean = plan == null && commands == null && modeId == null
}

private fun SessionMode.toModeOption(): ModeOption = ModeOption(id = id, name = name.ifBlank { id })

/**
 * Classifies an ACP tool call into the IDE's permission buckets.
 *
 * Agents are not required to send [PermissionToolCall.kind] (the Claude Code
 * adapter omits it), so the raw input is inspected first. Anything that cannot
 * be classified is left unknown and must be confirmed by the user.
 */
internal object AgentPermissionPolicy {
    /**
     * Returns the policy bucket for [toolCall], or null when it cannot be
     * classified and the user must be asked.
     */
    fun kindFor(toolCall: PermissionToolCall): PermissionKind? {
        val input = toolCall.rawInput as? JsonObject
        if (input != null) {
            if (stringField(input, "command", "cmd") != null) return PermissionKind.RUN
            if (stringField(input, "new_string", "old_string", "content") != null) return PermissionKind.EDIT
        }
        when (toolCall.kind) {
            ToolKind.READ, ToolKind.SEARCH, ToolKind.THINK -> return PermissionKind.READ
            ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE -> return PermissionKind.EDIT
            ToolKind.EXECUTE -> return PermissionKind.RUN
            ToolKind.FETCH, ToolKind.OTHER -> return null
            null -> Unit
        }
        if (input != null && stringField(input, "file_path", "filePath", "path", "abs_path") != null) {
            return PermissionKind.READ
        }
        return null
    }

    /** Returns the file path carried by [toolCall]'s raw input, if any. */
    fun pathOf(toolCall: PermissionToolCall): String? {
        val input = toolCall.rawInput as? JsonObject ?: return null
        return stringField(input, "file_path", "filePath", "path", "abs_path")
    }

    private fun stringField(
        input: JsonObject,
        vararg keys: String,
    ): String? = keys.firstNotNullOfOrNull { key -> (input[key] as? JsonPrimitive)?.contentOrNull }
}

/**
 * Connection details for the IDE's embedded MCP server.
 */
public data class IdeMcpEndpoint(
    /** Full endpoint URL, e.g. `http://127.0.0.1:3000/mcp`. */
    val url: String,
    /** Bearer token required by the server, when authentication is enabled. */
    val authToken: String?,
)
