package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A unit of message content, discriminated on the wire by the `type` field.
 *
 * Content blocks appear in prompts, agent responses, tool call payloads and
 * embedded resources.
 */
@Serializable
public sealed interface ContentBlock {
    /** Plain text content. */
    @Serializable
    @SerialName("text")
    public data class Text(
        /** The text value. */
        val text: String,
    ) : ContentBlock

    /** Base64-encoded image content. */
    @Serializable
    @SerialName("image")
    public data class Image(
        /** Base64-encoded image bytes. */
        val data: String,
        /** The image MIME type, e.g. `image/png`. */
        val mimeType: String,
        /** Optional source URI for the image. */
        val uri: String? = null,
    ) : ContentBlock

    /** Base64-encoded audio content. */
    @Serializable
    @SerialName("audio")
    public data class Audio(
        /** Base64-encoded audio bytes. */
        val data: String,
        /** The audio MIME type, e.g. `audio/wav`. */
        val mimeType: String,
    ) : ContentBlock

    /** A link to a resource, referenced by URI. */
    @Serializable
    @SerialName("resource_link")
    public data class ResourceLink(
        /** The resource URI. */
        val uri: String,
        /** A display name for the resource. */
        val name: String,
        /** Optional MIME type of the linked resource. */
        val mimeType: String? = null,
        /** Optional human-readable title. */
        val title: String? = null,
        /** Optional description. */
        val description: String? = null,
    ) : ContentBlock

    /** An embedded resource whose contents are inlined into the message. */
    @Serializable
    @SerialName("resource")
    public data class Resource(
        /** The embedded resource contents. */
        val resource: EmbeddedResource,
    ) : ContentBlock
}

/**
 * The contents of an embedded resource, either text or base64-encoded binary.
 */
@Serializable
public data class EmbeddedResource(
    /** The resource URI. */
    val uri: String,
    /** Text contents, when the resource is textual. */
    val text: String? = null,
    /** Base64-encoded contents, when the resource is binary. */
    val blob: String? = null,
    /** Optional MIME type. */
    val mimeType: String? = null,
)
