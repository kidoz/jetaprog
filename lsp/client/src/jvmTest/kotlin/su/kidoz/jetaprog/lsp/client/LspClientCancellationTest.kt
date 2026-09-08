package su.kidoz.jetaprog.lsp.client

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.lsp.client.transport.LspTransport
import su.kidoz.jetaprog.lsp.protocol.CompletionParams
import su.kidoz.jetaprog.lsp.protocol.JsonRpcMessage
import su.kidoz.jetaprog.lsp.protocol.JsonRpcNotification
import su.kidoz.jetaprog.lsp.protocol.JsonRpcRequest
import su.kidoz.jetaprog.lsp.protocol.LspMethod
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.TextDocumentIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A request the caller no longer wants must not keep the server busy: the client used to
 * await the response forever and never told the server to stop.
 */
class LspClientCancellationTest {
    private class RecordingTransport : LspTransport {
        val sent = mutableListOf<JsonRpcMessage>()
        override val isConnected: Boolean = true
        override val incoming: Flow<JsonRpcMessage> = MutableSharedFlow()

        override suspend fun send(message: JsonRpcMessage) {
            sent += message
        }

        override suspend fun close() = Unit
    }

    private val params = CompletionParams(TextDocumentIdentifier("file:///tmp/a.kt"), LspPosition(0, 0))

    private fun RecordingTransport.cancelNotifications(): List<Int> =
        sent
            .filterIsInstance<JsonRpcNotification>()
            .filter { it.method == LspMethod.CANCEL_REQUEST }
            .map {
                it.params!!
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive.int
            }

    @Test
    fun cancellingTheCallerSendsCancelRequestToTheServer() =
        runBlocking {
            val transport = RecordingTransport()
            val client = LspClient(transport, LspClientConfig(serverName = "fake", rootUri = "file:///tmp"))

            val job = launch { client.completion(params) }
            while (transport.sent.isEmpty()) yield()
            val requestId = (transport.sent.single() as JsonRpcRequest).id

            job.cancelAndJoin()

            assertEquals(listOf(requestId), transport.cancelNotifications())
        }

    @Test
    fun aSilentServerTimesOutAndIsToldToStop() =
        runBlocking {
            val transport = RecordingTransport()
            val client =
                LspClient(
                    transport,
                    LspClientConfig(serverName = "fake", rootUri = "file:///tmp", requestTimeoutMillis = 50),
                )

            val result = client.completion(params)

            assertNull(result)
            val requestId = (transport.sent.first() as JsonRpcRequest).id
            assertEquals(listOf(requestId), transport.cancelNotifications())
        }
}
