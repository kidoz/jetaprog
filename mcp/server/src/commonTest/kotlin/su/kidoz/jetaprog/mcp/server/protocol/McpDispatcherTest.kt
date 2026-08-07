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
import su.kidoz.jetaprog.mcp.server.prompts.Prompt
import su.kidoz.jetaprog.mcp.server.prompts.PromptArgument
import su.kidoz.jetaprog.mcp.server.prompts.PromptsRegistry
import su.kidoz.jetaprog.mcp.server.resources.Resource
import su.kidoz.jetaprog.mcp.server.resources.ResourceContent
import su.kidoz.jetaprog.mcp.server.resources.ResourcesManager
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
        val resources = ResourcesManager()
        resources.register(
            Resource("jetaprog://project", "Project", "Project context", "text/plain") {
                ResourceContent.Text("project context")
            },
        )
        val prompts = PromptsRegistry()
        prompts.register(
            Prompt(
                name = "review",
                description = "Review a target",
                arguments = listOf(PromptArgument("target", "Target to review", required = true)),
            ) { args -> "Review ${args["target"]?.jsonPrimitive?.content}" },
        )
        return McpDispatcher(
            ServerInfo(name = "jetaprog-ide", version = "1.0.0"),
            registry,
            resources,
            prompts,
        )
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
            assertTrue(result["capabilities"]!!.jsonObject.containsKey("resources"))
            assertTrue(result["capabilities"]!!.jsonObject.containsKey("prompts"))
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
    fun resourcesCanBeListedAndRead() =
        runTest {
            val listed = dispatcher().handle(request(5, "resources/list"))!!
            val resource =
                listed["result"]!!
                    .jsonObject["resources"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("jetaprog://project", resource["uri"]!!.jsonPrimitive.content)

            val params = buildJsonObject { put("uri", "jetaprog://project") }
            val read = dispatcher().handle(request(6, "resources/read", params))!!
            val content =
                read["result"]!!
                    .jsonObject["contents"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("project context", content["text"]!!.jsonPrimitive.content)
        }

    @Test
    fun promptsCanBeListedAndGenerated() =
        runTest {
            val listed = dispatcher().handle(request(7, "prompts/list"))!!
            val prompt =
                listed["result"]!!
                    .jsonObject["prompts"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("review", prompt["name"]!!.jsonPrimitive.content)

            val params =
                buildJsonObject {
                    put("name", "review")
                    putJsonObject("arguments") { put("target", "workspace") }
                }
            val generated = dispatcher().handle(request(8, "prompts/get", params))!!
            val message =
                generated["result"]!!
                    .jsonObject["messages"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("Review workspace", message["content"]!!.jsonObject["text"]!!.jsonPrimitive.content)
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
