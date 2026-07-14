package su.kidoz.jetaprog.dap.jvm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import su.kidoz.jetaprog.dap.client.DapClient
import su.kidoz.jetaprog.dap.protocol.DapEvent
import su.kidoz.jetaprog.dap.protocol.DapSource
import su.kidoz.jetaprog.dap.protocol.DapSourceBreakpoint
import su.kidoz.jetaprog.dap.protocol.InitializeRequestArguments
import su.kidoz.jetaprog.dap.protocol.LaunchRequestArguments
import su.kidoz.jetaprog.dap.protocol.StoppedEventBody
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.tools.ToolProvider
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmDebugAdapterIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun breakpointStopsProgramAndExposesVariables() {
        val programDir = compileTestProgram()
        val sourcePath = File(programDir, "TestProgram.java").absolutePath
        val jdwpPort = freePort()

        // A loopback socket pair stands in for the adapter's stdio streams;
        // piped streams are unsuitable because they bind to writer threads.
        val connectionServer = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(name = "test-adapter", isDaemon = true) {
            connectionServer.use { server ->
                val socket = server.accept()
                JvmDebugAdapter(DapServerConnection(socket.getInputStream(), socket.getOutputStream())).run()
            }
        }
        val clientSocket = Socket(InetAddress.getLoopbackAddress(), connectionServer.localPort)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = DapClient(clientSocket.getInputStream(), clientSocket.getOutputStream(), scope)
        client.start()

        val events = Channel<DapEvent>(Channel.UNLIMITED)
        scope.launch {
            client.events.collect { event ->
                println("DAP event: ${event.event} ${event.body}")
                events.send(event)
            }
        }

        try {
            runBlocking {
                client.initialize(InitializeRequestArguments(adapterID = "jetaprog-jvm")).getOrThrow()

                client
                    .launch(
                        LaunchRequestArguments(
                            program = javaBinary(),
                            args =
                                listOf(
                                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$jdwpPort",
                                    "-cp",
                                    programDir.absolutePath,
                                    "TestProgram",
                                ),
                            cwd = programDir.absolutePath,
                            attachPort = jdwpPort,
                            attachTimeoutMs = ATTACH_TIMEOUT_MS,
                            sourceRoots = listOf(programDir.absolutePath),
                        ),
                    ).getOrThrow()

                awaitEvent(events, "initialized")

                client
                    .setBreakpoints(
                        source = DapSource(name = "TestProgram.java", path = sourcePath),
                        breakpoints = listOf(DapSourceBreakpoint(line = BREAKPOINT_LINE)),
                    ).getOrThrow()

                client.configurationDone().getOrThrow()

                val stopped = awaitEvent(events, "stopped")
                val stoppedBody = json.decodeFromJsonElement<StoppedEventBody>(assertNotNull(stopped.body))
                val threadId = assertNotNull(stoppedBody.threadId)

                val frames = client.stackTrace(threadId).getOrThrow()
                val topFrame = frames.first()
                assertEquals(BREAKPOINT_LINE, topFrame.line)
                assertEquals(sourcePath, topFrame.source?.path)
                assertTrue(topFrame.name.contains("TestProgram.main"))

                val scopes = client.scopes(topFrame.id).getOrThrow()
                val variables = client.variables(scopes.first().variablesReference).getOrThrow()
                val counter = variables.first { it.name == "counter" }
                assertEquals("41", counter.value)

                client.continueExecution(threadId).getOrThrow()
                awaitEvent(events, "exited")
            }
        } finally {
            runBlocking {
                runCatching { client.disconnect(terminateDebuggee = true) }
            }
            client.dispose()
            scope.cancel()
            runCatching { clientSocket.close() }
        }
    }

    private suspend fun awaitEvent(
        events: Channel<DapEvent>,
        name: String,
    ): DapEvent =
        withTimeout(EVENT_TIMEOUT_MS) {
            while (true) {
                val event = events.receive()
                if (event.event == name) return@withTimeout event
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

    private fun compileTestProgram(): File {
        val directory = createTempDirectory("jvm-debug-adapter-test").toFile()
        val source = File(directory, "TestProgram.java")
        source.writeText(
            """
            public class TestProgram {
                public static void main(String[] args) {
                    int counter = 41;
                    counter = counter + 1;
                    System.out.println("counter=" + counter);
                }
            }
            """.trimIndent(),
        )

        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "JDK compiler required")
        val result =
            compiler.run(
                null,
                null,
                null,
                "-g",
                "-d",
                directory.absolutePath,
                source.absolutePath,
            )
        assertEquals(0, result, "Compilation of the test program failed")
        return directory
    }

    private fun javaBinary(): String = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        private const val BREAKPOINT_LINE = 4
        private const val ATTACH_TIMEOUT_MS = 60_000L
        private const val EVENT_TIMEOUT_MS = 60_000L
    }
}
