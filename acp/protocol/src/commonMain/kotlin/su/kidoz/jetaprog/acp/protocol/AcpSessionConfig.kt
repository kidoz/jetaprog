package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** Agent-provided session setting. Unknown types and categories remain decodable. */
@Serializable
public data class SessionConfigOption(
    /** Stable setting identifier. */
    val id: String,
    /** Display label supplied by the agent. */
    val name: String,
    /** Control type; select and boolean are supported. */
    val type: String,
    /** Current agent-owned value, including values of unsupported option types. */
    val currentValue: JsonElement = JsonNull,
    /** Optional explanatory text. */
    val description: String? = null,
    /** Optional semantic category, independent of the setting identifier. */
    val category: String? = null,
    /** Ordered flat choices or groups of choices. */
    val options: JsonElement = JsonNull,
)

/** A select choice or a group of choices, distinguished by value or group. */
@Serializable
public data class SessionConfigChoice(
    /** Agent-provided label. */
    val name: String,
    /** Selectable value; absent on groups. */
    val value: String? = null,
    /** Optional explanatory text. */
    val description: String? = null,
    /** Group identifier; absent on individual choices. */
    val group: String? = null,
    /** Choices belonging to a group. */
    val options: List<SessionConfigChoice> = emptyList(),
)

/** Changes one advertised select setting in an existing session. */
@Serializable
public data class SetSessionConfigRequest(
    /** Target session. */
    val sessionId: String,
    /** Advertised setting identifier. */
    val configId: String,
    /** Advertised select value. */
    val value: String,
)

/** Complete authoritative configuration after a successful change. */
@Serializable
public data class SetSessionConfigResponse(
    /** Replaces the previous list, including dependent settings and removed options. */
    val configOptions: List<SessionConfigOption>,
)

/** Changes one advertised boolean setting with an explicit wire discriminator. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class SetSessionBooleanConfigRequest(
    /** Target session. */
    val sessionId: String,
    /** Advertised setting identifier. */
    val configId: String,
    /** Boolean value, never a string representation. */
    val value: Boolean,
) {
    /** Required discriminator, including when default-value encoding is disabled. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    public val type: String = "boolean"
}
