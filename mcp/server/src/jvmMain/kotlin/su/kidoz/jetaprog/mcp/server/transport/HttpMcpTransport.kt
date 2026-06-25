package su.kidoz.jetaprog.mcp.server.transport

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import su.kidoz.jetaprog.mcp.server.McpServerConfig
import java.util.UUID

private const val STOP_GRACE_MILLIS = 500L
private const val STOP_TIMEOUT_MILLIS = 1500L

/**
 * JVM default transport: an MCP **Streamable HTTP** endpoint (a single `POST /mcp`
 * that accepts a JSON-RPC message and returns the JSON-RPC response). Bound to
 * loopback only. Compatible with MCP clients configured with `"type": "http"`.
 */
public actual fun createMcpTransport(config: McpServerConfig): McpTransport? {
    if (!config.transports.sse.enabled) return null
    return HttpMcpTransport(host = LOOPBACK_HOST, port = config.transports.sse.port)
}

private const val LOOPBACK_HOST = "127.0.0.1"

/**
 * Serves the MCP server over HTTP on [host]:[port] at `/mcp`.
 */
public class HttpMcpTransport(
    private val host: String,
    private val port: Int,
) : McpTransport {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val sessionId = UUID.randomUUID().toString()
    private var server: EmbeddedServer<*, *>? = null

    override val endpointDescription: String get() = "http://$host:$port/mcp"

    override suspend fun start(handler: suspend (JsonObject) -> JsonObject?) {
        if (server != null) return
        server =
            embeddedServer(CIO, host = host, port = port) {
                routing {
                    get("/mcp") { call.respond(HttpStatusCode.MethodNotAllowed) }
                    post("/mcp") {
                        val request =
                            runCatching { json.parseToJsonElement(call.receiveText()) as? JsonObject }
                                .getOrNull()
                        if (request == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                        call.response.headers.append("Mcp-Session-Id", sessionId)
                        val response = handler(request)
                        if (response == null) {
                            call.respond(HttpStatusCode.Accepted)
                        } else {
                            call.respondText(
                                text = json.encodeToString(JsonObject.serializer(), response),
                                contentType = ContentType.Application.Json,
                            )
                        }
                    }
                }
            }.also { it.start(wait = false) }
    }

    override suspend fun stop() {
        server?.stop(STOP_GRACE_MILLIS, STOP_TIMEOUT_MILLIS)
        server = null
    }
}
