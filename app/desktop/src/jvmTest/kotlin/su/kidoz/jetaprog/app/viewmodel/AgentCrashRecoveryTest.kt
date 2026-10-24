package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AgentCheckout
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.SessionRestoration
import su.kidoz.jetaprog.app.ui.agent.agentChecks
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.platform.process.JvmProcessExecutor
import java.io.File
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real JVM exits with a scripted ACP peer: no credentials or provider calls required. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentCrashRecoveryTest {
    @Test
    fun abruptExitRetainsPartialReviewInBothCheckoutTypes() =
        runBlocking {
            val root = createTempDirectory("jetaprog-crash-recovery").toFile().canonicalFile
            try {
                for (checkout in AgentCheckout.entries) {
                    val project = File(root, checkout.name).apply { mkdir() }
                    runChild(project, checkout, "produce", CRASH_EXIT)
                    val record = AgentHistoryStore(JvmFileSystem(), project.path).list().single()
                    assertTrue(record.interrupted)
                    assertEquals(project.path, record.projectPath)
                    assertEquals(File(project, "Reader.kt").path, record.pendingReviews.single().path)
                    File(project, "Reader.kt").writeText("manual edit after crash")
                    runChild(project, checkout, "restore", 0)
                    assertEquals("manual edit after crash", File(project, "Reader.kt").readText())
                }
            } finally {
                root.deleteRecursively()
            }
        }

    private suspend fun runChild(
        project: File,
        checkout: AgentCheckout,
        mode: String,
        expectedExit: Int,
    ) {
        val classpath = checkNotNull(System.getProperty("jetaprog.recovery.classpath"))
        val result =
            JvmProcessExecutor()
                .execute(
                    listOf(
                        File(System.getProperty("java.home"), "bin/java").path,
                        "-cp",
                        classpath,
                        javaClass.name,
                        project.path,
                        checkout.name,
                        mode,
                    ),
                    workingDirectory = project.path,
                    timeoutMillis = 30_000,
                ).getOrThrow()
        assertEquals(expectedExit, result.exitCode, result.stderr.takeLast(3000))
    }

    private suspend fun exercise(
        project: File,
        checkout: AgentCheckout,
        produce: Boolean,
    ) {
        val store = AgentHistoryStore(JvmFileSystem(), project.path)
        val model =
            AgentSessionViewModel(
                projectPath = project.path,
                fileSystem = JvmFileSystem(),
                checkout = checkout,
                transportFactory = { CrashTransport(project) },
            )
        if (produce) {
            model.dispatch(AgentIntent.Send("Edit before sudden process exit"))
            withTimeout(15_000) {
                while (store
                        .list()
                        .singleOrNull()
                        ?.pendingReviews
                        ?.isNotEmpty() != true
                ) {
                    delay(50)
                }
            }
            assertTrue(model.state.value.isStreaming)
            // Deliberately skip dispose, shutdown, finally blocks, and JVM shutdown hooks.
            Runtime.getRuntime().halt(CRASH_EXIT)
        } else {
            try {
                model.dispatch(AgentIntent.RestoreSession(store.list().single().id))
                withTimeout(15_000) {
                    while (model.state.value.restoration !is SessionRestoration.Resumed) delay(50)
                }
                assertTrue(model.state.value.interrupted)
                assertFalse(model.state.value.isStreaming)
                assertTrue(agentChecks(model.state.value.turns, model.state.value.workspaceRevision, false).isEmpty())
                val path = File(project, "Reader.kt").path
                model.dispatch(AgentIntent.DismissReviewFile(path))
                withTimeout(5_000) {
                    while (path !in model.state.value.reviewConflicts) delay(50)
                }
                assertEquals("manual edit after crash", File(path).readText())
                model.shutdown()
            } finally {
                model.dispose()
            }
        }
    }

    companion object {
        private const val CRASH_EXIT = 23

        /** Separate-process scenario entry point used by this test only. */
        @JvmStatic
        fun main(args: Array<String>): Unit =
            runBlocking {
                val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                Dispatchers.setMain(dispatcher)
                try {
                    withContext(Dispatchers.Main) {
                        AgentCrashRecoveryTest().exercise(
                            File(args[0]),
                            AgentCheckout.valueOf(args[1]),
                            args[2] == "produce",
                        )
                    }
                } finally {
                    Dispatchers.resetMain()
                    dispatcher.close()
                }
            }
    }
}

private class CrashTransport(
    private val project: File,
) : AcpTransport {
    private val inbound = Channel<String>(Channel.UNLIMITED)
    override val incoming = inbound.receiveAsFlow()

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        when (request["method"]?.jsonPrimitive?.content) {
            "initialize" -> {
                reply(request, """{"protocolVersion":1,"agentCapabilities":{"loadSession":true}}""")
            }

            "session/new" -> {
                reply(request, """{"sessionId":"crash-session"}""")
            }

            "session/load" -> {
                reply(request, "{}")
            }

            "session/prompt" -> {
                val file = File(project, "Reader.kt")
                file.writeText("new")
                val path = AcpConnection.defaultJson.encodeToString(file.path)
                inbound.send(
                    """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"crash-session","update":""" +
                        """{"sessionUpdate":"tool_call","toolCallId":"edit","title":"Edit","kind":"edit",""" +
                        """"status":"completed","content":[{"type":"diff","path":$path,""" +
                        """"oldText":"old","newText":"new"}]}}}""",
                )
                // No prompt reply: the turn must still be running when the JVM halts.
            }
        }
    }

    private suspend fun reply(
        request: JsonObject,
        result: String,
    ) {
        inbound.send("""{"jsonrpc":"2.0","id":${request["id"]},"result":$result}""")
    }

    override suspend fun close() {
        inbound.close()
    }
}
