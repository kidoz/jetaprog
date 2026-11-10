package su.kidoz.jetaprog.acp.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.acp.client.StdioAcpTransport
import su.kidoz.jetaprog.acp.client.StdioAgentConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcpStdioBackpressureTest {
    @Test
    fun closingUnblocksAWriterWhenTheAgentNeverReadsStdin(): Unit =
        runBlocking {
            val transport = transport("ignore-input")
            try {
                withTimeout(15_000) {
                    transport.start()
                    assertEquals("ready", transport.incoming.first())
                    val write = async { runCatching { transport.send("x".repeat(2_000_000)) } }
                    delay(100)
                    transport.close()
                    assertTrue(write.await().isFailure)
                    assertFalse(transport.isAlive)
                }
            } finally {
                transport.close()
            }
        }

    @Test
    fun oversizedUnterminatedFrameFailsWithoutBeingDelivered(): Unit =
        runBlocking {
            val transport = transport("oversized")
            try {
                withTimeout(15_000) {
                    transport.start()
                    val failure = runCatching { transport.incoming.toList() }.exceptionOrNull()
                    assertTrue(failure?.message?.contains("frame exceeded") == true)
                }
            } finally {
                transport.close()
            }
        }

    private fun transport(mode: String): StdioAcpTransport =
        StdioAcpTransport(
            StdioAgentConfig(
                listOf(
                    File(System.getProperty("java.home"), "bin/java").path,
                    "-cp",
                    checkNotNull(System.getProperty("jetaprog.acp.test.classpath")),
                    StdioBackpressureTestAgent::class.java.name,
                    mode,
                ),
            ),
        )
}
