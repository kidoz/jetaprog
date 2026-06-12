package su.kidoz.jetaprog.mcp.server.tools

import kotlinx.serialization.json.JsonObject

/**
 * A tool that can be invoked by AI agents.
 */
public data class Tool(
    /**
     * The tool name.
     */
    val name: String,
    /**
     * Description of what the tool does.
     */
    val description: String,
    /**
     * JSON Schema for the tool's input parameters.
     */
    val inputSchema: JsonObject,
    /**
     * The handler that executes the tool.
     */
    val handler: suspend (args: JsonObject) -> ToolResult,
)

/**
 * Result of a tool execution.
 */
public sealed interface ToolResult {
    /**
     * Successful tool execution.
     */
    public data class Success(
        val content: List<ToolContent>,
    ) : ToolResult

    /**
     * Failed tool execution.
     */
    public data class Error(
        val message: String,
    ) : ToolResult
}

/**
 * Content returned by a tool.
 */
public sealed interface ToolContent {
    /**
     * Text content.
     */
    public data class Text(
        val text: String,
    ) : ToolContent

    /**
     * Image content.
     */
    public data class Image(
        val data: ByteArray,
        val mimeType: String,
    ) : ToolContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
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
 * Registry for MCP tools.
 */
public class ToolsRegistry {
    private val tools = mutableMapOf<String, Tool>()

    /**
     * Registers a tool.
     */
    public fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    /**
     * Unregisters a tool.
     */
    public fun unregister(name: String) {
        tools.remove(name)
    }

    /**
     * Gets a tool by name.
     */
    public fun get(name: String): Tool? = tools[name]

    /**
     * Lists all registered tools.
     */
    public fun list(): List<Tool> = tools.values.toList()

    /**
     * Executes a tool.
     */
    public suspend fun execute(
        name: String,
        args: JsonObject,
    ): ToolResult {
        val tool = tools[name] ?: return ToolResult.Error("Tool not found: $name")
        return try {
            tool.handler(args)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Tool execution failed")
        }
    }
}
