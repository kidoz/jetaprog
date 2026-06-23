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
        /** Additional or replacement output. */
        val content: List<ToolCallContent> = emptyList(),
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
