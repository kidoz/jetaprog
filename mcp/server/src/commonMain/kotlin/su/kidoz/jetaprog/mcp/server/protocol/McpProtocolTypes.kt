package su.kidoz.jetaprog.mcp.server.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * MCP Protocol version constant.
 */
public const val MCP_PROTOCOL_VERSION: String = "2025-06-18"

/**
 * Annotations for tool behavior hints.
 * These provide clients with hints about tool behavior to improve UX.
 */
@Serializable
public data class ToolAnnotations(
    /**
     * Human-readable title for display in UI.
     */
    val title: String? = null,
    /**
     * If true, the tool does not modify any external state (files, APIs, etc.).
     */
    val readOnlyHint: Boolean? = null,
    /**
     * If true, the tool may perform destructive updates (deletes, overwrites).
     */
    val destructiveHint: Boolean? = null,
    /**
     * If true, the tool interacts with the "real world" (emails, purchases, etc.).
     */
    val idempotentHint: Boolean? = null,
    /**
     * If true, the tool may take a long time to complete.
     */
    val openWorldHint: Boolean? = null,
)

/**
 * Annotations for resource behavior.
 */
@Serializable
public data class ResourceAnnotations(
    /**
     * Intended audience for the resource: "user", "assistant", or both.
     */
    val audience: List<String>? = null,
    /**
     * Priority hint for ordering resources (higher = more important).
     */
    val priority: Double? = null,
)

/**
 * Role in a prompt message.
 */
public enum class PromptRole {
    /** User role for prompt messages. */
    USER,

    /** Assistant role for prompt messages. */
    ASSISTANT,
    ;

    /**
     * Converts to protocol format (lowercase).
     */
    public fun toProtocolFormat(): String = name.lowercase()
}

/**
 * Content within a prompt message.
 */
public sealed interface PromptContent {
    /**
     * Text content in a prompt.
     */
    public data class Text(
        val text: String,
    ) : PromptContent

    /**
     * Image content in a prompt.
     */
    public data class Image(
        val data: String,
        val mimeType: String,
    ) : PromptContent

    /**
     * Embedded resource content in a prompt.
     */
    public data class Resource(
        val uri: String,
        val mimeType: String? = null,
        val text: String? = null,
        val blob: String? = null,
    ) : PromptContent
}

/**
 * A message in a prompt result.
 */
public data class PromptMessage(
    /**
     * Role of the message (user or assistant).
     */
    val role: PromptRole,
    /**
     * Content of the message.
     */
    val content: PromptContent,
)

/**
 * Result of generating a prompt.
 */
public data class PromptResult(
    /**
     * Description of the generated prompt.
     */
    val description: String? = null,
    /**
     * Messages that make up the prompt.
     */
    val messages: List<PromptMessage>,
)

/**
 * Creates a simple text prompt result with a single user message.
 *
 * @param text The text content of the prompt
 * @param description Optional description of the prompt
 * @return A PromptResult with a single user text message
 */
public fun textPromptResult(
    text: String,
    description: String? = null,
): PromptResult =
    PromptResult(
        description = description,
        messages =
            listOf(
                PromptMessage(
                    role = PromptRole.USER,
                    content = PromptContent.Text(text),
                ),
            ),
    )

// Capability data classes for the 2025-06-18 specification

/**
 * Server capability for tools.
 */
@Serializable
public data class ToolsCapability(
    /**
     * Whether the server supports tool list change notifications.
     */
    val listChanged: Boolean = true,
)

/**
 * Server capability for resources.
 */
@Serializable
public data class ResourcesCapability(
    /**
     * Whether the server supports resource subscriptions.
     */
    val subscribe: Boolean = true,
    /**
     * Whether the server supports resource list change notifications.
     */
    val listChanged: Boolean = true,
)

/**
 * Server capability for prompts.
 */
@Serializable
public data class PromptsCapability(
    /**
     * Whether the server supports prompt list change notifications.
     */
    val listChanged: Boolean = true,
)

/**
 * Server capability for sampling.
 */
@Serializable
public data class SamplingCapability(
    /**
     * Whether sampling is enabled.
     */
    val enabled: Boolean = true,
)

/**
 * Server information returned in initialize response.
 */
@Serializable
public data class ServerInfo(
    /**
     * Server name.
     */
    val name: String,
    /**
     * Server version.
     */
    val version: String,
    /**
     * Human-readable title for the server.
     */
    val title: String? = null,
)

/**
 * Converts PromptMessage to protocol format for JSON serialization.
 */
public fun PromptMessage.toProtocolFormat(): Map<String, Any?> =
    mapOf(
        "role" to role.toProtocolFormat(),
        "content" to content.toProtocolFormat(),
    )

/**
 * Converts PromptContent to protocol format for JSON serialization.
 */
public fun PromptContent.toProtocolFormat(): Map<String, Any?> =
    when (this) {
        is PromptContent.Text -> {
            mapOf(
                "type" to "text",
                "text" to text,
            )
        }

        is PromptContent.Image -> {
            mapOf(
                "type" to "image",
                "data" to data,
                "mimeType" to mimeType,
            )
        }

        is PromptContent.Resource -> {
            buildMap {
                put("type", "resource")
                put(
                    "resource",
                    buildMap {
                        put("uri", uri)
                        mimeType?.let { put("mimeType", it) }
                        text?.let { put("text", it) }
                        blob?.let { put("blob", it) }
                    },
                )
            }
        }
    }

/**
 * Converts PromptResult to protocol format for JSON serialization.
 */
public fun PromptResult.toProtocolFormat(): Map<String, Any?> =
    buildMap {
        description?.let { put("description", it) }
        put("messages", messages.map { it.toProtocolFormat() })
    }

/**
 * Converts ToolAnnotations to protocol format for JSON response.
 */
public fun ToolAnnotations.toProtocolFormat(): Map<String, Any?> =
    buildMap {
        title?.let { put("title", it) }
        readOnlyHint?.let { put("readOnlyHint", it) }
        destructiveHint?.let { put("destructiveHint", it) }
        idempotentHint?.let { put("idempotentHint", it) }
        openWorldHint?.let { put("openWorldHint", it) }
    }

/**
 * Converts ResourceAnnotations to protocol format for JSON response.
 */
public fun ResourceAnnotations.toProtocolFormat(): Map<String, Any?> =
    buildMap {
        audience?.let { put("audience", it) }
        priority?.let { put("priority", it) }
    }

/**
 * Converts a JsonObject outputSchema to protocol format.
 */
public fun JsonObject.toOutputSchemaFormat(): Map<String, Any?> =
    this.entries.associate { (key, value) ->
        key to value
    }
