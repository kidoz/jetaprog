package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Agent request for structured input; modes must be explicitly negotiated. */
@Serializable
public data class CreateElicitationRequest(
    val mode: String,
    val message: String,
    val requestedSchema: JsonObject? = null,
    val sessionId: String? = null,
    val toolCallId: String? = null,
    val requestId: JsonElement? = null,
)

/** User decision; content is included only for an accepted form. */
@Serializable
public data class CreateElicitationResponse(
    val action: String,
    val content: JsonObject? = null,
)

/** Explicitly supported elicitation modes; omitted modes are unsupported. */
@Serializable
public data class ElicitationCapabilities(
    val form: SessionFeatureCapability? = null,
)
