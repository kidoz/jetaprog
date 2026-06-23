package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Parameters of the `session/request_permission` request, sent by the agent to
 * ask the client to authorize a tool call.
 */
@Serializable
public data class RequestPermissionRequest(
    /** The session the tool call belongs to. */
    val sessionId: String,
    /** A description of the tool call awaiting authorization. */
    val toolCall: PermissionToolCall,
    /** The options the client may choose between. */
    val options: List<PermissionOption>,
)

/**
 * A minimal description of the tool call a permission request refers to.
 */
@Serializable
public data class PermissionToolCall(
    /** The tool call identifier. */
    val toolCallId: String,
    /** A short human-readable title. */
    val title: String? = null,
    /** The category of the tool call. */
    val kind: ToolKind? = null,
    /** The raw, tool-specific input. */
    val rawInput: JsonElement? = null,
)

/**
 * A choice presented to the client for a permission request.
 */
@Serializable
public data class PermissionOption(
    /** Stable identifier returned in the outcome. */
    val optionId: String,
    /** Human-readable label. */
    val name: String,
    /** The semantic kind of the option. */
    val kind: PermissionOptionKind,
)

/**
 * Result of the `session/request_permission` request.
 */
@Serializable
public data class RequestPermissionResponse(
    /** The selected outcome. */
    val outcome: PermissionOutcome,
)

/**
 * The outcome of a permission request, discriminated by the `outcome` field.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("outcome")
public sealed interface PermissionOutcome {
    /** The user selected one of the offered options. */
    @Serializable
    @SerialName("selected")
    public data class Selected(
        /** The chosen [PermissionOption.optionId]. */
        val optionId: String,
    ) : PermissionOutcome

    /** The request was cancelled, e.g. because the turn ended. */
    @Serializable
    @SerialName("cancelled")
    public data object Cancelled : PermissionOutcome
}
