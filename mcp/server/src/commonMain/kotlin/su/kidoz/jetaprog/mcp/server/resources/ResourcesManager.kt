package su.kidoz.jetaprog.mcp.server.resources

/**
 * A resource exposed by the MCP server.
 */
public data class Resource(
    /**
     * The resource URI.
     */
    val uri: String,
    /**
     * Human-readable name.
     */
    val name: String,
    /**
     * Description of the resource.
     */
    val description: String,
    /**
     * The MIME type of the resource content.
     */
    val mimeType: String,
    /**
     * Handler that provides the resource content.
     */
    val handler: suspend () -> ResourceContent,
)

/**
 * Content of a resource.
 */
public sealed interface ResourceContent {
    /**
     * Text content.
     */
    public data class Text(
        val text: String,
    ) : ResourceContent

    /**
     * Binary content.
     */
    public data class Binary(
        val data: ByteArray,
        val mimeType: String,
    ) : ResourceContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return data.contentEquals(other.data) && mimeType == other.mimeType
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }
}

/**
 * Manages MCP resources.
 */
public class ResourcesManager {
    private val resources = mutableMapOf<String, Resource>()

    /**
     * Registers a resource.
     */
    public fun register(resource: Resource) {
        resources[resource.uri] = resource
    }

    /**
     * Unregisters a resource.
     */
    public fun unregister(uri: String) {
        resources.remove(uri)
    }

    /**
     * Gets a resource by URI.
     */
    public fun get(uri: String): Resource? = resources[uri]

    /**
     * Lists all registered resources.
     */
    public fun list(): List<Resource> = resources.values.toList()

    /**
     * Reads a resource's content.
     */
    public suspend fun read(uri: String): Result<ResourceContent> {
        val resource =
            resources[uri] ?: return Result.failure(
                IllegalArgumentException("Resource not found: $uri"),
            )
        return try {
            Result.success(resource.handler())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
