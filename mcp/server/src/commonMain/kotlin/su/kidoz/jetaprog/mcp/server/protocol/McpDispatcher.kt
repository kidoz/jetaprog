package su.kidoz.jetaprog.mcp.server.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import su.kidoz.jetaprog.mcp.server.prompts.PromptsRegistry
import su.kidoz.jetaprog.mcp.server.resources.ResourceContent
import su.kidoz.jetaprog.mcp.server.resources.ResourcesManager
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.mcp.server.tools.ToolsRegistry
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Stateless MCP JSON-RPC 2.0 request handler.
 *
 * Translates a single incoming JSON-RPC message into a response object (or `null`
 * for notifications), dispatching `initialize`, `tools/list`, `tools/call`,
 * `resources/list`, `prompts/list` and `ping` against the registries. The transport
 * layer is responsible for delivery; this class is pure and unit-testable.
 */
public class McpDispatcher(
    private val serverInfo: ServerInfo,
    private val tools: ToolsRegistry,
    private val resources: ResourcesManager = ResourcesManager(),
    private val prompts: PromptsRegistry = PromptsRegistry(),
) {
    /**
     * Handles one JSON-RPC [request], returning the response object, or `null` when
     * the message is a notification (no `id`) that needs no reply.
     */
    public suspend fun handle(request: JsonObject): JsonObject? {
        val method = (request["method"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val id = request["id"]
        if (method == null) {
            return id?.let { errorResponse(it, McpErrorCodes.INVALID_REQUEST, "Missing method") }
        }
        // Notifications (no id) are acknowledged without a response body.
        if (id == null || id is JsonNull) {
            return null
        }
        return when (method) {
            "initialize" -> success(id, initializeResult())
            "ping" -> success(id, buildJsonObject {})
            "tools/list" -> success(id, toolsListResult())
            "tools/call" -> toolsCall(id, request["params"] as? JsonObject)
            "resources/list" -> success(id, resourcesListResult())
            "resources/read" -> resourcesRead(id, request["params"] as? JsonObject)
            "prompts/list" -> success(id, promptsListResult())
            "prompts/get" -> promptsGet(id, request["params"] as? JsonObject)
            else -> errorResponse(id, McpErrorCodes.METHOD_NOT_FOUND, "Unknown method: $method")
        }
    }

    private fun initializeResult(): JsonObject =
        buildJsonObject {
            put("protocolVersion", MCP_PROTOCOL_VERSION)
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
                putJsonObject("resources") {
                    put("subscribe", false)
                    put("listChanged", false)
                }
                putJsonObject("prompts") { put("listChanged", false) }
            }
            putJsonObject("serverInfo") {
                put("name", serverInfo.name)
                put("version", serverInfo.version)
                serverInfo.title?.let { put("title", it) }
            }
        }

    private fun toolsListResult(): JsonObject =
        buildJsonObject {
            putJsonArray("tools") {
                tools.list().forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        },
                    )
                }
            }
        }

    private suspend fun toolsCall(
        id: JsonElement,
        params: JsonObject?,
    ): JsonObject {
        val name =
            (params?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return errorResponse(id, McpErrorCodes.INVALID_PARAMS, "Missing tool name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val result = tools.execute(name, arguments)
        return success(id, result.toToolCallResult())
    }

    private fun resourcesListResult(): JsonObject =
        buildJsonObject {
            putJsonArray("resources") {
                resources.list().forEach { resource ->
                    add(
                        buildJsonObject {
                            put("uri", resource.uri)
                            put("name", resource.name)
                            put("description", resource.description)
                            put("mimeType", resource.mimeType)
                        },
                    )
                }
            }
        }

    private suspend fun resourcesRead(
        id: JsonElement,
        params: JsonObject?,
    ): JsonObject {
        val uri =
            params?.get("uri")?.jsonPrimitive?.content
                ?: return errorResponse(id, McpErrorCodes.INVALID_PARAMS, "Missing resource URI")
        val resource =
            resources.get(uri)
                ?: return errorResponse(id, McpErrorCodes.INVALID_PARAMS, "Resource not found: $uri")
        return resources.read(uri).fold(
            onSuccess = { content ->
                success(
                    id,
                    buildJsonObject {
                        putJsonArray("contents") {
                            add(resourceContent(resource.uri, resource.mimeType, content))
                        }
                    },
                )
            },
            onFailure = { error ->
                errorResponse(id, McpErrorCodes.INTERNAL_ERROR, error.message ?: "Resource read failed")
            },
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun resourceContent(
        uri: String,
        mimeType: String,
        content: ResourceContent,
    ): JsonObject =
        buildJsonObject {
            put("uri", uri)
            when (content) {
                is ResourceContent.Text -> {
                    put("mimeType", mimeType)
                    put("text", content.text)
                }

                is ResourceContent.Binary -> {
                    put("mimeType", content.mimeType)
                    put("blob", Base64.Default.encode(content.data))
                }
            }
        }

    private fun promptsListResult(): JsonObject =
        buildJsonObject {
            putJsonArray("prompts") {
                prompts.list().forEach { prompt ->
                    add(
                        buildJsonObject {
                            put("name", prompt.name)
                            put("description", prompt.description)
                            putJsonArray("arguments") {
                                prompt.arguments.forEach { argument ->
                                    add(
                                        buildJsonObject {
                                            put("name", argument.name)
                                            put("description", argument.description)
                                            put("required", argument.required)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

    private suspend fun promptsGet(
        id: JsonElement,
        params: JsonObject?,
    ): JsonObject {
        val name =
            params?.get("name")?.jsonPrimitive?.content
                ?: return errorResponse(id, McpErrorCodes.INVALID_PARAMS, "Missing prompt name")
        val prompt =
            prompts.get(name)
                ?: return errorResponse(id, McpErrorCodes.INVALID_PARAMS, "Prompt not found: $name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return prompts.generate(name, arguments).fold(
            onSuccess = { generated ->
                success(
                    id,
                    buildJsonObject {
                        put("description", prompt.description)
                        putJsonArray("messages") {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    putJsonObject("content") {
                                        put("type", "text")
                                        put("text", generated)
                                    }
                                },
                            )
                        }
                    },
                )
            },
            onFailure = { error ->
                errorResponse(id, McpErrorCodes.INTERNAL_ERROR, error.message ?: "Prompt generation failed")
            },
        )
    }

    private fun ToolResult.toToolCallResult(): JsonObject =
        when (this) {
            is ToolResult.Success -> {
                buildJsonObject {
                    put("content", content.toContentArray())
                    put("isError", false)
                }
            }

            is ToolResult.Error -> {
                buildJsonObject {
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", message)
                            },
                        )
                    }
                    put("isError", true)
                }
            }
        }

    private fun List<ToolContent>.toContentArray() =
        buildJsonArray {
            this@toContentArray.forEach { item ->
                when (item) {
                    is ToolContent.Text -> {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", item.text)
                            },
                        )
                    }

                    is ToolContent.Image -> {
                        add(
                            buildJsonObject {
                                put("type", "image")
                                put("mimeType", item.mimeType)
                            },
                        )
                    }
                }
            }
        }

    private fun success(
        id: JsonElement,
        result: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun errorResponse(
        id: JsonElement,
        code: Int,
        message: String,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
}
