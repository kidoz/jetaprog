package su.kidoz.jetaprog.mcp.server

import kotlinx.serialization.Serializable

/**
 * Configuration for the embedded MCP server.
 */
@Serializable
public data class McpServerConfig(
    /**
     * Whether the MCP server is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Transport configurations.
     */
    val transports: TransportsConfig = TransportsConfig(),
    /**
     * Security configuration.
     */
    val security: SecurityConfig = SecurityConfig(),
    /**
     * Server capabilities.
     */
    val capabilities: CapabilitiesConfig = CapabilitiesConfig(),
)

/**
 * Transport configurations.
 */
@Serializable
public data class TransportsConfig(
    /**
     * stdio transport configuration.
     */
    val stdio: StdioConfig = StdioConfig(),
    /**
     * SSE transport configuration.
     */
    val sse: SseConfig = SseConfig(),
    /**
     * WebSocket transport configuration.
     */
    val webSocket: WebSocketConfig = WebSocketConfig(),
)

/**
 * stdio transport configuration.
 */
@Serializable
public data class StdioConfig(
    val enabled: Boolean = true,
)

/**
 * SSE transport configuration.
 */
@Serializable
public data class SseConfig(
    val enabled: Boolean = true,
    val port: Int = 3000,
)

/**
 * WebSocket transport configuration.
 */
@Serializable
public data class WebSocketConfig(
    val enabled: Boolean = true,
    val port: Int = 3001,
)

/**
 * Security configuration.
 */
@Serializable
public data class SecurityConfig(
    /**
     * Whether authentication is enabled.
     */
    val authenticationEnabled: Boolean = false,
    /**
     * Allowed origins for CORS.
     */
    val allowedOrigins: List<String> = listOf("*"),
    /**
     * Rate limiting configuration.
     */
    val rateLimiting: RateLimitingConfig = RateLimitingConfig(),
)

/**
 * Rate limiting configuration.
 */
@Serializable
public data class RateLimitingConfig(
    val enabled: Boolean = true,
    val requestsPerMinute: Int = 60,
)

/**
 * Server capabilities configuration.
 */
@Serializable
public data class CapabilitiesConfig(
    val tools: Boolean = true,
    val resources: Boolean = true,
    val prompts: Boolean = true,
    val sampling: Boolean = false,
)
