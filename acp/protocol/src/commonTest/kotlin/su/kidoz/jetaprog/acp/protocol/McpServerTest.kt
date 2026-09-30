package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpServerTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun httpSessionUsesUrlAndHeadersInsteadOfAnExecutable() {
        val server =
            McpServer.Http(
                "ide",
                "http://127.0.0.1:3000/mcp",
                listOf(HttpHeader("Authorization", "Bearer test")),
            )
        val request = NewSessionRequest("/workspace", listOf(server))
        val encoded = json.encodeToString(request)
        val actual =
            json
                .parseToJsonElement(encoded)
                .jsonObject
                .getValue("mcpServers")
                .jsonArray
                .single()
        val expected =
            json.parseToJsonElement(
                """{"name":"ide","type":"http","url":"http://127.0.0.1:3000/mcp","headers":[{"name":"Authorization","value":"Bearer test"}]}""",
            )
        assertEquals(expected, actual)
        assertEquals(request, json.decodeFromString<NewSessionRequest>(encoded))
    }

    @Test
    fun emptyHeadersAndStdioArgumentsAreStillPresentOnTheWire() {
        val http: McpServer = McpServer.Http("ide", "http://localhost/mcp")
        assertEquals(
            json.parseToJsonElement("""{"name":"ide","type":"http","url":"http://localhost/mcp","headers":[]}"""),
            json.parseToJsonElement(json.encodeToString(http)),
        )
        val stdio: McpServer = McpServer.Stdio("local", "/bin/server")
        val encoded = json.encodeToString(stdio)
        assertEquals(
            json.parseToJsonElement("""{"name":"local","command":"/bin/server","args":[],"env":[]}"""),
            json.parseToJsonElement(encoded),
        )
        assertEquals(stdio, json.decodeFromString<McpServer>(encoded))
    }

    @Test
    fun httpTransportRequiresAnAdvertisedCapability() {
        assertFalse(
            json
                .decodeFromString<InitializeResponse>("""{"protocolVersion":1}""")
                .agentCapabilities.mcpCapabilities.http,
        )
        assertTrue(
            json
                .decodeFromString<InitializeResponse>(
                    """{"protocolVersion":1,"agentCapabilities":{"mcpCapabilities":{"http":true}}}""",
                ).agentCapabilities.mcpCapabilities.http,
        )
    }
}
