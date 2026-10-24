package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.Immutable
import su.kidoz.jetaprog.app.agent.AgentGoal
import su.kidoz.jetaprog.app.agent.AgentNavigationState
import su.kidoz.jetaprog.app.agent.AgentSessionRecord

/** Lifecycle of the connection to the backing ACP agent. */
public enum class AgentConnection {
    /** No agent process is running. */
    DISCONNECTED,

    /** A connection attempt is in progress. */
    CONNECTING,

    /** A session is established and ready for prompts. */
    CONNECTED,

    /** The last connection attempt failed. */
    ERROR,
}

/** Whether a saved conversation's agent context has been restored. */
public sealed interface SessionRestoration {
    /** Connecting to the agent or loading its saved context. */
    public data object Loading : SessionRestoration

    /** The original agent session is ready to continue. */
    public data object Resumed : SessionRestoration

    /** Only the saved transcript is available; the next prompt starts a separate chat. */
    public data class TranscriptOnly(
        /** Why the agent context could not be restored. */
        val reason: String,
    ) : SessionRestoration
}

/** Visual status of a tool call card. */
public enum class ToolStatus {
    /** The tool call is still running. */
    RUNNING,

    /** The tool call finished successfully. */
    OK,

    /** The tool call failed. */
    FAILED,

    /** The tool call was interrupted before it finished. */
    INTERRUPTED,
}

/** The user's decision on a proposed diff. */
public enum class DiffDecision {
    /** Awaiting the user's accept/reject. */
    PROPOSED,

    /** The user accepted the change. */
    ACCEPTED,

    /** The user rejected (and reverted) the change. */
    REJECTED,
}

/** A category of action the agent may perform, gated by a [PermissionPolicy]. */
public enum class PermissionKind {
    /** Reading files. */
    READ,

    /** Editing or creating files. */
    EDIT,

    /** Running shell commands. */
    RUN,
}

/** How a [PermissionKind] is authorized. */
public enum class PermissionPolicy {
    /** Allow automatically without prompting. */
    AUTO,

    /** Surface an approval card and wait for the user. */
    ASK,

    /** Always refuse. */
    NEVER,
}

/**
 * A selectable model reported by the connected agent.
 *
 * @property id Stable identifier passed to `session/set_model`.
 * @property displayName Full name shown in the picker.
 * @property shortName Compact name shown in the composer / docked header.
 */
@Immutable
public data class ModelOption(
    val id: String,
    val displayName: String,
    val shortName: String,
)

/**
 * A session mode reported by the connected agent, such as Ask or Code.
 *
 * @property id Stable identifier passed to `session/set_mode`.
 * @property name Human-readable name shown on the composer chip.
 */
@Immutable
public data class ModeOption(
    val id: String,
    val name: String,
)

/**
 * An agent the composer can launch.
 *
 * @property id Stable id stored with the chat and the project preference.
 * @property name Label shown on the agent chip.
 */
@Immutable
public data class AgentOption(
    val id: String,
    val name: String,
)

/**
 * One agent in the picker menu.
 *
 * Ready rows are [selectable]. A row with [installCommand] copies that command. The other rows
 * show why the scan could not launch the agent.
 *
 * @property id Stable id stored with the chat.
 * @property name Label shown on the row.
 * @property detail Discovered path, command, or the reason this agent cannot launch.
 * @property selectable When true, choosing the row switches to this agent.
 * @property installCommand Command copied by Install. Null when the row has no install action.
 * @property warning When true, [detail] uses the warning color.
 */
@Immutable
public data class AgentMenuRow(
    val id: String,
    val name: String,
    val detail: String,
    val selectable: Boolean,
    val installCommand: String? = null,
    val warning: Boolean = false,
)

/** Footer under the agent menu. The scan checks the IDE PATH, then Homebrew and `/usr/local/bin`. */
internal const val AGENT_SCAN_FOOTER: String = "Scanned PATH, Homebrew, and /usr/local/bin."

/**
 * Ready rows first, then rows that cannot launch.
 *
 * Order inside each group stays as given.
 */
internal fun splitAgentMenu(rows: List<AgentMenuRow>): Pair<List<AgentMenuRow>, List<AgentMenuRow>> {
    val ready = mutableListOf<AgentMenuRow>()
    val blocked = mutableListOf<AgentMenuRow>()
    for (row in rows) {
        if (row.selectable) ready += row else blocked += row
    }
    return ready to blocked
}

/**
 * One step of the agent's current plan.
 *
 * A later plan update replaces the whole list. The step is not editable.
 *
 * @property content The human-readable step.
 * @property status The agent's progress on this step.
 */
@Immutable
public data class PlanStep(
    val content: String,
    val status: PlanStepStatus,
)

/** Progress of one [PlanStep], as reported by the agent. */
public enum class PlanStepStatus {
    /** The step has not started. */
    PENDING,

    /** The agent is working on this step. */
    IN_PROGRESS,

    /** The agent marked the step finished. */
    COMPLETED,

    /** The agent reported a status this surface does not recognize. */
    UNKNOWN,
}

/**
 * A slash command the connected agent is currently advertising.
 *
 * @property name Command name without the leading slash.
 * @property description Optional explanation shown in the slash menu.
 */
@Immutable
public data class AgentCommand(
    val name: String,
    val description: String?,
)

/**
 * Returns the slash-command name being typed, or null when the composer is not
 * choosing a command.
 *
 * The menu stays open only while the trimmed input is a single `/name` token
 * with no arguments yet. `/` yields an empty query, which matches every command.
 */
internal fun slashCommandQuery(input: String): String? {
    val trimmed = input.trimStart()
    if (!trimmed.startsWith("/")) return null
    val token = trimmed.substringBefore(' ').substringBefore('\n')
    if (trimmed.length > token.length) return null
    return token.removePrefix("/")
}

/**
 * A file or selection attached to the composer draft.
 *
 * @property label Chip text, usually the file name.
 * @property path Workspace path included in the prompt the agent receives.
 * @property excerpt Selected text, or null when the attachment is the whole file.
 */
@Immutable
public data class ComposerAttachment(
    val label: String,
    val path: String,
    val excerpt: String? = null,
)

/** Chip text for [attachment], distinguishing a selection from the whole file. */
internal fun chipLabel(attachment: ComposerAttachment): String =
    if (attachment.excerpt == null) attachment.label else "${attachment.label} (selection)"

/**
 * One project file that matches the `@` token being typed.
 *
 * @property label File name shown in the mention menu.
 * @property path Path passed to [AgentIntent.AttachMention].
 */
@Immutable
public data class MentionMatch(
    val label: String,
    val path: String,
)

/**
 * The editor file and selection available for attachment.
 *
 * @property label File name shown on the chip.
 * @property path Workspace-relative path, or the absolute path when the file is outside the project.
 * @property selection The selected text, capped, or null when the caret has no selection.
 */
@Immutable
public data class EditorSnippet(
    val label: String,
    val path: String,
    val selection: String? = null,
)

/**
 * One follow-up waiting to be sent after the running turn.
 *
 * @property prompt The trimmed follow-up text.
 * @property attachments Context captured when the follow-up was queued.
 * @property steer Whether the running turn should be cancelled at the next tool boundary.
 * @property restoreComposerOnFailure Whether an unsent submission should restore the cleared draft.
 */
@Immutable
public data class QueuedFollowUp(
    val prompt: String,
    val attachments: List<ComposerAttachment>,
    val steer: Boolean = false,
    /** Stable identity for queue actions, independent of the prompt text or position. */
    val id: String,
    val restoreComposerOnFailure: Boolean = false,
)

/**
 * Returns the `@` query being typed, or null when the composer is not mentioning a file.
 *
 * The query is the last whitespace-delimited token when it starts with `@`.
 * A bare `@` yields an empty query. A trailing space closes the menu.
 */
internal fun mentionQuery(input: String): String? {
    if (input.isEmpty() || input.last().isWhitespace()) return null
    val start = input.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1
    val token = input.substring(start)
    if (!token.startsWith("@")) return null
    return token.removePrefix("@")
}

/**
 * Removes the `@` token being typed so a chosen mention does not stay in the prompt.
 *
 * Text before that token is kept. When no mention is open, [input] is returned unchanged.
 */
internal fun withoutMentionToken(input: String): String {
    if (mentionQuery(input) == null) return input
    val start = input.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1
    return input.substring(0, start).trimEnd(' ', '\t')
}

/**
 * Builds the ACP prompt, prepending attached files and selections so the agent can see them.
 *
 * Chip labels stay on the user turn. This text is what `session/prompt` receives.
 */
internal fun promptWithContext(
    prompt: String,
    attachments: List<ComposerAttachment>,
): String {
    if (attachments.isEmpty()) return prompt
    val preamble =
        buildString {
            append("Context:\n")
            attachments.forEach { attachment ->
                val excerpt = attachment.excerpt
                if (excerpt == null) {
                    append("File: ")
                    append(attachment.path)
                    append("\n\n")
                } else {
                    append("Selection in ")
                    append(attachment.path)
                    append(":\n")
                    append(excerpt)
                    append("\n\n")
                }
            }
        }
    return preamble + prompt
}

/** The kind of a single rendered diff line. */
public enum class DiffLineKind {
    /** Unchanged context line. */
    CONTEXT,

    /** Added line. */
    ADDED,

    /** Removed line. */
    REMOVED,
}

/**
 * One rendered line of an inline diff.
 *
 * @property number The line number to show in the gutter, or null for none.
 * @property sign The leading sign character (`+`, `-` or a space).
 * @property text The line text.
 * @property kind Whether the line was added, removed or is context.
 */
@Immutable
public data class DiffLine(
    val number: Int?,
    val sign: Char,
    val text: String,
    val kind: DiffLineKind,
)

/** A typed block within an agent turn. */
public sealed interface Block {
    /** A unique identifier, stable across streaming updates. */
    public val id: String

    /**
     * Streamed agent text (or internal reasoning when [thought] is true).
     *
     * @property text The accumulated text.
     * @property thought Whether this is internal reasoning rather than a reply.
     * @property streaming Whether more text is still arriving for this block.
     */
    @Immutable
    public data class Text(
        override val id: String,
        val text: String,
        val thought: Boolean = false,
        val streaming: Boolean = false,
    ) : Block

    /**
     * A tool call surfaced during the turn.
     *
     * @property name Human-readable title.
     * @property kind The ACP tool kind wire value (drives the icon).
     * @property args A short one-line summary of the arguments.
     * @property status Current visual status.
     * @property result The result body shown when expanded, if any.
     * @property expanded Whether the result body is shown.
     * @property command Full command when supplied as a structured input, for evidence inspection.
     * @property exitCode Structured process exit code reported by the adapter, when available.
     * @property checkRevision Workspace revision at command start; null for replayed or legacy tools.
     * @property startedAtEpochMillis Local time the command was first observed.
     */
    @Immutable
    public data class Tool(
        override val id: String,
        val name: String,
        val kind: String,
        val args: String,
        val status: ToolStatus,
        val result: String?,
        val expanded: Boolean,
        val command: String? = null,
        val exitCode: Int? = null,
        val checkRevision: Long? = null,
        val startedAtEpochMillis: Long? = null,
    ) : Block

    /**
     * A proposed file change the agent wants to (or did) make.
     *
     * @property fileName The file's display name.
     * @property path The file's parent path.
     * @property added Count of added lines.
     * @property removed Count of removed lines.
     * @property lines The rendered diff hunk.
     * @property decision The user's decision.
     * @property absolutePath The absolute path used to apply/revert the change.
     * @property newText The latest agent-supplied fragment, used to reopen review when an edit changes.
     * @property oldText The agent-supplied prior text for this hunk. Display only:
     *   reverts restore an IDE-side snapshot because ACP diffs are not whole files.
     */
    @Immutable
    public data class Diff(
        override val id: String,
        val fileName: String,
        val path: String,
        val added: Int,
        val removed: Int,
        val lines: List<DiffLine>,
        val decision: DiffDecision,
        val absolutePath: String,
        val oldText: String?,
        val newText: String? = null,
        /** False for restored review metadata with no saved diff contents. */
        val previewAvailable: Boolean = true,
    ) : Block

    /**
     * A permission gate awaiting the user's approval.
     *
     * @property title What the agent wants to do.
     * @property command The command or detail to show, if any.
     * @property resolved Whether the user has already decided.
     * @property choices The outcomes the user can pick. Each one keeps the option id to send back.
     */
    @Immutable
    public data class Approval(
        override val id: String,
        val title: String,
        val command: String?,
        val resolved: Boolean = false,
        val choices: List<ApprovalChoice> = emptyList(),
    ) : Block
}

/**
 * How an approval choice is drawn.
 */
public enum class ApprovalChoiceRole {
    /** Allow this single request. Drawn as the primary action. */
    ALLOW_ONCE,

    /** Allow this request and later ones of the same kind. */
    ALLOW_ALWAYS,

    /** Decline the request. */
    REJECT,
}

/**
 * One button on an approval card.
 *
 * @property optionId The ACP option id sent when the user picks this button.
 * @property label The button text.
 * @property role How the button is drawn.
 */
@Immutable
public data class ApprovalChoice(
    val optionId: String,
    val label: String,
    val role: ApprovalChoiceRole,
)

/** One conversation turn. */
public sealed interface Turn {
    /** A unique identifier. */
    public val id: String

    /**
     * A message from the user.
     *
     * @property text The message text.
     * @property timeLabel A relative time label.
     * @property context Names of any attached context files.
     * @property canRestore Whether this message has a file checkpoint that can be restored.
     * @property restoreNotice What the last restore of this message changed or skipped.
     */
    @Immutable
    public data class User(
        override val id: String,
        val text: String,
        val timeLabel: String,
        val context: List<String> = emptyList(),
        val canRestore: Boolean = false,
        val restoreNotice: String? = null,
    ) : Turn

    /**
     * A turn produced by the agent.
     *
     * @property timeLabel A relative time label.
     * @property blocks The typed content blocks, in order.
     */
    @Immutable
    public data class Agent(
        override val id: String,
        val timeLabel: String,
        val blocks: List<Block>,
    ) : Turn
}

/** State of a file the agent touched in the current session. */
public enum class FileChangeState {
    /** The change was accepted. */
    ACCEPTED,

    /** The file is being edited right now. */
    EDITING,

    /** The change is awaiting a decision. */
    PENDING,

    /** The change was rejected and the file was restored. */
    REJECTED,

    /** All changes were reviewed, with some kept and some reverted. */
    REVIEWED,
}

/**
 * A file the agent changed this session, shown in the perspective rail.
 *
 * @property name The file's display name.
 * @property added Count of added lines.
 * @property removed Count of removed lines.
 * @property state The change state.
 */
@Immutable
public data class SessionFileChange(
    val name: String,
    val added: Int?,
    val removed: Int?,
    val state: FileChangeState,
)

/**
 * Live presence shown while the agent is acting on the workspace.
 *
 * @property action A short verb, e.g. "Editing" or "Running".
 * @property fileName The file currently being touched, if any.
 * @property startedAtEpochMillis When the turn started, for the elapsed timer.
 */
@Immutable
public data class Presence(
    val action: String,
    val fileName: String?,
    val startedAtEpochMillis: Long,
)

/**
 * Immutable state of the agent surface.
 *
 * @property hasUnsavedEdits Whether this checkout has editor changes absent from the command input on disk.
 * @property workspacePath Checkout whose command evidence is displayed.
 * @property workspaceRevision Monotonic revision used to invalidate command evidence after edits.
 * @property connection The agent connection lifecycle.
 * @property agentName The connected agent's reported name, if any.
 * @property turns The conversation, oldest first.
 * @property isStreaming Whether a turn is currently in flight.
 * @property presence Live presence while acting, or null.
 * @property permissions Per-kind authorization policies.
 * @property availableModels Models reported by the connected agent, if any.
 * @property model The model currently active in the agent's session, if known.
 * @property availableModes Modes the agent can switch to, if it reported any.
 * @property mode The mode currently active in the agent's session, if known.
 * @property plan The agent's current plan. A new plan replaces this list.
 * @property commands Slash commands the agent is currently advertising.
 * @property docked Whether the surface is docked beside the editor (vs full perspective).
 * @property error The most recent error message, if any.
 * @property composerInput The current text in the composer input field.
 * @property composerAttachments Files and selections chips above the composer.
 * @property queuedPrompts Follow-ups in delivery order, each retaining its attached context.
 * @property mentionFiles Project files matching the `@` token being typed.
 * @property restoration The outcome or progress of restoring a saved conversation.
 * @property activeRecordId Id of the conversation open in this surface, when one has been claimed.
 *   The session list uses it to overlay live status and diff counts.
 * @property goal The one objective for this session, or null when none is set.
 * @property goalNotice Why the latest goal command was refused or the loop paused, if any.
 * @property reviewVisible Whether the read-only review of this session's diffs is open.
 * @property reviewNotice Why `/review` or a review action could not be applied, if any.
 * @property worktreeNotice Why `/worktree` could not open a second session, if any.
 * @property availableAgents Agents whose launch command exists on this machine.
 * @property agentMenu Every catalog agent, with the path or the reason it cannot launch.
 * @property agent The agent this chat is using, when one has been chosen.
 * @property agentNotice Why the latest agent change was refused, or why a saved agent is missing.
 */
@Immutable
public data class AgentUiState(
    val workspaceRevision: Long = 0,
    val workspacePath: String = "",
    val hasUnsavedEdits: Boolean = false,
    val connection: AgentConnection = AgentConnection.DISCONNECTED,
    val agentName: String? = null,
    val turns: List<Turn> = emptyList(),
    val isStreaming: Boolean = false,
    /** The latest turn stopped before normal completion. Cleared when a new turn starts. */
    val interrupted: Boolean = false,
    val presence: Presence? = null,
    val permissions: Map<PermissionKind, PermissionPolicy> =
        mapOf(
            PermissionKind.READ to PermissionPolicy.AUTO,
            PermissionKind.EDIT to PermissionPolicy.ASK,
            PermissionKind.RUN to PermissionPolicy.ASK,
        ),
    val availableModels: List<ModelOption> = emptyList(),
    val model: ModelOption? = null,
    val availableModes: List<ModeOption> = emptyList(),
    val mode: ModeOption? = null,
    val plan: List<PlanStep> = emptyList(),
    val commands: List<AgentCommand> = emptyList(),
    val docked: Boolean = false,
    val error: String? = null,
    val composerInput: String = "",
    val composerAttachments: List<ComposerAttachment> = emptyList(),
    val queuedPrompts: List<QueuedFollowUp> = emptyList(),
    /** Delivery is paused after Stop, failure, or an explicit pause. */
    val queuePaused: Boolean = false,
    val mentionFiles: List<MentionMatch> = emptyList(),
    val restoration: SessionRestoration? = null,
    /** Persisted conversations, most recent first (loaded on History). */
    val sessionHistory: List<AgentSessionRecord> = emptyList(),
    /** Whether the History popup is shown. */
    val historyVisible: Boolean = false,
    /** Per-checkout task organization and unsent drafts, independent of transcript saves. */
    val taskNavigation: AgentNavigationState = AgentNavigationState(),
    /** Id of the open conversation, when one has been claimed. */
    val activeRecordId: String? = null,
    /** The session objective. A saved active goal is paused when the chat is reopened. */
    val goal: AgentGoal? = null,
    /** A short explanation for a refused goal command or a paused loop. */
    val goalNotice: String? = null,
    /** Whether `/review` is showing this session's files. */
    val reviewVisible: Boolean = false,
    /** Which review source is currently selected. */
    val reviewScope: ReviewScope = ReviewScope.AGENT,
    /** Read-only Git review of the active checkout. */
    val workspaceReview: WorkspaceReviewState = WorkspaceReviewState(),
    /** Exact net counts for files with complete IDE-observed baselines. */
    val reviewCounts: Map<String, ReviewDelta> = emptyMap(),
    /** File-specific reasons a revert could not safely proceed. */
    val reviewConflicts: Map<String, String> = emptyMap(),
    /** A short explanation for a refused review command or action. */
    val reviewNotice: String? = null,
    /** A short explanation for a refused worktree command. */
    val worktreeNotice: String? = null,
    /** Agents whose launch command exists on this machine. */
    val availableAgents: List<AgentOption> = emptyList(),
    /** Every catalog agent, ready rows and rows that cannot launch. */
    val agentMenu: List<AgentMenuRow> = emptyList(),
    /** The agent launching this chat. */
    val agent: AgentOption? = null,
    /** A short explanation for a refused agent change, or a missing saved agent. */
    val agentNotice: String? = null,
) {
    /** Files the agent changed in this session, for the session rail. */
    val sessionChanges: List<SessionFileChange>
        get() =
            reviewFiles(turns, reviewCounts).map { file ->
                SessionFileChange(
                    name = file.fileName,
                    added = file.added,
                    removed = file.removed,
                    state =
                        when (file.statusLabel) {
                            "Kept" -> FileChangeState.ACCEPTED
                            "Reviewed" -> FileChangeState.REVIEWED
                            "Reverted" -> FileChangeState.REJECTED
                            else -> FileChangeState.PENDING
                        },
                )
            }
}

/** User intents for the agent surface. */
public sealed interface AgentIntent {
    /**
     * Send a prompt, optionally with attached context file names.
     *
     * While a turn is streaming or a queue exists, append a follow-up with its current context.
     * Active queues deliver in order. Stop pauses delivery and retains all unsent items.
     *
     * @property prompt The user's message.
     * @property context Names of attached context files, used when the composer has no chips.
     */
    public data class Send(
        val prompt: String,
        val context: List<String> = emptyList(),
    ) : AgentIntent

    /**
     * Send [prompt] now, or interrupt the running turn so it is delivered at the next tool boundary.
     *
     * When a tool call is running, the turn is cancelled once that call finishes. When none is
     * running, the turn is cancelled immediately. The conversation stays. Stop leaves the
     * follow-up unsent. Steering puts this message first and retains the previous queue.
     */
    public data class Steer(
        val prompt: String,
    ) : AgentIntent

    /** Ensure the agent is connected (no-op if already connected/connecting). */
    public data object EnsureConnected : AgentIntent

    /** Cancel the in-flight turn. */
    public data object Stop : AgentIntent

    /** Start a fresh conversation, archiving the current one. */
    public data object NewChat : AgentIntent

    /** Open the session History popup (loads persisted conversations). */
    public data object ShowHistory : AgentIntent

    /** Close the session History popup. */
    public data object HideHistory : AgentIntent

    /** Pins or unpins a saved task without changing its conversation. */
    public data class SetSessionPinned(
        val recordId: String,
        val pinned: Boolean,
    ) : AgentIntent

    /** Moves a task between Recent and Archived without stopping or deleting it. */
    public data class SetSessionArchived(
        val recordId: String,
        val archived: Boolean,
    ) : AgentIntent

    /** Restore the persisted conversation with [recordId] into the surface. */
    public data class RestoreSession(
        val recordId: String,
    ) : AgentIntent

    /** Delete the persisted conversation with [recordId]. */
    public data class DeleteSession(
        val recordId: String,
    ) : AgentIntent

    /** Toggle the expanded state of a tool-call card. */
    public data class ToggleToolCall(
        val blockId: String,
    ) : AgentIntent

    /** Keep all pending changes in the file identified by this diff block. */
    public data class AcceptDiff(
        val blockId: String,
    ) : AgentIntent

    /** Revert all pending changes in the file identified by this diff block, when safe. */
    public data class RejectDiff(
        val blockId: String,
    ) : AgentIntent

    /**
     * Open the diff with [blockId] in the IDE's version-control diff viewer.
     *
     * The inline card stays a short summary. The viewer shows the working tree against HEAD.
     */
    public data class OpenDiff(
        val blockId: String,
    ) : AgentIntent

    /**
     * Roll project files back to their state at the user message [turnId].
     *
     * The transcript stays. Files edited by hand after the agent wrote them, files
     * whose contents cannot be read, and files a command changed are left in place.
     * The turn's restore notice names what was restored and what was skipped.
     */
    public data class RestoreCheckpoint(
        val turnId: String,
    ) : AgentIntent

    /** Keep all pending file changes in this session. */
    public data object AcceptAll : AgentIntent

    /** Revert all pending diffs in the current turn. */
    public data object RevertAll : AgentIntent

    /**
     * Pick one offered outcome for the approval card [callId].
     *
     * An option id the card did not offer completes the request as cancelled.
     *
     * @property callId The approval block id, matching the tool call id.
     * @property optionId The ACP option id of the chosen outcome.
     */
    public data class SelectApproval(
        val callId: String,
        val optionId: String,
    ) : AgentIntent

    /** Change a permission kind's policy. */
    public data class SetPermission(
        val kind: PermissionKind,
        val policy: PermissionPolicy,
    ) : AgentIntent

    /** Select the agent-reported model with [modelId]. */
    public data class SetModel(
        val modelId: String,
    ) : AgentIntent

    /**
     * Switch the active session mode to [modeId].
     *
     * The conversation is kept. The agent's permission policies are unchanged.
     */
    public data class SetMode(
        val modeId: String,
    ) : AgentIntent

    /**
     * Launch [agentId] for a new chat.
     *
     * An idle chat is archived first. The composer draft, permission policies, and dock stay.
     * A running turn keeps the current agent.
     */
    public data class SelectAgent(
        val agentId: String,
    ) : AgentIntent

    /** Look again for installed agents. The open chat stays on its current agent. */
    public data object RefreshAgents : AgentIntent

    /**
     * Record that Install copied its command, or that the copy failed.
     *
     * @property copied Whether the command reached the clipboard.
     */
    public data class ReportAgentInstallCopy(
        val copied: Boolean,
    ) : AgentIntent

    /** Expand the docked surface to the full perspective. */
    public data object ExpandToPerspective : AgentIntent

    /** Dock the perspective back to a tool window. */
    public data object DockToToolWindow : AgentIntent

    /** Update the composer input text. */
    public data class SetComposerInput(
        val text: String,
    ) : AgentIntent

    /** Attach the editor's current file. Does nothing when no file is open. */
    public data object AttachCurrentFile : AgentIntent

    /** Attach the editor's current selection. Does nothing when there is no selection. */
    public data object AttachCurrentSelection : AgentIntent

    /**
     * Attach the project file chosen from the `@` menu.
     *
     * @property path Workspace path included in the prompt.
     * @property label Chip text, usually the file name.
     */
    public data class AttachMention(
        val path: String,
        val label: String,
    ) : AgentIntent

    /**
     * Remove one composer chip.
     *
     * @property path Path of the attachment to remove.
     * @property selection True to remove the selection chip, false to remove the whole-file chip.
     */
    public data class RemoveAttachment(
        val path: String,
        val selection: Boolean,
    ) : AgentIntent

    /** Drop the queued follow-up so a later turn end does not send it. */
    public data class RemoveQueuedPrompt(
        val id: String,
    ) : AgentIntent

    /** Pauses automatic delivery without stopping the current turn. */
    public data object PauseQueue : AgentIntent

    /** Resumes queued delivery, starting immediately when no turn is running. */
    public data object ResumeQueue : AgentIntent

    /** Replaces queued text while retaining its identity and attached context. */
    public data class UpdateQueuedPrompt(
        val id: String,
        val prompt: String,
    ) : AgentIntent

    /** Moves a queued prompt by one position in the requested direction. */
    public data class MoveQueuedPrompt(
        val id: String,
        val earlier: Boolean,
    ) : AgentIntent

    /** Hold the session goal. A running turn is stopped so the loop does not continue. */
    public data object PauseGoal : AgentIntent

    /** Continue a paused goal with another work round. */
    public data object ResumeGoal : AgentIntent

    /** Remove the session goal. A running turn is stopped. */
    public data object ClearGoal : AgentIntent

    /** Put `/goal ` in the composer so the next send replaces the objective. */
    public data object ReplaceGoal : AgentIntent

    /** Hide the session review card. */
    public data object HideReview : AgentIntent

    /** Opens local file review without sending or clearing the composer. */
    public data object ShowReview : AgentIntent

    /** Switches between agent-reported edits and all local Git changes. */
    public data class SetReviewScope(
        val scope: ReviewScope,
    ) : AgentIntent

    /** Rescans the checkout without modifying files or the Git index. */
    public data object RefreshWorkspaceReview : AgentIntent

    /** Loads a read-only preview for a path in the latest workspace scan. */
    public data class PreviewWorkspaceChange(
        val path: String,
    ) : AgentIntent

    /**
     * Keep all pending changes of the reviewed file [key].
     *
     * Keep acknowledges the edits already on disk without writing to the file.
     */
    public data class AcceptReviewFile(
        val key: String,
    ) : AgentIntent

    /**
     * Revert all pending changes of the reviewed file [key].
     *
     * Restoration requires saved original contents and no subsequent external or unsaved edits.
     */
    public data class DismissReviewFile(
        val key: String,
    ) : AgentIntent
}
