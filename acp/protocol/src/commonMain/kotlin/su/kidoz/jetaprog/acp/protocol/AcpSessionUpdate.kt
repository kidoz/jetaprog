package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Parameters of the `session/update` notification.
 */
@Serializable
public data class SessionNotification(
    /** The session this update belongs to. */
    val sessionId: String,
    /** The incremental update payload. */
    val update: SessionUpdate,
)

/**
 * An incremental update streamed from the agent during a turn.
 *
 * Discriminated on the wire by the `sessionUpdate` field.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("sessionUpdate")
public sealed interface SessionUpdate {
    /** A chunk of the agent's user-visible message. */
    @Serializable
    @SerialName("agent_message_chunk")
    public data class AgentMessageChunk(
        /** The message content chunk. */
        val content: ContentBlock,
    ) : SessionUpdate

    /** A chunk of the agent's internal reasoning. */
    @Serializable
    @SerialName("agent_thought_chunk")
    public data class AgentThoughtChunk(
        /** The thought content chunk. */
        val content: ContentBlock,
    ) : SessionUpdate

    /** A chunk echoing the user's message, e.g. when replaying history. */
    @Serializable
    @SerialName("user_message_chunk")
    public data class UserMessageChunk(
        /** The message content chunk. */
        val content: ContentBlock,
    ) : SessionUpdate

    /** Announces a new tool call. */
    @Serializable
    @SerialName("tool_call")
    public data class ToolCallStart(
        /** Unique identifier for the tool call. */
        val toolCallId: String,
        /** A short human-readable title. */
        val title: String,
        /** The category of the tool call. */
        val kind: ToolKind? = null,
        /** The current status. */
        val status: ToolCallStatus? = null,
        /** Output produced so far. */
        val content: List<ToolCallContent> = emptyList(),
        /** File locations the tool call touches. */
        val locations: List<ToolCallLocation> = emptyList(),
        /** The raw, tool-specific input. */
        val rawInput: JsonElement? = null,
        /** The raw, tool-specific output. */
        val rawOutput: JsonElement? = null,
    ) : SessionUpdate

    /** Updates the status or output of an existing tool call. */
    @Serializable
    @SerialName("tool_call_update")
    public data class ToolCallProgress(
        /** The tool call being updated. */
        val toolCallId: String,
        /** The updated status, if changed. */
        val status: ToolCallStatus? = null,
        /** The updated title, if changed. */
        val title: String? = null,
        /** The updated category, if changed. */
        val kind: ToolKind? = null,
        /** Additional or replacement output, or `null` when unchanged. */
        val content: List<ToolCallContent>? = null,
        /** Updated file locations, if changed. */
        val locations: List<ToolCallLocation> = emptyList(),
        /** The raw, tool-specific input. */
        val rawInput: JsonElement? = null,
        /** The raw, tool-specific output. */
        val rawOutput: JsonElement? = null,
    ) : SessionUpdate

    /** Communicates the agent's execution plan. */
    @Serializable
    @SerialName("plan")
    public data class Plan(
        /** The ordered plan entries. */
        val entries: List<PlanEntry> = emptyList(),
    ) : SessionUpdate

    /** Announces the slash commands currently available to the user. */
    @Serializable
    @SerialName("available_commands_update")
    public data class AvailableCommandsUpdate(
        /** The available commands. */
        val availableCommands: List<AvailableCommand> = emptyList(),
    ) : SessionUpdate

    /** Replaces all agent-provided session configuration options. */
    @Serializable
    @SerialName("config_option_update")
    public data class ConfigOptionUpdate(
        /** Complete current configuration, in display order. */
        val configOptions: List<SessionConfigOption>,
    ) : SessionUpdate

    /** Partial agent-owned session metadata. */
    @Serializable
    @SerialName("session_info_update")
    public data class SessionInfoUpdate(
        /** Omission retains the title; null clears it. */
        val title: AcpPatch<String> = AcpPatch.Absent,
        /** ISO 8601 activity time with the same patch semantics. */
        val updatedAt: AcpPatch<String> = AcpPatch.Absent,
    ) : SessionUpdate

    /** Agent-reported context usage and optional cumulative session cost. */
    @Serializable
    @SerialName("usage_update")
    public data class UsageUpdate(
        /** Total context size in tokens. */
        val size: Long,
        /** Tokens currently in context. */
        val used: Long,
        /** Optional cumulative cost. */
        val cost: SessionCost? = null,
    ) : SessionUpdate

    /** Live notice; severity never changes authorization or execution state. */
    @Serializable
    @SerialName("notice")
    public data class Notice(
        /** Plain-text title. */
        val title: String,
        /** Presentation hint, including future or implementation-specific values. */
        val severity: String,
        /** Optional plain-text details. */
        val description: String? = null,
    ) : SessionUpdate

    /** Upserts one context compaction by agent-owned ID. */
    @Serializable
    @SerialName("compaction_update")
    public data class CompactionUpdate(
        /** Stable compaction identity. */
        val compactionId: String,
        /** Agent-reported status; unknown values remain displayable. */
        val status: String,
        /** Complete replacement summary; omission retains it and null clears it. */
        val summary: AcpPatch<List<ContentBlock>> = AcpPatch.Absent,
        /** Error patch with the same semantics. */
        val error: AcpPatch<String> = AcpPatch.Absent,
    ) : SessionUpdate

    /** Appends summary content to a compaction, creating it if first seen. */
    @Serializable
    @SerialName("compaction_summary_chunk")
    public data class CompactionSummaryChunk(
        /** Stable compaction identity. */
        val compactionId: String,
        /** Content appended in wire order. */
        val content: ContentBlock,
    ) : SessionUpdate

    /** Announces a change to the active session mode. */
    @Serializable
    @SerialName("current_mode_update")
    public data class CurrentModeUpdate(
        /** The identifier of the newly active mode. */
        val currentModeId: String,
    ) : SessionUpdate
}

/**
 * Output content produced by a tool call.
 */
@Serializable
public data class ToolCallContent(
    /** The kind of content, e.g. `content` or `diff`. */
    val type: String,
    /** The content block, when [type] is `content`. */
    val content: ContentBlock? = null,
    /** The file path, when [type] is `diff`. */
    val path: String? = null,
    /** The prior text, when [type] is `diff`. */
    val oldText: String? = null,
    /** The new text, when [type] is `diff`. */
    val newText: String? = null,
    /** The terminal identifier, when [type] is `terminal`. */
    val terminalId: String? = null,
)

/**
 * A file location associated with a tool call.
 */
@Serializable
public data class ToolCallLocation(
    /** The absolute file path. */
    val path: String,
    /** An optional line number. */
    val line: Int? = null,
)

/**
 * A single entry in an agent execution [SessionUpdate.Plan].
 */
@Serializable
public data class PlanEntry(
    /** The human-readable plan step. */
    val content: String,
    /** The priority, e.g. `high`, `medium`, `low`. */
    val priority: String? = null,
    /** The status, e.g. `pending`, `in_progress`, `completed`. */
    val status: String? = null,
)

/**
 * A slash command the user can invoke within a session.
 */
@Serializable
public data class AvailableCommand(
    /** The command name, without the leading slash. */
    val name: String,
    /** A description shown to the user. */
    val description: String? = null,
)

/** Cumulative cost in an agent-specified currency or unit. */
@Serializable
public data class SessionCost(
    /** Total reported amount. */
    val amount: Double,
    /** Currency code or other unit such as credits. */
    val currency: String,
)
