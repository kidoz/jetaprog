package su.kidoz.jetaprog.app.viewmodel

import androidx.compose.ui.text.TextRange
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
import su.kidoz.jetaprog.acp.protocol.StopReason
import su.kidoz.jetaprog.acp.protocol.ToolCallContent
import su.kidoz.jetaprog.acp.protocol.ToolCallStatus
import su.kidoz.jetaprog.acp.protocol.ToolKind
import su.kidoz.jetaprog.acp.protocol.WriteTextFileRequest
import su.kidoz.jetaprog.app.agent.AGENT_BUSY_NOTICE
import su.kidoz.jetaprog.app.agent.AgentBringOutcome
import su.kidoz.jetaprog.app.agent.AgentBroughtChange
import su.kidoz.jetaprog.app.agent.AgentCatalogEntry
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentChoice
import su.kidoz.jetaprog.app.agent.AgentDraft
import su.kidoz.jetaprog.app.agent.AgentDraftAttachment
import su.kidoz.jetaprog.app.agent.AgentFileReview
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentGoalStatus
import su.kidoz.jetaprog.app.agent.AgentHistoryMessage
import su.kidoz.jetaprog.app.agent.AgentHistoryRole
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.agent.AgentLaunch
import su.kidoz.jetaprog.app.agent.AgentMessageCheckpoints
import su.kidoz.jetaprog.app.agent.AgentNavigationStore
import su.kidoz.jetaprog.app.agent.AgentPendingReview
import su.kidoz.jetaprog.app.agent.AgentPrefs
import su.kidoz.jetaprog.app.agent.AgentPrefsStore
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.agent.AgentTaskNavigation
import su.kidoz.jetaprog.app.agent.AgentWorkspaceReviewSource
import su.kidoz.jetaprog.app.agent.AgentWorktree
import su.kidoz.jetaprog.app.agent.AgentWorktreeLauncher
import su.kidoz.jetaprog.app.agent.CLAUDE_AGENT_ID
import su.kidoz.jetaprog.app.agent.CheckpointSkip
import su.kidoz.jetaprog.app.agent.FileSnapshot
import su.kidoz.jetaprog.app.agent.ZCODE_INSTALL_COPIED_NOTICE
import su.kidoz.jetaprog.app.agent.ZCODE_INSTALL_COPY_FAILED_NOTICE
import su.kidoz.jetaprog.app.agent.agentLabel
import su.kidoz.jetaprog.app.agent.agentUnavailableNotice
import su.kidoz.jetaprog.app.agent.checkpointNotice
import su.kidoz.jetaprog.app.agent.defaultLaunch
import su.kidoz.jetaprog.app.agent.discoverAgentCatalog
import su.kidoz.jetaprog.app.agent.discoverInstalledAgents
import su.kidoz.jetaprog.app.agent.legacyClaudeLaunch
import su.kidoz.jetaprog.app.agent.loadProjectInstructions
import su.kidoz.jetaprog.app.agent.restoredLaunch
import su.kidoz.jetaprog.app.agent.toModelOption
import su.kidoz.jetaprog.app.ui.agent.AgentCommand
import su.kidoz.jetaprog.app.ui.agent.AgentConnection
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentMenuRow
import su.kidoz.jetaprog.app.ui.agent.AgentOption
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
import su.kidoz.jetaprog.app.ui.agent.ReviewScope
import su.kidoz.jetaprog.app.ui.agent.SessionRestoration
import su.kidoz.jetaprog.app.ui.agent.ToolStatus
import su.kidoz.jetaprog.app.ui.agent.Turn
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_ALREADY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_BUSY_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_OPENING_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WORKTREE_UNAVAILABLE_NOTICE
import su.kidoz.jetaprog.app.ui.agent.WorkspaceReviewState
import su.kidoz.jetaprog.app.ui.agent.bringableRelativePaths
import su.kidoz.jetaprog.app.ui.agent.chipLabel
import su.kidoz.jetaprog.app.ui.agent.commandExitCode
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
 * @param defaultAgentCommand fallback command used when no installed agent is discovered.
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
 * @param discoverAgents Finds installed agents. The default reads this machine and starts no process.
 * @param discoverCatalog Reads every catalog row, including agents that cannot launch yet.
 * @param agentChoice The agent a new chat launches. Shared with the linked worktree session.
 * @param onAgentSwitch Tells the other checkout to adopt [AgentLaunch]. Returns a notice to refuse
 *   the change, or null when this chat may switch.
 * @param remembersAgentChoice When true, the selected agent is saved as the project default.
 * @param workspaceReviewSource Read-only Git inspection for this checkout; null reports unavailable.
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
    private val discoverAgents: () -> List<AgentLaunch> = { discoverInstalledAgents() },
    private val discoverCatalog: () -> List<AgentCatalogEntry> = { discoverAgentCatalog() },
    private val agentChoice: AgentChoice = AgentChoice(legacyClaudeLaunch(defaultAgentCommand)),
    private val onAgentSwitch: (suspend (AgentLaunch) -> String?)? = null,
    private val remembersAgentChoice: Boolean = true,
    private val workspaceReviewSource: AgentWorkspaceReviewSource? = null,
) : Disposable,
    AcpClientHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fallbackLaunch = legacyClaudeLaunch(defaultAgentCommand)
    private var activeLaunch: AgentLaunch = agentChoice.launch
    private var chosenAgentId: String? = null
    private var installedAgents: List<AgentLaunch> = emptyList()
    private val prefsStore = AgentPrefsStore(fileSystem, projectPath)
    private val historyStore = AgentHistoryStore(fileSystem, projectPath)
    private val navigationStore = AgentNavigationStore(fileSystem, projectPath)
    private val navigationLoaded = CompletableDeferred<Unit>()
    private val navigationMutex = Mutex()
    private var navigationSaveJob: Job? = null
    private var contextReadJob: Job? = null
    private var contextGeneration: Long = 0
    private val pathGuard = WorkspacePathGuard { projectPath }

    private val _state = MutableStateFlow(AgentUiState(workspacePath = projectPath))

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

    /** Coalesces streaming updates without postponing the save on every token. */
    private var recoverySaveJob: Job? = null
    private var recoverySaveDirty = false

    /** Invalidates completions belonging to an abandoned turn. */
    private var turnGeneration = 0

    /** Drops mention results that belong to an older `@` query. */
    private var mentionGeneration = 0

    /** When true, [endTurn] sends the queued follow-up. Stop clears this. */
    private var deliverQueuedPrompt = false
    private var queueVersion = 0L
    private var queueSession = 0L
    private var queueDeliveryJob: Job? = null

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

    /** Full-file baselines and expected contents used by Keep and Revert. */
    private val fileReview = AgentFileReview()
    private var workspaceReviewEpoch = 0
    private var workspaceRequest = 0
    private var workspaceBaselineAttempted = false

    /** File baselines taken at each user message. Not persisted. */
    private val checkpoints = AgentMessageCheckpoints()

    /** Paths whose edit permission was already granted for an upcoming write. */
    private val approvedEditPaths = mutableMapOf<String, Int>()

    /** Runs process teardown off the UI thread after [dispose]. */
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The model last chosen for each agent id. */
    private val modelIds = mutableMapOf<String, String>()

    /** The single model from a preference file written before per-agent models. */
    private var legacyModelId: String? = null

    /** Completes once the on-disk preferences have been applied. */
    private val prefsLoaded = CompletableDeferred<AgentPrefs>()

    init {
        scope.launch {
            val navigation = navigationStore.load()
            _state.update { it.copy(taskNavigation = navigation) }
            applyDraft(navigation.newChatDraft)
            navigationLoaded.complete(Unit)
            val prefs = prefsStore.load()
            legacyModelId = prefs.modelId
            modelIds.putAll(prefs.modelIds)
            val legacyOwner = prefs.agentId?.takeIf { it.isNotBlank() } ?: CLAUDE_AGENT_ID
            val legacyModel = prefs.modelId
            if (legacyModel != null) modelIds.putIfAbsent(legacyOwner, legacyModel)
            val installed = discoverAgents()
            if (remembersAgentChoice) {
                val preferred = defaultLaunch(prefs.agentId, installed, fallbackLaunch)
                agentChoice.launch = preferred
                activeLaunch = preferred
                chosenAgentId = preferred.id
            } else {
                activeLaunch = agentChoice.launch
            }
            publishAgents(installed)
            _state.update {
                it.copy(
                    permissions = prefs.permissions,
                    docked = prefs.docked,
                )
            }
            prefsLoaded.complete(prefs)
        }
    }

    /** Waits for checkout-local drafts and agent preferences without starting an agent process. */
    internal suspend fun awaitInitialization() {
        prefsLoaded.await()
    }

    /** Reuses a validated linked checkout for later `/worktree` commands. */
    internal fun rememberLinkedWorktree(tree: AgentWorktree) {
        linkedWorktree = tree
    }

    /** Explains why a saved checkout pair could not be reopened. */
    internal fun reportCheckoutRestoreFailure(message: String) {
        _state.update { it.copy(worktreeNotice = message) }
    }

    /** Dispatches a user [intent]. */
    public fun dispatch(intent: AgentIntent) {
        if (
            intent is AgentIntent.RestoreSession ||
            intent == AgentIntent.NewChat ||
            intent is AgentIntent.SelectAgent
        ) {
            sessionChangeJob?.cancel()
            sessionChangeJob = scope.launch { sendMutex.withLock { handle(intent) } }
        } else {
            scope.launch { handle(intent) }
        }
    }

    private suspend fun handle(intent: AgentIntent) {
        navigationLoaded.await()
        when (intent) {
            AgentIntent.EnsureConnected -> {
                sendMutex.withLock {
                    prefsLoaded.await()
                    publishAgents(discoverAgents())
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

            AgentIntent.ShowReview -> {
                showReview(clearComposer = false)
            }

            is AgentIntent.SetReviewScope -> {
                _state.update { it.copy(reviewScope = intent.scope) }
                if (intent.scope == ReviewScope.WORKSPACE) refreshWorkspaceReview()
            }

            AgentIntent.RefreshWorkspaceReview -> {
                refreshWorkspaceReview()
            }

            is AgentIntent.PreviewWorkspaceChange -> {
                previewWorkspaceChange(intent.path)
            }

            AgentIntent.Stop -> {
                handleStop()
            }

            AgentIntent.NewChat -> {
                startNewChat()
            }

            AgentIntent.ShowContext -> {
                _state.update { it.copy(contextVisible = true) }
                refreshContextPreview()
            }

            AgentIntent.RefreshContext -> {
                if (_state.value.contextVisible) refreshContextPreview()
            }

            AgentIntent.HideContext -> {
                contextGeneration++
                contextReadJob?.cancel()
                _state.update { it.copy(contextVisible = false) }
            }

            AgentIntent.ShowHistory -> {
                rememberDraft()
                archiveConversation()
                _state.update { it.copy(sessionHistory = historyStore.list(), historyVisible = true) }
            }

            AgentIntent.HideHistory -> {
                _state.update { it.copy(historyVisible = false) }
            }

            is AgentIntent.SetSessionPinned -> {
                organizeTask(intent.recordId) { it.copy(pinned = intent.pinned) }
            }

            is AgentIntent.SetSessionArchived -> {
                organizeTask(intent.recordId) { it.copy(archived = intent.archived) }
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

            is AgentIntent.SelectAgent -> {
                selectAgent(intent.agentId)
            }

            AgentIntent.RefreshAgents -> {
                prefsLoaded.await()
                publishAgents(discoverAgents())
            }

            is AgentIntent.ReportAgentInstallCopy -> {
                _state.update {
                    it.copy(
                        agentNotice =
                            if (intent.copied) {
                                ZCODE_INSTALL_COPIED_NOTICE
                            } else {
                                ZCODE_INSTALL_COPY_FAILED_NOTICE
                            },
                    )
                }
            }

            AgentIntent.ExpandToPerspective -> {
                _state.update { it.copy(docked = false) }
                persist()
            }

            AgentIntent.DockToToolWindow -> {
                _state.update { it.copy(docked = true) }
                persist()
            }

            is AgentIntent.SaveConversationView -> {
                val navigation = _state.value.taskNavigation
                if (intent.recordId == recordId || intent.recordId in navigation.tasks) {
                    val task = navigation.tasks[intent.recordId] ?: AgentTaskNavigation()
                    val view =
                        intent.view.copy(
                            index = intent.view.index.coerceAtLeast(0),
                            offset = intent.view.offset.coerceAtLeast(0),
                        )
                    if (view != task.view) {
                        _state.update {
                            it.copy(
                                taskNavigation =
                                    it.taskNavigation.copy(
                                        tasks =
                                            it.taskNavigation.tasks + (intent.recordId to task.copy(view = view)),
                                    ),
                            )
                        }
                        scheduleNavigationSave()
                    }
                }
            }

            is AgentIntent.SetComposerInput -> {
                updateComposerInput(intent.text, intent.selectionStart, intent.selectionEnd)
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

            is AgentIntent.RemoveQueuedPrompt -> {
                changeQueue { queue -> queue.filterNot { it.id == intent.id } }
            }

            AgentIntent.PauseQueue -> {
                disarmQueue()
            }

            AgentIntent.ResumeQueue -> {
                deliverQueuedPrompt = true
                _state.update { it.copy(queuePaused = false) }
                startQueueDelivery()
            }

            is AgentIntent.UpdateQueuedPrompt -> {
                val text = intent.prompt.trim()
                if (text.isNotEmpty()) {
                    changeQueue { queue ->
                        queue.map { if (it.id == intent.id) it.copy(prompt = text) else it }
                    }
                }
            }

            is AgentIntent.MoveQueuedPrompt -> {
                changeQueue { queue ->
                    val index = queue.indexOfFirst { it.id == intent.id }
                    val target = index + if (intent.earlier) -1 else 1
                    if (index < 0 || target !in queue.indices) {
                        queue
                    } else {
                        queue.toMutableList().apply {
                            add(target, removeAt(index))
                        }
                    }
                }
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
        rememberDraft()
    }

    // ------------------------------------------------------------------------
    // Agent picker
    // ------------------------------------------------------------------------

    /**
     * Archives this chat and launches [launch] on the other checkout when that checkout is idle.
     *
     * Returns a notice when the other checkout is busy. The caller's own process stays up until
     * the caller switches too.
     */
    internal suspend fun acceptSharedAgent(launch: AgentLaunch) {
        sendMutex.withLock {
            agentChoice.launch = launch
            if (remembersAgentChoice) {
                chosenAgentId = launch.id
                persist()
            }
            if (launch != activeLaunch) switchOnto(launch, reconnect = client != null)
        }
    }

    /**
     * Points this checkout at [launch].
     *
     * A connected process is replaced. A checkout that has not connected yet keeps the launch
     * for its next connection.
     */
    internal suspend fun applySharedAgent(launch: AgentLaunch) {
        sendMutex.withLock {
            if (launch == activeLaunch) return@withLock
            switchOnto(launch, reconnect = client != null)
        }
    }

    private suspend fun selectAgent(agentId: String) {
        prefsLoaded.await()
        val installed = discoverAgents()
        val catalog = discoverCatalog()
        publishAgents(installed, catalog)
        val launch = installed.firstOrNull { it.id == agentId } ?: claudeAdapterFallback(agentId, catalog) ?: return
        if (launch == activeLaunch) {
            _state.update { it.copy(agentNotice = null) }
            return
        }
        if (_state.value.isStreaming) {
            _state.update { it.copy(agentNotice = AGENT_BUSY_NOTICE) }
            return
        }
        val previous = agentChoice.launch
        agentChoice.launch = launch
        val refusal = onAgentSwitch?.invoke(launch)
        if (refusal != null) {
            agentChoice.launch = previous
            _state.update { it.copy(agentNotice = refusal) }
            return
        }
        if (remembersAgentChoice) {
            chosenAgentId = launch.id
            persist()
        }
        switchOnto(launch, reconnect = true)
    }

    private suspend fun switchOnto(
        launch: AgentLaunch,
        reconnect: Boolean,
    ) {
        rememberDraft()
        saveNavigation()
        clearQueuedPrompt()
        freezeGoalPaused(notice = null, keepNotice = true)
        archiveConversation()
        clearFileMemory()
        sessionId = null
        conversationSessionId = null
        recordId = null
        resetGoalLoop()
        activeLaunch = launch
        if (client != null) resetConnection()
        _state.update {
            it.copy(
                turns = emptyList(),
                interrupted = false,
                isStreaming = false,
                presence = null,
                error = null,
                restoration = null,
                activeRecordId = null,
                goal = null,
                goalNotice = null,
                reviewVisible = false,
                reviewNotice = null,
                agentNotice = null,
                connection = if (client == null) AgentConnection.DISCONNECTED else it.connection,
                agent = AgentOption(launch.id, launch.label),
            )
        }
        // Changing providers carries the visible prompt into the new provider's chat.
        rememberDraft()
        publishAgents(installedAgents)
        if (reconnect && ensureConnected()) {
            client?.let { ensureSession(it) }
        }
    }

    /** Returns a new chat to the saved agent after a restored chat used a different one. */
    private suspend fun alignWithChosenAgent() {
        val chosen = agentChoice.launch
        if (chosen == activeLaunch) return
        activeLaunch = chosen
        if (client != null) resetConnection()
        _state.update {
            it.copy(
                connection = if (client == null) AgentConnection.DISCONNECTED else it.connection,
                agent = AgentOption(chosen.id, chosen.label),
            )
        }
        publishAgents(installedAgents)
    }

    /**
     * Selects the agent stored on [record] before `session/load`.
     *
     * Returns false when that agent is not installed. The current process is closed so a later
     * message cannot resume the chat on a different agent.
     */
    private suspend fun prepareRestoredAgent(record: AgentSessionRecord): Boolean {
        val installed = discoverAgents()
        val target = restoredLaunch(record.agentId, installed, fallbackLaunch)
        if (target == null) {
            publishAgents(installed)
            if (client != null) resetConnection()
            val notice = agentUnavailableNotice(agentLabel(record.agentId))
            _state.update {
                it.copy(
                    connection = AgentConnection.DISCONNECTED,
                    agentNotice = notice,
                )
            }
            return false
        }
        if (target != activeLaunch) {
            if (client != null) resetConnection()
            activeLaunch = target
        }
        publishAgents(installed)
        _state.update { it.copy(agentNotice = null) }
        return true
    }

    private fun publishAgents(
        installed: List<AgentLaunch>,
        catalog: List<AgentCatalogEntry> = discoverCatalog(),
    ) {
        installedAgents = installed
        _state.update {
            it.copy(
                availableAgents = installed.map { launch -> AgentOption(launch.id, launch.label) },
                agentMenu = catalog.map { entry -> entry.toMenuRow() },
                agent = AgentOption(activeLaunch.id, activeLaunch.label),
            )
        }
    }

    /** The npx Claude adapter, when the catalog row is the selectable fallback. */
    private fun claudeAdapterFallback(
        agentId: String,
        catalog: List<AgentCatalogEntry>,
    ): AgentLaunch? {
        if (agentId != CLAUDE_AGENT_ID) return null
        val entry = catalog.firstOrNull { it.id == CLAUDE_AGENT_ID } ?: return null
        if (entry.countsAsInstalled || entry.launch == null) return null
        return fallbackLaunch
    }

    private fun AgentCatalogEntry.toMenuRow(): AgentMenuRow =
        AgentMenuRow(
            id = id,
            name = label,
            detail = detail,
            selectable = launch != null,
            installCommand = installCommand,
            warning = warning,
        )

    // ------------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------------

    private val connectionMutex = Mutex()

    private suspend fun ensureConnected(): Boolean =
        connectionMutex.withLock {
            val current = client
            if (current != null && current.isOpen && _state.value.connection == AgentConnection.CONNECTED) return true
            if (current != null) resetConnection()
            if (activeLaunch.command.isEmpty()) {
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
                    command = activeLaunch.command,
                    workingDirectory = projectPath,
                    environment = activeLaunch.environment,
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
     * Serializes [prompt] through the follow-up queue so connection and instruction
     * preparation cannot reorder concurrent submissions.
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
        queueFollowUp(text, attachments, steer, clearComposer)
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
        queuedId: String? = null,
        expectedQueueVersion: Long = 0,
    ): Boolean {
        val text = prompt.trim()
        if (text.isEmpty()) return false
        val instructions = readProjectInstructions()
        val claim =
            claimTurn(text, attachments, clearComposer, instructions != null, queuedId, expectedQueueVersion)
                ?: return false
        try {
            val body = wirePrompt ?: promptWithContext(text, attachments)
            val result = claim.client.promptText(claim.sessionId, promptWithInstructions(instructions, body))
            if (claim.generation == turnGeneration) {
                if (result.stopReason != StopReason.END_TURN) {
                    _state.update { it.copy(interrupted = true) }
                }
                endTurn()
            }
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
    private suspend fun readProjectInstructions(): String? = loadProjectInstructions(fileSystem, projectPath).text

    private fun refreshContextPreview() {
        val generation = ++contextGeneration
        contextReadJob?.cancel()
        _state.update { it.copy(instructionPreview = it.instructionPreview.copy(loading = true)) }
        contextReadJob =
            scope.launch {
                val preview = loadProjectInstructions(fileSystem, projectPath)
                if (generation == contextGeneration) _state.update { it.copy(instructionPreview = preview) }
            }
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
        queuedId: String? = null,
        expectedQueueVersion: Long = 0,
    ): TurnClaim? {
        // Connection setup can still own the mutex after CONNECTED is published.
        // Keep the prompt queued until setup finishes; cancellation intents remain dispatchable.
        sendMutex.lock()
        try {
            if (_state.value.isStreaming || !canDeliverQueued(queuedId, expectedQueueVersion)) return null
            if (!ensureConnected()) return null
            val acpClient = client ?: return null
            val draftOwner = recordId
            val activeSession = ensureSession(acpClient) ?: return null
            if (!canDeliverQueued(queuedId, expectedQueueVersion)) return null
            if (!captureWorkspaceBaseline() || !canDeliverQueued(queuedId, expectedQueueVersion)) return null
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
                    queuedPrompts = if (queuedId == null) it.queuedPrompts else it.queuedPrompts.drop(1),
                    interrupted = false,
                    error = null,
                    goalNotice = null,
                    presence = Presence(action = "Thinking", fileName = null, startedAtEpochMillis = now()),
                    composerInput = if (clearComposer) "" else it.composerInput,
                    composerAttachments = if (clearComposer) emptyList() else it.composerAttachments,
                    mentionFiles = if (clearComposer) emptyList() else it.mentionFiles,
                )
            }
            if (clearComposer) rememberDraft(draftOwner)
            if (draftOwner == null) {
                // Text typed during connection setup belongs to this newly claimed conversation.
                _state.update { it.copy(taskNavigation = it.taskNavigation.copy(newChatDraft = AgentDraft())) }
                rememberDraft()
            }
            persistConversation()
            return TurnClaim(acpClient, activeSession, generation)
        } finally {
            sendMutex.unlock()
        }
    }

    private suspend fun handleStop() {
        disarmQueue()
        freezeGoalPaused(notice = null, keepNotice = true)
        val acpClient = client ?: return
        val activeSession = sessionId ?: return
        if (!_state.value.isStreaming) return
        _state.update { it.copy(interrupted = true) }

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
                    resetConnection()
                    _state.update { it.copy(connection = AgentConnection.DISCONNECTED) }
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
        recoverySaveJob?.cancel()
        recoverySaveJob = null
        queueDeliveryJob?.cancel()
        queueDeliveryJob = null
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cancelPendingApprovals()
        approvedEditPaths.clear()
    }

    private suspend fun endTurn() {
        recoverySaveJob?.cancel()
        recoverySaveJob = null
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cancelPendingApprovals()
        approvedEditPaths.clear()
        val state = _state.value
        if (!state.error.isNullOrBlank() || (state.interrupted && state.queuedPrompts.firstOrNull()?.steer != true)) {
            disarmQueue()
        }
        _state.update { st ->
            val turns =
                st.turns.map { turn ->
                    if (turn is Turn.Agent) {
                        turn.copy(
                            blocks =
                                turn.blocks.map { block ->
                                    when (block) {
                                        is Block.Text -> {
                                            block.copy(streaming = false)
                                        }

                                        is Block.Tool -> {
                                            if (block.status == ToolStatus.RUNNING) {
                                                block.copy(
                                                    status =
                                                        if (state.interrupted) {
                                                            ToolStatus.INTERRUPTED
                                                        } else {
                                                            ToolStatus.FAILED
                                                        },
                                                )
                                            } else {
                                                block
                                            }
                                        }

                                        else -> {
                                            block
                                        }
                                    }
                                },
                        )
                    } else {
                        turn
                    }
                }
            st.copy(
                isStreaming = false,
                presence = null,
                turns = turns,
            )
        }
        // Capture before another history action can replace the visible conversation.
        persistConversation()
        if (deliverQueuedPrompt && _state.value.queuedPrompts.isNotEmpty()) {
            startQueueDelivery()
            return
        }
        if (queueDeliveryJob?.isActive == true) return
        // The check can mark the goal met or paused after the snapshot above.
        continueAfterQueue()
        persistConversation()
    }

    /** Writes the visible conversation, including the goal, without blocking the turn. */
    private fun persistConversation() {
        val record = conversationRecord() ?: return
        scope.launch {
            historyMutex.withLock { historyStore.save(record) }
        }
    }

    /** Saves partial work after a bounded delay, even when the agent never finishes its turn. */
    private fun scheduleRecoverySave() {
        if (!_state.value.isStreaming) return
        recoverySaveDirty = true
        if (recoverySaveJob?.isActive == true) return
        val generation = turnGeneration
        recoverySaveJob =
            scope.launch {
                do {
                    delay(RECOVERY_SAVE_DELAY_MS)
                    if (generation != turnGeneration || !_state.value.isStreaming) return@launch
                    recoverySaveDirty = false
                    archiveConversation()
                } while (recoverySaveDirty)
            }
    }

    /** Claims queued input only if Stop, editing, or a session switch has not invalidated it. */
    private fun canDeliverQueued(
        id: String?,
        version: Long,
    ): Boolean =
        id == null ||
            (
                deliverQueuedPrompt && version == queueVersion && _state.value.queuedPrompts
                    .firstOrNull()
                    ?.id == id
            )

    /** One drain job serializes delivery; each item leaves the queue only after its turn is claimed. */
    private fun startQueueDelivery() {
        if (_state.value.isStreaming || !deliverQueuedPrompt || queueDeliveryJob?.isActive == true) return
        val session = queueSession
        queueDeliveryJob =
            scope.launch {
                while (session == queueSession && deliverQueuedPrompt && !_state.value.isStreaming) {
                    val queued = _state.value.queuedPrompts.firstOrNull() ?: break
                    val version = queueVersion
                    val sent =
                        handleSend(
                            queued.prompt,
                            queued.attachments,
                            clearComposer = false,
                            queuedId = queued.id,
                            expectedQueueVersion = version,
                        )
                    if (!sent && version == queueVersion) {
                        if (queued.restoreComposerOnFailure && _state.value.composerInput.isBlank()) {
                            _state.update {
                                it.copy(
                                    composerInput = queued.prompt,
                                    composerAttachments = queued.attachments,
                                    queuedPrompts =
                                        it.queuedPrompts.filterNot { item -> item.id == queued.id },
                                )
                            }
                        }
                        rememberDraft()
                        disarmQueue()
                        break
                    }
                }
                if (session == queueSession) {
                    queueDeliveryJob = null
                    if (!_state.value.isStreaming) continueAfterQueue()
                    persistConversation()
                }
            }
    }

    private suspend fun continueAfterQueue() {
        if (_state.value.queuedPrompts.isNotEmpty() && !deliverQueuedPrompt) {
            freezeGoalPaused(notice = "Follow-up queue paused. Resume when ready.", keepNotice = false)
        } else {
            continueGoal()
        }
    }

    private fun changeQueue(change: (List<QueuedFollowUp>) -> List<QueuedFollowUp>) {
        queueVersion++
        _state.update { state ->
            state.copy(
                queuedPrompts =
                    change(state.queuedPrompts).mapIndexed { index, item ->
                        if (index == 0) item else item.copy(steer = false)
                    },
            )
        }
        steerArmed = steerArmed && _state.value.queuedPrompts
            .firstOrNull()
            ?.steer == true
        if (_state.value.queuedPrompts.isEmpty()) disarmQueue()
    }

    private suspend fun queueFollowUp(
        text: String,
        attachments: List<ComposerAttachment>,
        steer: Boolean,
        clearComposer: Boolean,
    ) {
        val entry =
            QueuedFollowUp(
                text,
                attachments,
                steer,
                id = nextId("queued"),
                restoreComposerOnFailure = clearComposer,
            )
        val wasEmpty = _state.value.queuedPrompts.isEmpty()
        if (wasEmpty || steer) {
            deliverQueuedPrompt = true
            _state.update { it.copy(queuePaused = false) }
        }
        if (steer) {
            queueVersion++
            steerArmed = true
        }
        if (clearComposer) mentionGeneration++
        _state.update { st ->
            st.copy(
                queuedPrompts =
                    if (steer) {
                        listOf(entry) + st.queuedPrompts.map { it.copy(steer = false) }
                    } else {
                        st.queuedPrompts + entry
                    },
                composerInput = if (clearComposer) "" else st.composerInput,
                composerAttachments = if (clearComposer) emptyList() else st.composerAttachments,
                mentionFiles = if (clearComposer) emptyList() else st.mentionFiles,
            )
        }
        rememberDraft()
        if (steer && _state.value.isStreaming) steerCancelIfIdle()
        startQueueDelivery()
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
        queueVersion++
        deliverQueuedPrompt = false
        steerArmed = false
        _state.update { st ->
            st.copy(queuePaused = true, queuedPrompts = st.queuedPrompts.map { it.copy(steer = false) })
        }
    }

    private fun clearQueuedPrompt() {
        disarmQueue()
        queueSession++
        queueDeliveryJob?.cancel()
        queueDeliveryJob = null
        _state.update { it.copy(queuedPrompts = emptyList(), queuePaused = false) }
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
    private suspend fun showReview(clearComposer: Boolean) {
        val files = reviewFiles(_state.value.turns)
        _state.update {
            it.copy(
                reviewVisible = true,
                reviewScope = if (files.isEmpty()) ReviewScope.WORKSPACE else ReviewScope.AGENT,
                reviewNotice = null,
                composerInput = if (clearComposer) "" else it.composerInput,
            )
        }
        if (files.isEmpty()) refreshWorkspaceReview()
    }

    private suspend fun captureWorkspaceBaseline(): Boolean {
        if (workspaceBaselineAttempted || _state.value.turns.isNotEmpty()) return true
        workspaceBaselineAttempted = true
        val epoch = workspaceReviewEpoch
        val baseline =
            workspaceRead { workspaceReviewSource?.changes() ?: unavailableWorkspaceReview() }
                .getOrNull()
                ?.map { it.path }
                ?.toSet()
        if (epoch != workspaceReviewEpoch) return false
        _state.update { it.copy(workspaceReview = it.workspaceReview.copy(preExistingPaths = baseline)) }
        return true
    }

    private suspend fun refreshWorkspaceReview() {
        val request = ++workspaceRequest
        val revision = _state.value.workspaceRevision
        _state.update {
            it.copy(
                workspaceReview =
                    it.workspaceReview.copy(
                        loading = true,
                        error = null,
                        preview = null,
                        previewPath = null,
                    ),
            )
        }
        val result = workspaceRead { workspaceReviewSource?.changes() ?: unavailableWorkspaceReview() }
        if (request != workspaceRequest) return
        _state.update {
            it.copy(
                workspaceReview =
                    it.workspaceReview.copy(
                        files = result.getOrNull().orEmpty(),
                        loading = false,
                        revision = revision,
                        error =
                            result.exceptionOrNull()?.let { error ->
                                error.message ?: "Workspace inspection failed."
                            },
                    ),
            )
        }
    }

    private suspend fun previewWorkspaceChange(path: String) {
        val change =
            _state.value.workspaceReview.files
                .firstOrNull { it.path == path } ?: return
        val request = ++workspaceRequest
        _state.update {
            it.copy(
                workspaceReview =
                    it.workspaceReview.copy(
                        loading = true,
                        error = null,
                        previewPath = path,
                        preview = null,
                    ),
            )
        }
        val result = workspaceRead { workspaceReviewSource?.preview(change) ?: unavailableWorkspaceReview() }
        if (request != workspaceRequest) return
        _state.update {
            it.copy(
                workspaceReview =
                    it.workspaceReview.copy(
                        loading = false,
                        preview = result.getOrNull(),
                        error =
                            result.exceptionOrNull()?.let { error ->
                                error.message ?: "Workspace inspection failed."
                            },
                    ),
            )
        }
    }

    private fun <T> unavailableWorkspaceReview(): Result<T> =
        Result.failure(IllegalStateException("Workspace review is unavailable for this checkout."))

    private suspend fun <T> workspaceRead(read: suspend () -> Result<T>): Result<T> =
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
        file.pendingBlockIds.firstOrNull()?.let { blockId -> decideDiff(blockId, accept) }
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
            fileReview.record(path, FileSnapshot(existed, current), FileSnapshot(change.exists, incoming))
            val (lines, added, removed) = computeDiff(current, incoming.orEmpty())
            blocks +=
                Block.Diff(
                    id = "${nextId("bring-diff")}:diff:$index",
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
        if (written.isNotEmpty()) {
            invalidateCheckEvidence()
            onWorkspaceFilesChanged(written)
        }
        publishReviewCounts()
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
        if (_state.value.interrupted) {
            freezeGoalPaused(
                notice = "The agent stopped before completing the turn. Resume when ready.",
                keepNotice = false,
            )
            return
        }
        if (!_state.value.error.isNullOrBlank()) {
            freezeGoalPaused(notice = GOAL_FAILED_NOTICE, keepNotice = false)
            return
        }
        if (goalChecking) {
            val evidence =
                goalEvidence(
                    _state.value.turns.drop(goalBaseline),
                    _state.value.plan,
                    _state.value.workspaceRevision,
                    _state.value.hasUnsavedEdits,
                )
            val reply = lastAgentReply()
            val claimsCompletion =
                reply
                    .lineSequence()
                    .lastOrNull { it.isNotBlank() }
                    ?.trim()
                    ?.startsWith("GOAL_MET:") == true
            if (claimsCompletion && evidence.commandOutputs.isEmpty()) {
                freezeGoalPaused(
                    notice = "Completion is unverified. Check the command evidence before resuming the goal.",
                    keepNotice = false,
                )
                return
            }
            val verdict = judgeGoal(reply, evidence)
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

    private fun updateComposerInput(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        val textChanged = _state.value.composerInput != text
        val query = mentionQuery(text)
        _state.update { st ->
            st.copy(
                composerInput = text,
                composerSelection =
                    TextRange(
                        selectionStart.coerceIn(0, text.length),
                        selectionEnd.coerceIn(0, text.length),
                    ),
                composerSelectionText = text,
                mentionFiles = if (query.isNullOrEmpty()) emptyList() else st.mentionFiles,
            )
        }
        rememberDraft()
        if (!textChanged) return
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
        rememberDraft()
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
        rememberDraft()
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

    private fun rememberDraft(owner: String? = recordId) {
        val state = _state.value
        val draft =
            AgentDraft(
                state.composerInput,
                state.composerAttachments.map { AgentDraftAttachment(it.label, it.path, it.excerpt) },
                selectionStart =
                    state.composerSelection.takeIf { state.composerSelectionText == state.composerInput }?.start
                        ?: state.composerInput.length,
                selectionEnd =
                    state.composerSelection.takeIf { state.composerSelectionText == state.composerInput }?.end
                        ?: state.composerInput.length,
            )
        val navigation = state.taskNavigation
        val updated =
            if (owner == null) {
                navigation.copy(newChatDraft = draft)
            } else {
                val task = navigation.tasks[owner] ?: AgentTaskNavigation()
                navigation.copy(tasks = navigation.tasks + (owner to task.copy(draft = draft)))
            }
        if (navigation == updated) return
        _state.update { it.copy(taskNavigation = updated) }
        scheduleNavigationSave()
    }

    private fun scheduleNavigationSave() {
        navigationSaveJob?.cancel()
        navigationSaveJob =
            scope.launch {
                delay(RECOVERY_SAVE_DELAY_MS)
                saveNavigation()
            }
    }

    private fun applyDraft(draft: AgentDraft) {
        mentionGeneration++
        _state.update {
            it.copy(
                composerInput = draft.text,
                composerSelection =
                    TextRange(
                        draft.selectionStart.coerceIn(0, draft.text.length),
                        draft.selectionEnd.coerceIn(0, draft.text.length),
                    ),
                composerSelectionText = draft.text,
                composerAttachments =
                    draft.attachments.map { item ->
                        ComposerAttachment(item.label, item.path, item.excerpt)
                    },
                mentionFiles = emptyList(),
            )
        }
    }

    private suspend fun saveNavigation() {
        navigationMutex.withLock { navigationStore.save(_state.value.taskNavigation) }
    }

    private suspend fun organizeTask(
        id: String,
        change: (AgentTaskNavigation) -> AgentTaskNavigation,
    ) {
        if (id != recordId && _state.value.sessionHistory.none { it.id == id }) return
        rememberDraft()
        _state.update {
            val navigation = it.taskNavigation
            it.copy(
                taskNavigation =
                    navigation.copy(
                        tasks =
                            navigation.tasks + (id to change(navigation.tasks[id] ?: AgentTaskNavigation())),
                    ),
            )
        }
        saveNavigation()
    }

    /**
     * Persists the conversation text and its message checkpoints.
     *
     * Tool cards, diff cards, and approvals are left out of the file.
     * Creates the record on first use and upserts afterwards.
     */
    private suspend fun archiveConversation() {
        conversationRecord()?.let { record -> historyMutex.withLock { historyStore.save(record) } }
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
                            listOf(AgentHistoryMessage(AgentHistoryRole.USER, turn.text, id = turn.id))
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
                isStreaming = _state.value.isStreaming,
                error = _state.value.error,
                live = false,
                interrupted = _state.value.interrupted,
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
            checkpoints = checkpoints.persistedCheckpoints(),
            checkpointDisk = checkpoints.persistedDisk(),
            agentId = activeLaunch.id,
            pendingReviews = pendingReviewRecords(),
            failure = _state.value.error,
            interrupted = _state.value.interrupted || _state.value.isStreaming,
        )
    }

    /** Starts a fresh chat, cancelling the current turn and abandoning its ACP session. */
    private suspend fun startNewChat() {
        rememberDraft()
        saveNavigation()
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
                interrupted = false,
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
        applyDraft(_state.value.taskNavigation.newChatDraft)
        alignWithChosenAgent()
    }

    /** Deletes a persisted conversation, clearing it when it is the active one. */
    private suspend fun deleteSession(recordIdToDelete: String) {
        if (recordIdToDelete == recordId) sessionChangeJob?.cancelAndJoin()
        sendMutex.withLock { deleteSessionRecord(recordIdToDelete) }
    }

    private suspend fun deleteSessionRecord(recordIdToDelete: String) {
        historyMutex.withLock { historyStore.remove(recordIdToDelete) }
        _state.update {
            it.copy(
                taskNavigation =
                    it.taskNavigation.copy(
                        tasks =
                            it.taskNavigation.tasks - recordIdToDelete,
                    ),
            )
        }
        saveNavigation()
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
                interrupted = false,
                error = null,
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
        applyDraft(_state.value.taskNavigation.newChatDraft)
    }

    /**
     * Rebuilds the surface turns from a persisted transcript and reloads the
     * agent's session when the record carries its identifier.
     */
    private suspend fun restoreSession(recordIdToRestore: String) {
        val record = historyStore.list().firstOrNull { it.id == recordIdToRestore } ?: return
        rememberDraft()
        saveNavigation()
        clearQueuedPrompt()
        val acpClient = client
        val previousSession = sessionId
        val wasStreaming = _state.value.isStreaming
        invalidateTurn()
        if (wasStreaming && acpClient != null && previousSession != null) {
            runCatching { acpClient.cancel(previousSession) }
        }
        // Archive while the outgoing chat still has its checkpoints.
        archiveConversation()
        clearFileMemory()
        _state.update { it.copy(isStreaming = false, presence = null) }

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
                val generated = "restored-${record.id}-${counter++}"
                val id = message.id?.takeIf { it.isNotBlank() } ?: generated
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
                turns = turns + restoredReviewTurns(record),
                historyVisible = false,
                error = record.failure,
                interrupted = record.interrupted,
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
        applyDraft(
            _state.value.taskNavigation.tasks[record.id]
                ?.draft ?: AgentDraft(),
        )
        checkpoints.loadPersisted(record.checkpoints, record.checkpointDisk)
        publishCheckpointFlags()
        if (!prepareRestoredAgent(record)) {
            transcriptOnly(agentUnavailableNotice(agentLabel(record.agentId)))
            return
        }
        if (record.agentSessionId == null) {
            transcriptOnly("This saved conversation has no agent session to resume.")
        } else if (ensureConnected()) {
            client?.let { restoreAgentSession(it, record) }
        } else {
            transcriptOnly("Could not reconnect to the agent.")
        }
        _state.update { it.copy(error = it.error ?: record.failure) }
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

    /** Invalidates command evidence when an agent, editor, or external process changes the workspace. */
    public fun invalidateCheckEvidence() {
        _state.update { it.copy(workspaceRevision = it.workspaceRevision + 1) }
    }

    /** Marks unsaved editor content that cannot have been checked by a disk-based command. */
    public fun setCheckUnsavedEdits(dirty: Boolean) {
        _state.update { it.copy(hasUnsavedEdits = dirty) }
    }

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
                    rawOutput = update.rawOutput,
                )
            }

            is SessionUpdate.ToolCallProgress -> {
                applyToolCall(
                    id = update.toolCallId,
                    title = update.title,
                    kind = update.kind,
                    status = update.status,
                    content = update.content.orEmpty(),
                    location = update.locations.firstOrNull()?.path,
                    rawInput = update.rawInput,
                    rawOutput = update.rawOutput,
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
        rawOutput: JsonElement?,
    ) {
        val resultText =
            content
                .filter { it.type != "diff" }
                .mapNotNull {
                    it.content?.let(
                        ::textOf,
                    )
                }.joinToString("\n")
        if (kind in setOf(ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE) || content.any { it.type == "diff" }) {
            invalidateCheckEvidence()
        }
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
                                kind = kind?.value ?: existing.kind,
                                args = rawInput?.let(::summarizeArgs) ?: existing.args,
                                status = if (status == null) existing.status else toolStatus,
                                command = stringField(rawInput, "command", "cmd") ?: existing.command,
                                exitCode = commandExitCode(rawOutput) ?: existing.exitCode,
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
                            command = stringField(rawInput, "command", "cmd"),
                            exitCode = commandExitCode(rawOutput),
                            checkRevision =
                                if (kind == ToolKind.EXECUTE && toolStatus == ToolStatus.RUNNING &&
                                    _state.value.isStreaming && _state.value.restoration !is SessionRestoration.Loading
                                ) {
                                    _state.value.workspaceRevision
                                } else {
                                    null
                                },
                            startedAtEpochMillis = now(),
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
        val rawPath = content.path ?: return blocks
        val path = resolvedPath(rawPath) ?: rawPath
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
                // Identical updates preserve decisions; changed content needs review again.
                decision =
                    if (existing != null && existing.absolutePath == path && existing.newText == content.newText &&
                        existing.oldText == content.oldText
                    ) {
                        existing.decision
                    } else {
                        DiffDecision.PROPOSED
                    },
                absolutePath = path,
                oldText = content.oldText,
                newText = content.newText,
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
        if (_state.value.isStreaming) {
            _state.update { it.copy(reviewNotice = REVIEW_BUSY_NOTICE) }
            return
        }
        val diff = allDiffs().firstOrNull { it.id == blockId && it.decision == DiffDecision.PROPOSED } ?: return
        val path = runCatching { pathGuard.resolve(diff.absolutePath) }.getOrNull()
        if (path == null) {
            reviewConflict(diff.absolutePath, "Cannot review this path outside the project.")
            return
        }
        if (accept) {
            fileReview.keep(path)
            finishFileReview(path, DiffDecision.ACCEPTED)
            return
        }
        if (!fileReview.hasPending(path)) {
            reviewConflict(
                path,
                "Revert unavailable: original file contents were not saved. Review this file manually.",
            )
            return
        }
        if (isEditorDirty(path)) {
            reviewConflict(path, "Revert blocked: this file has unsaved editor changes.")
            return
        }
        val current = currentSnapshot(path)
        if (current == null) {
            reviewConflict(path, "Revert blocked: the current file could not be read.")
            return
        }
        val target = fileReview.revertTarget(path, current)
        if (target == null) {
            reviewConflict(
                path,
                "Revert blocked: this file changed outside the pending agent edits. Review it manually.",
            )
            return
        }
        val result =
            if (target.existed) {
                fileSystem.writeText(
                    path,
                    target.content.orEmpty(),
                )
            } else {
                fileSystem.delete(path)
            }
        if (result.isFailure) {
            reviewConflict(path, "Revert failed: ${result.exceptionOrNull()?.message ?: "file operation failed"}")
            return
        }
        fileReview.reverted(path, target)
        checkpoints.noteDisk(path, target)
        invalidateCheckEvidence()
        finishFileReview(path, DiffDecision.REJECTED)
        onWorkspaceFilesChanged(listOf(path))
    }

    private fun reviewConflict(
        path: String,
        notice: String,
    ) {
        _state.update { it.copy(reviewVisible = true, reviewConflicts = it.reviewConflicts + (path to notice)) }
    }

    private fun finishFileReview(
        path: String,
        decision: DiffDecision,
    ) {
        mutateBlocks { block ->
            if (block is Block.Diff && block.decision == DiffDecision.PROPOSED &&
                resolvedPath(block.absolutePath) == path
            ) {
                block.copy(decision = decision)
            } else {
                block
            }
        }
        _state.update { it.copy(reviewConflicts = it.reviewConflicts - path, reviewNotice = null) }
        publishReviewCounts()
        persistReviewDecision()
    }

    private fun pendingReviewRecords(): List<AgentPendingReview> =
        reviewFiles(_state.value.turns)
            .filter { it.pendingBlockIds.isNotEmpty() }
            .map { AgentPendingReview(it.key, it.fileName, it.pathLabel) }

    private fun restoredReviewTurns(record: AgentSessionRecord): List<Turn.Agent> {
        if (record.pendingReviews.isEmpty()) return emptyList()
        return listOf(
            Turn.Agent(
                id = "restored-review-${record.id}",
                timeLabel = "",
                blocks =
                    record.pendingReviews.mapIndexed { index, file ->
                        Block.Diff(
                            id = "restored-review-${record.id}:diff:$index",
                            fileName = file.fileName,
                            path = file.parent,
                            absolutePath = file.path,
                            added = 0,
                            removed = 0,
                            lines = emptyList(),
                            decision = DiffDecision.PROPOSED,
                            previewAvailable = false,
                            oldText = null,
                        )
                    },
            ),
        )
    }

    /** Review decisions also persist for transcript-only sessions, without rewriting their messages. */
    private fun persistReviewDecision() {
        if (_state.value.restoration !is SessionRestoration.TranscriptOnly) {
            persistConversation()
            return
        }
        val id = recordId ?: return
        val pending = pendingReviewRecords()
        val summary = summarizeSession(_state.value.turns, false, _state.value.error, false, _state.value.interrupted)
        scope.launch {
            historyMutex.withLock {
                val saved = historyStore.list().firstOrNull { it.id == id } ?: return@withLock
                historyStore.save(saved.copy(pendingReviews = pending, status = summary.status))
            }
        }
    }

    private fun publishReviewCounts() {
        val counts =
            fileReview
                .netContents()
                .mapNotNull { (path, contents) ->
                    su.kidoz.jetaprog.app.ui.agent
                        .netReviewDelta(contents.first, contents.second)
                        ?.let { path to it }
                }.toMap()
        _state.update { it.copy(reviewCounts = counts) }
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
                    invalidateCheckEvidence()
                    checkpoints.noteDisk(path, snapshot)
                    fileReview.drop(path)
                    publishReviewCounts()
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
        if (restoredPaths.isNotEmpty()) {
            invalidateCheckEvidence()
            onWorkspaceFilesChanged(restoredPaths)
            persistReviewDecision()
        }
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
        workspaceReviewEpoch++
        workspaceRequest++
        workspaceBaselineAttempted = false
        _state.update { it.copy(workspaceReview = WorkspaceReviewState(), reviewScope = ReviewScope.AGENT) }
        fileReview.clear()
        _state.update { it.copy(reviewCounts = emptyMap(), reviewConflicts = emptyMap()) }
        checkpoints.clear()
    }

    private suspend fun decideAll(accept: Boolean) {
        // Newest first so repeated edits to one file pop snapshots in order.
        allDiffs()
            .filter { it.decision == DiffDecision.PROPOSED }
            .asReversed()
            .distinctBy { resolvedPath(it.absolutePath) ?: it.absolutePath }
            .forEach { decideDiff(it.id, accept) }
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
            scheduleRecoverySave()
            if (update is SessionUpdate.ToolCallStart || update is SessionUpdate.ToolCallProgress) {
                steerCancelIfIdle()
            }
        }
    }

    override suspend fun readTextFile(request: ReadTextFileRequest): ReadTextFileResponse {
        requireActiveTurn(request.sessionId)
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
        requireActiveTurn(request.sessionId)
        val path = guardedPath(request.path)
        if (isEditorDirty(path)) {
            throw AcpRequestException(
                code = AcpError.INVALID_REQUEST,
                message = "${File(
                    path,
                ).name} has unsaved editor changes. Save or revert them before the agent writes it.",
            )
        }
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
            throw error
        }
        invalidateCheckEvidence()
        fileReview.record(path, before, FileSnapshot(existed = true, content = request.content))
        publishReviewCounts()
        checkpoints.noteWrite(path, before, request.content)
        publishCheckpointFlags()
        scheduleRecoverySave()
        onWorkspaceFilesChanged(listOf(path))
    }

    private fun requireActiveTurn(requestSessionId: String) {
        if (requestSessionId != sessionId || !_state.value.isStreaming) {
            throw AcpRequestException(
                code = AcpError.INVALID_REQUEST,
                message = "The agent session or turn is no longer active",
            )
        }
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
        requireActiveTurn(request.sessionId)
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

    /**
     * Captures an interrupted in-flight conversation and awaits its final history write.
     * Stops session writers before saving so an older queued snapshot cannot replace it.
     * Project close must use this suspending path; [dispose] is emergency cleanup only.
     */
    public suspend fun shutdown() {
        navigationLoaded.await()
        rememberDraft()
        val navigation = _state.value.taskNavigation
        val record = conversationRecord()
        val openClient = client
        client = null
        dispose()
        withContext(NonCancellable) {
            try {
                if (record != null) historyMutex.withLock { historyStore.save(record) }
                navigationMutex.withLock { navigationStore.save(navigation) }
            } finally {
                runCatching { openClient?.close() }
            }
        }
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
        prefsStore.save(
            AgentPrefs(
                permissions = st.permissions,
                modelId = modelIds[chosenAgentId ?: CLAUDE_AGENT_ID] ?: legacyModelId,
                docked = st.docked,
                agentId = chosenAgentId,
                modelIds = modelIds.toMap(),
            ),
        )
    }

    /**
     * Selects [modelId] for the active session and remembers it for this agent.
     * The ACP call is authoritative; state changes only after the agent accepts it.
     * An id the agent did not advertise is ignored.
     */
    private suspend fun setModel(modelId: String) {
        val option = _state.value.availableModels.firstOrNull { it.id == modelId } ?: return
        val acpClient = client
        val activeSession = sessionId
        if (acpClient == null || activeSession == null || !acpClient.isOpen) {
            modelIds[activeLaunch.id] = modelId
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
        modelIds[activeLaunch.id] = modelId
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
        val preferred = modelIds[activeLaunch.id] ?: return
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
        private const val RECOVERY_SAVE_DELAY_MS = 1_000L
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
         * Fallback launch used when Claude's binary is not installed.
         * Discovery replaces it with the installed `claude` and the current ACP adapter.
         */
        const val DEFAULT_CLAUDE_CODE_COMMAND: String = "npx -y @agentclientprotocol/claude-agent-acp"

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
            val oldLines = oldText?.takeIf { it.isNotEmpty() }?.split("\n").orEmpty()
            val newLines = newText.takeIf { it.isNotEmpty() }?.split("\n").orEmpty()
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
            val removed = oldLines.subList(prefix, oldLines.size - suffix)
            val added = newLines.subList(prefix, newLines.size - suffix)
            val before = oldLines.subList(maxOf(0, prefix - CONTEXT_LINES), prefix)

            val lines = mutableListOf<DiffLine>()
            var oldNo = prefix - before.size + 1
            var newNo = prefix - before.size + 1
            before.forEach {
                lines += DiffLine(newNo, ' ', it, DiffLineKind.CONTEXT)
                oldNo++
                newNo++
            }
            removed.take(MAX_DIFF_BODY_LINES).forEach { lines += DiffLine(oldNo++, '-', it, DiffLineKind.REMOVED) }
            added.take(MAX_DIFF_BODY_LINES).forEach { lines += DiffLine(newNo++, '+', it, DiffLineKind.ADDED) }
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
