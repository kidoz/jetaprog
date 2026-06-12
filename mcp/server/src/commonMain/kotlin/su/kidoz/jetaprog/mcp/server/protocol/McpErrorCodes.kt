package su.kidoz.jetaprog.mcp.server.protocol

/**
 * Standard MCP error codes following the JSON-RPC 2.0 specification.
 */
public object McpErrorCodes {
    /**
     * Parse error - Invalid JSON was received.
     */
    public const val PARSE_ERROR: Int = -32700

    /**
     * Invalid request - The JSON sent is not a valid Request object.
     */
    public const val INVALID_REQUEST: Int = -32600

    /**
     * Method not found - The method does not exist or is not available.
     */
    public const val METHOD_NOT_FOUND: Int = -32601

    /**
     * Invalid params - Invalid method parameter(s).
     */
    public const val INVALID_PARAMS: Int = -32602

    /**
     * Internal error - Internal JSON-RPC error.
     */
    public const val INTERNAL_ERROR: Int = -32603

    // MCP-specific error codes

    /**
     * Resource not found - The requested resource does not exist.
     */
    public const val RESOURCE_NOT_FOUND: Int = -32002

    /**
     * Tool not found - The requested tool does not exist.
     */
    public const val TOOL_NOT_FOUND: Int = -32002

    /**
     * Prompt not found - The requested prompt does not exist.
     */
    public const val PROMPT_NOT_FOUND: Int = -32002

    /**
     * Tool execution error - The tool execution failed.
     */
    public const val TOOL_EXECUTION_ERROR: Int = -32000
}
