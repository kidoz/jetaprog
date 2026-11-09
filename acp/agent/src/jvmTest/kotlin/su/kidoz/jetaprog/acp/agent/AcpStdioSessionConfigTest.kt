package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.client.AcpClient
import su.kidoz.jetaprog.acp.client.AcpClientHandler
import su.kidoz.jetaprog.acp.client.StdioAcpTransport
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpError
import su.kidoz.jetaprog.acp.protocol.AcpRequestException
import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.ClientSessionCapabilities
import su.kidoz.jetaprog.acp.protocol.ContentBlock
import su.kidoz.jetaprog.acp.protocol.SessionConfigBooleanCapability
import su.kidoz.jetaprog.acp.protocol.SessionConfigCapabilities
import su.kidoz.jetaprog.acp.protocol.SessionConfigOption
import su.kidoz.jetaprog.acp.protocol.SessionNotification
import su.kidoz.jetaprog.acp.protocol.SessionUpdate
import su.kidoz.jetaprog.acp.protocol.StopReason
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val stdioBooleanCapabilities =
    ClientCapabilities(session = ClientSessionCapabilities(SessionConfigCapabilities(SessionConfigBooleanCapability)))

/** Uses real process pipes and the production client/server transports, without credentials or provider calls. */
class AcpStdioSessionConfigTest {
    @Test
    fun settingsChangesRejectionsUpdatesAndRestorationCrossProcessPipes(): Unit =
        runBlocking {
            withWorkspace { workspace ->
                withPeer(workspace) { peer ->
                    assertEquals(
                        "stdio-settings-fixture",
                        peer.client.initializeResponse
                            ?.agentInfo
                            ?.name,
                    )
                    assertConfiguration(peer.initial, "fast", false)
                    assertConfiguration(
                        peer.client.setConfigOption(peer.sessionId, "model", "deep").configOptions,
                        "deep",
                        false,
                    )
                    for (value in listOf(true, false)) {
                        assertConfiguration(
                            peer.client.setConfigOption(peer.sessionId, "compact", value).configOptions,
                            "deep",
                            value,
                        )
                    }
                    val error =
                        assertFailsWith<AcpRequestException> {
                            peer.client.setConfigOption(peer.sessionId, "model", "unavailable")
                        }
                    assertEquals(AcpError.INVALID_PARAMS, error.code)
                    assertEquals("Model unavailable in test agent", error.message)
                    assertConfiguration(
                        peer.client.loadSession(peer.sessionId, workspace.path).configOptions!!,
                        "deep",
                        false,
                    )

                    assertConfiguration(
                        peer.client.setConfigOption(peer.sessionId, "model", "fast").configOptions,
                        "fast",
                        false,
                    )
                    assertEquals(StopReason.END_TURN, peer.client.promptText(peer.sessionId, "update").stopReason)
                    val update =
                        peer.updates
                            .map { it.update }
                            .filterIsInstance<SessionUpdate.ConfigOptionUpdate>()
                            .single()
                    assertConfiguration(update.configOptions, "deep", true)
                    val text = assertIs<SessionUpdate.AgentMessageChunk>(peer.updates.last().update)
                    assertEquals("Settings fixture ready: Привет", assertIs<ContentBlock.Text>(text.content).text)
                }
                // A new JVM must load the state saved by the original process.
                withPeer(workspace, restore = true) { peer ->
                    assertConfiguration(peer.initial, "deep", true)
                }
            }
        }

    @Test
    fun olderClientsReceiveSelectSettingsAcrossAllStdioResponsesAndUpdates(): Unit =
        runBlocking {
            withWorkspace { workspace ->
                withPeer(workspace, capabilities = ClientCapabilities()) { peer ->
                    assertEquals(listOf("model"), peer.initial.map { it.id })
                    assertEquals(
                        listOf("model"),
                        peer.client
                            .loadSession(peer.sessionId, workspace.path)
                            .configOptions!!
                            .map { it.id },
                    )
                    val changed = peer.client.setConfigOption(peer.sessionId, "model", "deep")
                    assertEquals(listOf("model"), changed.configOptions.map { it.id })
                    peer.client.promptText(peer.sessionId, "update")
                    val update =
                        peer.updates
                            .map { it.update }
                            .filterIsInstance<SessionUpdate.ConfigOptionUpdate>()
                            .single()
                    assertEquals(listOf("model"), update.configOptions.map { it.id })
                }
            }
        }

    @Test
    fun agentExitFailsBothAnActivePromptAndASettingsRequest(): Unit =
        runBlocking {
            withWorkspace { workspace ->
                withPeer(workspace) { peer ->
                    val prompt = async { runCatching { peer.client.promptText(peer.sessionId, "wait") } }
                    peer.waiting.await()
                    val error =
                        assertFailsWith<AcpRequestException> {
                            peer.client.setConfigOption(peer.sessionId, "model", "disconnect")
                        }
                    assertEquals(AcpError.INTERNAL_ERROR, error.code)
                    assertIs<AcpRequestException>(prompt.await().exceptionOrNull())
                    assertFalse(peer.client.isOpen)
                }
            }
        }

    @Test
    fun clientCloseReleasesAnActivePromptAndReapsTheChildProcess(): Unit =
        runBlocking {
            withWorkspace { workspace ->
                withPeer(workspace) { peer ->
                    val prompt = async { runCatching { peer.client.promptText(peer.sessionId, "wait") } }
                    peer.waiting.await()
                    peer.client.close()
                    assertIs<AcpRequestException>(prompt.await().exceptionOrNull())
                    assertFalse(peer.client.isOpen)
                    assertFalse(peer.processAlive())
                }
            }
        }

    @Test
    fun serverExitsCleanlyWhenTheClientClosesStdin() {
        val process = ProcessBuilder(testAgentCommand()).start()
        try {
            process.outputStream.close()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Agent did not exit after stdin EOF")
            assertEquals(0, process.exitValue(), process.errorStream.bufferedReader().readText())
            assertEquals("", process.inputStream.bufferedReader().readText(), "Logs must not contaminate ACP stdout")
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }

    private suspend fun withWorkspace(block: suspend (File) -> Unit) {
        val workspace = createTempDirectory("jetaprog stdio settings ").toFile().canonicalFile
        try {
            withTimeout(30_000) { block(workspace) }
        } finally {
            workspace.deleteRecursively()
        }
    }

    private suspend fun withPeer(
        workspace: File,
        capabilities: ClientCapabilities = stdioBooleanCapabilities,
        restore: Boolean = false,
        block: suspend (StdioConfigPeer) -> Unit,
    ): Unit =
        coroutineScope {
            val peer = StdioConfigPeer(this, workspace, capabilities)
            try {
                peer.start(restore)
                block(peer)
            } finally {
                withContext(NonCancellable) { peer.client.close() }
                assertFalse(peer.processAlive(), "Child process survived client close")
            }
        }

    private fun assertConfiguration(
        options: List<SessionConfigOption>,
        model: String,
        compact: Boolean,
    ) {
        assertEquals(listOf("model", "compact"), options.map { it.id })
        assertEquals(JsonPrimitive(model), options[0].currentValue)
        assertEquals(JsonPrimitive(compact), options[1].currentValue)
    }
}

private class StdioConfigPeer(
    scope: CoroutineScope,
    private val workspace: File,
    capabilities: ClientCapabilities,
) {
    private val transport = StdioAcpTransport(StdioAgentConfig(testAgentCommand(), workspace.path))
    val updates = CopyOnWriteArrayList<SessionNotification>()
    val waiting = CompletableDeferred<Unit>()
    val client =
        AcpClient(
            transport,
            scope,
            capabilities = capabilities,
            handler =
                object : AcpClientHandler {
                    override suspend fun sessionUpdate(notification: SessionNotification) {
                        updates += notification
                        val update = notification.update
                        if (update is SessionUpdate.AgentMessageChunk &&
                            update.content == ContentBlock.Text("Waiting")
                        ) {
                            waiting.complete(Unit)
                        }
                    }
                },
        )
    var sessionId = "stdio-settings-session"
    var initial = emptyList<SessionConfigOption>()
    private var pid: Long? = null

    suspend fun start(restore: Boolean) {
        transport.start()
        client.initialize()
        if (restore) {
            initial = client.loadSession(sessionId, workspace.path).configOptions!!
        } else {
            val response = client.newSession(workspace.path)
            sessionId = response.sessionId
            initial = response.configOptions!!
        }
        val first = File(workspace, ".jetaprog/stdio-settings-trace.jsonl").readLines().last()
        pid =
            AcpConnection.defaultJson
                .parseToJsonElement(
                    first,
                ).jsonObject
                .getValue("pid")
                .jsonPrimitive.content
                .toLong()
    }

    fun processAlive(): Boolean =
        pid?.let { ProcessHandle.of(it).map { process -> process.isAlive }.orElse(false) } ?: transport.isAlive
}

private fun testAgentCommand(): List<String> =
    listOf(
        File(System.getProperty("java.home"), "bin/java").path,
        "-Dlogback.configurationFile=${checkNotNull(
            StdioSessionConfigTestAgent::class.java.getResource("/acp-stdio-logback.xml"),
        )}",
        "-cp",
        checkNotNull(System.getProperty("jetaprog.acp.test.classpath")),
        StdioSessionConfigTestAgent::class.java.name,
    )
