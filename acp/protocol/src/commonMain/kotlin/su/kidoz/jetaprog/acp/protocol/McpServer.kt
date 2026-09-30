package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An MCP transport configuration supplied when creating or loading an ACP session. */
@Serializable(with = McpServerSerializer::class)
public sealed interface McpServer {
    /** Unique server name within the session. */
    public val name: String

    /** Process-based MCP transport; ACP deliberately omits a type discriminator. */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    public data class Stdio(
        override val name: String,
        /** Executable to launch. */
        val command: String,
        /** Required on the wire, including when empty. */
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        val args: List<String> = emptyList(),
        /** Environment for the server process. */
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        val env: List<EnvVariable> = emptyList(),
    ) : McpServer

    /** Streamable HTTP transport; only send when the agent advertises HTTP support. */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    public data class Http(
        override val name: String,
        /** Full MCP endpoint URL. */
        val url: String,
        /** Headers supplied to the MCP endpoint, including optional authorization. */
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        val headers: List<HttpHeader> = emptyList(),
    ) : McpServer {
        /** Required ACP transport discriminator. */
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        public val type: String = "http"
    }
}

/** Header sent by an agent to an HTTP MCP server. */
@Serializable
public data class HttpHeader(
    /** HTTP header name. */
    val name: String,
    /** HTTP header value. */
    val value: String,
)

/** Preserves ACP's untagged stdio format while accepting tagged HTTP transports. */
public object McpServerSerializer : JsonContentPolymorphicSerializer<McpServer>(McpServer::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<McpServer> =
        when (val type = element.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
            null -> McpServer.Stdio.serializer()
            "http" -> McpServer.Http.serializer()
            else -> throw SerializationException("Unsupported MCP transport: $type")
        }
}
