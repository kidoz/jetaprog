package su.kidoz.jetaprog.lsp.client

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.lsp.client.transport.StdioTransport
import su.kidoz.jetaprog.lsp.client.transport.TransportConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real [LspClient] against a real clangd process.
 *
 * A pure JSON test cannot catch a handshake that fails inside the client, which is how a
 * `boolean | Options` capability mismatch previously stopped clangd from ever starting
 * while clangd itself reported success. Skipped when clangd is not installed.
 */
class ClangdHandshakeTest {
    private fun clangdAvailable(): Boolean =
        runCatching {
            val process = ProcessBuilder("clangd", "--version").redirectErrorStream(true).start()
            process.waitFor() == 0
        }.getOrDefault(false)

    @Test
    fun completesTheInitializeHandshakeAndExposesCapabilities() =
        runBlocking {
            if (!clangdAvailable()) {
                println("clangd not on PATH - skipping the handshake test")
                return@runBlocking
            }

            val transport =
                StdioTransport(
                    TransportConfig.Stdio(
                        command = listOf("clangd", "--background-index=false", "--enable-config"),
                    ),
                )
            val client =
                LspClient(
                    transport,
                    LspClientConfig(serverName = "clangd", rootUri = "file:///tmp"),
                )

            try {
                transport.start()
                withTimeout(HANDSHAKE_TIMEOUT_MILLIS) { client.start() }

                assertTrue(client.isInitialized, "client reported no successful initialize")

                val capabilities = client.serverCapabilities
                assertTrue(capabilities != null, "capabilities were not decoded")

                // The fields that previously aborted the handshake.
                assertEquals(true, capabilities.codeActionProvider)
                assertEquals(true, capabilities.documentRangeFormattingProvider)
                assertTrue(capabilities.textDocumentSync?.save != null)
                assertEquals(true, capabilities.semanticTokensProvider?.full)

                // The features the C and C++ plugins register providers for.
                assertEquals(true, capabilities.hoverProvider)
                assertEquals(true, capabilities.definitionProvider)
                assertEquals(true, capabilities.referencesProvider)
                assertEquals(true, capabilities.documentFormattingProvider)
            } finally {
                runCatching { client.stop() }
            }
        }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MILLIS = 30_000L
    }
}
