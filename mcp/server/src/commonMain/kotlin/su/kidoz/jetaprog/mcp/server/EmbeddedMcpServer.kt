package su.kidoz.jetaprog.mcp.server

import su.kidoz.jetaprog.mcp.server.prompts.PromptsRegistry
import su.kidoz.jetaprog.mcp.server.protocol.McpDispatcher
import su.kidoz.jetaprog.mcp.server.protocol.ServerInfo
import su.kidoz.jetaprog.mcp.server.resources.ResourcesManager
import su.kidoz.jetaprog.mcp.server.tools.ToolsRegistry
import su.kidoz.jetaprog.mcp.server.transport.McpTransport
import su.kidoz.jetaprog.mcp.server.transport.createMcpTransport

/**
 * Embedded MCP (Model Context Protocol) server for AI agent integration.
 *
 * Exposes IDE functionality to external AI agents (e.g. a terminal `claude` or
 * `codex`) over an HTTP transport. Register tools via [tools] before [start].
 */
public class EmbeddedMcpServer(
    private val config: McpServerConfig,
    private val serverVersion: String = "1.0.0",
) {
    private val toolsRegistry = ToolsRegistry()
    private val resourcesManager = ResourcesManager()
    private val promptsRegistry = PromptsRegistry()

    private var transport: McpTransport? = null
    private var isRunning = false

    /**
     * Whether the server is running.
     */
    public val running: Boolean get() = isRunning

    /**
     * The endpoint clients connect to (e.g. `http://127.0.0.1:3000/mcp`), or null when not running.
     */
    public val endpoint: String? get() = transport?.endpointDescription

    /**
     * The tools registry.
     */
    public val tools: ToolsRegistry get() = toolsRegistry

    /**
     * The resources manager.
     */
    public val resources: ResourcesManager get() = resourcesManager

    /**
     * The prompts registry.
     */
    public val prompts: PromptsRegistry get() = promptsRegistry

    /**
     * Starts the MCP server.
     */
    public suspend fun start() {
        if (isRunning) return
        if (!config.enabled) return

        val dispatcher =
            McpDispatcher(
                serverInfo = ServerInfo(name = "jetaprog-ide", version = serverVersion, title = "JetaProg IDE"),
                tools = toolsRegistry,
            )
        transport = createMcpTransport(config)
        transport?.start { request -> dispatcher.handle(request) }

        isRunning = true
    }

    /**
     * Stops the MCP server.
     */
    public suspend fun stop() {
        if (!isRunning) return

        transport?.stop()
        transport = null

        isRunning = false
    }
}
