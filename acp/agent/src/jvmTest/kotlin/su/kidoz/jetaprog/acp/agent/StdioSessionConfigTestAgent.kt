package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.acp.protocol.AgentCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.Implementation
import su.kidoz.jetaprog.acp.protocol.InitializeRequest
import su.kidoz.jetaprog.acp.protocol.InitializeResponse
import su.kidoz.jetaprog.acp.protocol.LoadSessionRequest
import su.kidoz.jetaprog.acp.protocol.LoadSessionResponse
import su.kidoz.jetaprog.acp.protocol.NewSessionRequest
import su.kidoz.jetaprog.acp.protocol.NewSessionResponse
import su.kidoz.jetaprog.acp.protocol.PromptRequest
import su.kidoz.jetaprog.acp.protocol.PromptResponse
import su.kidoz.jetaprog.acp.protocol.SessionConfigOption
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.SetSessionBooleanConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigRequest
import su.kidoz.jetaprog.acp.protocol.SetSessionConfigResponse
import su.kidoz.jetaprog.acp.protocol.StopReason
import java.io.File

/** Deterministic subprocess fixture, also usable for native desktop acceptance. No provider calls. */
public object StdioSessionConfigTestAgent {
    /** Starts the production ACP server over process stdin/stdout, exiting when the client closes stdin. */
    @JvmStatic
    public fun main(args: Array<String>): Unit =
        runBlocking {
            require(args.isEmpty())
            val finished = CompletableDeferred<Unit>()
            val transport = StdioServerTransport(System.`in`, System.out)
            val observed =
                object : AcpTransport by transport {
                    override val incoming = transport.incoming.onCompletion { finished.complete(Unit) }
                }
            val server = AcpAgentServer(PersistentConfigAgent(), observed, this)
            server.start()
            transport.start()
            try {
                finished.await()
            } finally {
                server.close()
            }
        }
}

private class PersistentConfigAgent : AcpAgent {
    private val json = AcpConnection.defaultJson
    private var workspace: File? = null
    private var model = "fast"
    private var compact = false

    override suspend fun initialize(request: InitializeRequest): InitializeResponse =
        InitializeResponse(
            agentCapabilities = AgentCapabilities(loadSession = true),
            agentInfo = Implementation("stdio-settings-fixture", "1.0"),
        )

    override suspend fun newSession(request: NewSessionRequest): NewSessionResponse {
        workspace = File(request.cwd).canonicalFile
        model = "fast"
        compact = false
        save()
        record("new")
        return NewSessionResponse(SESSION_ID, configOptions = configuration())
    }

    override suspend fun loadSession(request: LoadSessionRequest): LoadSessionResponse {
        validateSession(request.sessionId)
        workspace = File(request.cwd).canonicalFile
        val saved = json.parseToJsonElement(stateFile().readText()).jsonObject
        model = saved.getValue("model").jsonPrimitive.content
        compact =
            saved
                .getValue("compact")
                .jsonPrimitive.content
                .toBooleanStrict()
        record("load")
        return LoadSessionResponse(configOptions = configuration())
    }

    override suspend fun setConfigOption(request: SetSessionConfigRequest): SetSessionConfigResponse {
        validateSession(request.sessionId)
        if (request.configId != "model" || request.value !in listOf("fast", "deep", "unavailable", "disconnect")) {
            throw AcpRequestException(AcpError.INVALID_PARAMS, "Unknown setting or value")
        }
        record("select", JsonPrimitive(request.value))
        if (request.value == "unavailable") {
            throw AcpRequestException(AcpError.INVALID_PARAMS, "Model unavailable in test agent")
        }
        if (request.value == "disconnect") Runtime.getRuntime().halt(29)
        model = request.value
        save()
        return SetSessionConfigResponse(configuration())
    }

    override suspend fun setConfigOption(request: SetSessionBooleanConfigRequest): SetSessionConfigResponse {
        validateSession(request.sessionId)
        if (request.configId != "compact") throw AcpRequestException(AcpError.INVALID_PARAMS, "Unknown boolean setting")
        compact = request.value
        save()
        record("boolean", JsonPrimitive(compact))
        return SetSessionConfigResponse(configuration())
    }

    override suspend fun prompt(
        request: PromptRequest,
        session: AcpAgentSession,
    ): PromptResponse {
        validateSession(request.sessionId)
        record("prompt")
        val command =
            request.prompt
                .filterIsInstance<ContentBlock.Text>()
                .joinToString(" ") { it.text }
                .trim()
        if (command == "update") {
            model = "deep"
            compact = true
            save()
            session.update(SessionUpdate.ConfigOptionUpdate(configuration()))
            record("update")
        }
        session.sendMessageChunk(if (command == "wait") "Waiting" else "Settings fixture ready: Привет")
        if (command == "wait") awaitCancellation()
        return PromptResponse(StopReason.END_TURN)
    }

    private fun configuration(): List<SessionConfigOption> =
        listOf(
            SessionConfigOption(
                "model",
                "Model",
                "select",
                JsonPrimitive(model),
                category = "model",
                options =
                    json.parseToJsonElement(
                        """[{"value":"fast","name":"Fast"},{"value":"deep","name":"Deep"},{"value":"unavailable","name":"Unavailable"},{"value":"disconnect","name":"Disconnect"}]""",
                    ),
            ),
            SessionConfigOption(
                "compact",
                "Auto compact",
                "boolean",
                JsonPrimitive(compact),
                description = "Compact the conversation automatically",
            ),
        )

    private fun validateSession(sessionId: String) {
        if (sessionId != SESSION_ID) throw AcpRequestException(AcpError.RESOURCE_NOT_FOUND, "Unknown session")
    }

    private fun stateFile(): File = File(checkNotNull(workspace), ".jetaprog/stdio-settings.json")

    private fun save() {
        val file = stateFile()
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        file.writeText(
            buildJsonObject {
                put("model", model)
                put("compact", compact)
            }.toString(),
        )
    }

    private fun record(
        event: String,
        value: JsonPrimitive? = null,
    ) {
        val entry =
            buildJsonObject {
                put("event", event)
                put("pid", ProcessHandle.current().pid())
                if (value != null) put("value", value)
            }
        File(stateFile().parentFile, "stdio-settings-trace.jsonl").appendText("$entry\n")
    }

    private companion object {
        const val SESSION_ID = "stdio-settings-session"
    }
}
