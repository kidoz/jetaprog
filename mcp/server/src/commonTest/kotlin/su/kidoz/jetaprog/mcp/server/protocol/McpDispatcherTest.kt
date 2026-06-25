package su.kidoz.jetaprog.mcp.server.protocol

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import su.kidoz.jetaprog.mcp.server.tools.Tool
import su.kidoz.jetaprog.mcp.server.tools.ToolContent
import su.kidoz.jetaprog.mcp.server.tools.ToolResult
import su.kidoz.jetaprog.mcp.server.tools.ToolsRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpDispatcherTest {
    private fun dispatcher(): McpDispatcher {
        val registry = ToolsRegistry()
        registry.register(
            Tool(
                name = "echo",
                description = "Echoes the text argument",
                inputSchema = buildJsonObject { put("type", "object") },
            ) { args ->
                val value = args["text"]?.jsonPrimitive?.content ?: ""
                ToolResult.Success(listOf(ToolContent.Text(value)))
            },
        )
        return McpDispatcher(ServerInfo(name = "jetaprog-ide", version = "1.0.0"), registry)
    }

    private fun request(
        id: Int,
        method: String,
        params: JsonObject? = null,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }

    @Test
    fun initializeReportsProtocolAndServerInfo() =
        runTest {
            val response = dispatcher().handle(request(1, "initialize"))!!
            val result = response["result"]!!.jsonObject
            assertEquals(MCP_PROTOCOL_VERSION, result["protocolVersion"]!!.jsonPrimitive.content)
            assertEquals("jetaprog-ide", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
        }

    @Test
    fun toolsListIncludesRegisteredTools() =
        runTest {
            val response = dispatcher().handle(request(2, "tools/list"))!!
            val tools = response["result"]!!.jsonObject["tools"]!!.jsonArray
            assertEquals(
                "echo",
                tools
                    .single()
                    .jsonObject["name"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun toolsCallExecutesAndReturnsContent() =
        runTest {
            val params =
                buildJsonObject {
                    put("name", "echo")
                    putJsonObject("arguments") { put("text", "hello") }
                }
            val response = dispatcher().handle(request(3, "tools/call", params))!!
            val result = response["result"]!!.jsonObject
            assertEquals(false, result["isError"]!!.jsonPrimitive.content.toBoolean())
            val first = result["content"]!!.jsonArray.single().jsonObject
            assertEquals("text", first["type"]!!.jsonPrimitive.content)
            assertEquals("hello", first["text"]!!.jsonPrimitive.content)
        }

    @Test
    fun unknownMethodReturnsMethodNotFound() =
        runTest {
            val response = dispatcher().handle(request(4, "does/notExist"))!!
            assertEquals(
                McpErrorCodes.METHOD_NOT_FOUND,
                response["error"]!!
                    .jsonObject["code"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
        }

    @Test
    fun notificationWithoutIdReturnsNoResponse() =
        runTest {
            val notification =
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                }
            assertNull(dispatcher().handle(notification))
        }
}
