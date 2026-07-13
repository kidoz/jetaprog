package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.runtime.Immutable

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

/** Visual status of a tool call card. */
public enum class ToolStatus {
    /** The tool call is still running. */
    RUNNING,

    /** The tool call finished successfully. */
    OK,

    /** The tool call failed. */
    FAILED,
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

/** Reasoning-effort preference shown on the model chip. */
public enum class Effort(
    /** Short display label. */
    public val label: String,
) {
    /** Minimal reasoning. */
    LOW("Low"),

    /** Balanced reasoning. */
    MEDIUM("Medium"),

    /** Maximum reasoning. */
    HIGH("High"),
}

/**
 * A selectable model preference.
 *
 * @property id Stable identifier.
 * @property displayName Full name shown in the perspective header.
 * @property shortName Compact name shown in the composer / docked header.
 */
@Immutable
public data class ModelOption(
    val id: String,
    val displayName: String,
    val shortName: String,
)

/** The models offered in the model picker. */
public val AGENT_MODELS: List<ModelOption> =
    listOf(
        ModelOption("claude-opus-4-8", "Claude Opus 4.8", "Opus 4.8"),
        ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6", "Sonnet 4.6"),
        ModelOption("claude-haiku-4-5", "Claude Haiku 4.5", "Haiku 4.5"),
    )

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
     * @property oldText The prior file contents, used to revert.
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
    ) : Block

    /**
     * A permission gate awaiting the user's approval.
     *
     * @property title What the agent wants to do.
     * @property command The command or detail to show, if any.
     * @property resolved Whether the user has already decided.
     */
    @Immutable
    public data class Approval(
        override val id: String,
        val title: String,
        val command: String?,
        val resolved: Boolean = false,
    ) : Block
}

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
     */
    @Immutable
    public data class User(
        override val id: String,
        val text: String,
        val timeLabel: String,
        val context: List<String> = emptyList(),
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
    val added: Int,
    val removed: Int,
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
 * @property connection The agent connection lifecycle.
 * @property agentName The connected agent's reported name, if any.
 * @property turns The conversation, oldest first.
 * @property isStreaming Whether a turn is currently in flight.
 * @property presence Live presence while acting, or null.
 * @property permissions Per-kind authorization policies.
 * @property model The selected model preference.
 * @property effort The selected effort preference.
 * @property docked Whether the surface is docked beside the editor (vs full perspective).
 * @property error The most recent error message, if any.
 * @property composerInput The current text in the composer input field.
 */
@Immutable
public data class AgentUiState(
    val connection: AgentConnection = AgentConnection.DISCONNECTED,
    val agentName: String? = null,
    val turns: List<Turn> = emptyList(),
    val isStreaming: Boolean = false,
    val presence: Presence? = null,
    val permissions: Map<PermissionKind, PermissionPolicy> =
        mapOf(
            PermissionKind.READ to PermissionPolicy.AUTO,
            PermissionKind.EDIT to PermissionPolicy.ASK,
            PermissionKind.RUN to PermissionPolicy.ASK,
        ),
    val model: ModelOption = AGENT_MODELS.first(),
    val effort: Effort = Effort.HIGH,
    val docked: Boolean = false,
    val error: String? = null,
    val composerInput: String = "",
) {
    /** Files the agent changed in the latest agent turn, for the session rail. */
    val sessionChanges: List<SessionFileChange>
        get() =
            (turns.lastOrNull() as? Turn.Agent)
                ?.blocks
                ?.filterIsInstance<Block.Diff>()
                ?.map { diff ->
                    SessionFileChange(
                        name = diff.fileName,
                        added = diff.added,
                        removed = diff.removed,
                        state =
                            when (diff.decision) {
                                DiffDecision.ACCEPTED -> FileChangeState.ACCEPTED
                                DiffDecision.REJECTED -> FileChangeState.ACCEPTED
                                DiffDecision.PROPOSED -> FileChangeState.PENDING
                            },
                    )
                }.orEmpty()
}

/** User intents for the agent surface. */
public sealed interface AgentIntent {
    /**
     * Send a prompt, optionally with attached context file names.
     *
     * @property prompt The user's message.
     * @property context Names of attached context files.
     */
    public data class Send(
        val prompt: String,
        val context: List<String> = emptyList(),
    ) : AgentIntent

    /** Ensure the agent is connected (no-op if already connected/connecting). */
    public data object EnsureConnected : AgentIntent

    /** Cancel the in-flight turn. */
    public data object Stop : AgentIntent

    /** Start a fresh conversation, archiving the current one. */
    public data object NewChat : AgentIntent

    /** Toggle the expanded state of a tool-call card. */
    public data class ToggleToolCall(
        val blockId: String,
    ) : AgentIntent

    /** Accept the proposed diff with the given block id. */
    public data class AcceptDiff(
        val blockId: String,
    ) : AgentIntent

    /** Reject (and revert) the proposed diff with the given block id. */
    public data class RejectDiff(
        val blockId: String,
    ) : AgentIntent

    /** Accept all pending diffs in the current turn. */
    public data object AcceptAll : AgentIntent

    /** Revert all pending diffs in the current turn. */
    public data object RevertAll : AgentIntent

    /** Approve the gated tool call with the given call id. */
    public data class Approve(
        val callId: String,
    ) : AgentIntent

    /** Deny the gated tool call with the given call id. */
    public data class Deny(
        val callId: String,
    ) : AgentIntent

    /** Change a permission kind's policy. */
    public data class SetPermission(
        val kind: PermissionKind,
        val policy: PermissionPolicy,
    ) : AgentIntent

    /** Change the selected model and effort. */
    public data class SetModel(
        val model: ModelOption,
        val effort: Effort,
    ) : AgentIntent

    /** Expand the docked surface to the full perspective. */
    public data object ExpandToPerspective : AgentIntent

    /** Dock the perspective back to a tool window. */
    public data object DockToToolWindow : AgentIntent

    /** Update the composer input text. */
    public data class SetComposerInput(
        val text: String,
    ) : AgentIntent
}
