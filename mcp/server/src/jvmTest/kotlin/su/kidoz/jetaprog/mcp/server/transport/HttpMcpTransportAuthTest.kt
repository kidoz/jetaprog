package su.kidoz.jetaprog.mcp.server.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals

private const val READY_ATTEMPTS = 30
private const val READY_DELAY_MILLIS = 100L
private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val HTTP_UNAUTHORIZED = 401

class HttpMcpTransportAuthTest {
    @Test
    fun rejectsRequestsWithoutTheToken() =
        runTest {
            withTransport(token = "s3cret") { port ->
                assertEquals(HTTP_UNAUTHORIZED, post(port, token = null))
                assertEquals(HTTP_UNAUTHORIZED, post(port, token = "wrong"))
            }
        }

    @Test
    fun acceptsRequestsWithTheToken() =
        runTest {
            withTransport(token = "s3cret") { port ->
                assertEquals(HTTP_OK, post(port, token = "s3cret"))
            }
        }

    @Test
    fun rejectsBrowserOriginatedRequests() =
        runTest {
            withTransport(token = null) { port ->
                assertEquals(HTTP_FORBIDDEN, post(port, token = null, origin = "http://evil.example"))
                assertEquals(HTTP_OK, post(port, token = null))
            }
        }

    private suspend fun withTransport(
        token: String?,
        block: suspend (Int) -> Unit,
    ) {
        val port = ServerSocket(0).use { it.localPort }
        val transport = HttpMcpTransport(host = "127.0.0.1", port = port, authToken = token)
        transport.start { request -> buildJsonObject { put("echo", request.toString()) } }
        try {
            awaitReady(port, token)
            block(port)
        } finally {
            transport.stop()
        }
    }

    private suspend fun awaitReady(
        port: Int,
        token: String?,
    ) {
        repeat(READY_ATTEMPTS) {
            if (runCatching { post(port, token) }.isSuccess) return
            delay(READY_DELAY_MILLIS)
        }
    }

    /**
     * Issues the request over a raw socket: `HttpURLConnection` silently drops an
     * `Origin` header, which the transport's rebinding check depends on.
     */
    private fun post(
        port: Int,
        token: String?,
        origin: String? = null,
    ): Int =
        Socket("127.0.0.1", port).use { socket ->
            val headers =
                buildList {
                    add("POST /mcp HTTP/1.1")
                    add("Host: 127.0.0.1:$port")
                    add("Content-Type: application/json")
                    add("Content-Length: ${REQUEST_BODY.toByteArray().size}")
                    token?.let { add("Authorization: Bearer $it") }
                    origin?.let { add("Origin: $it") }
                    add("Connection: close")
                }
            socket.getOutputStream().apply {
                write((headers.joinToString("\r\n") + "\r\n\r\n" + REQUEST_BODY).toByteArray())
                flush()
            }
            val statusLine =
                socket
                    .getInputStream()
                    .bufferedReader()
                    .readLine()
                    ?: error("No response from transport")
            statusLine.split(' ')[1].toInt()
        }

    private companion object {
        const val REQUEST_BODY: String = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
    }
}
