package su.kidoz.jetaprog.mcp.server.transport

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpMcpTransportTest {
    @Test
    fun servesJsonRpcOverHttp() =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            val transport = HttpMcpTransport(host = "127.0.0.1", port = port)
            transport.start { request ->
                // Echo a minimal JSON-RPC result carrying back the request id.
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", request["id"]!!)
                    put("result", buildJsonObject { put("ok", true) })
                }
            }
            try {
                val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
                val (status, response) = postWithRetry("http://127.0.0.1:$port/mcp", body)
                assertEquals(HttpURLConnection.HTTP_OK, status)
                assertTrue(response.contains("\"ok\":true"), "Unexpected body: $response")
            } finally {
                transport.stop()
            }
        }

    private fun postWithRetry(
        url: String,
        body: String,
        attempts: Int = 20,
    ): Pair<Int, String> {
        var lastError: Exception? = null
        repeat(attempts) {
            try {
                return post(url, body)
            } catch (e: java.io.IOException) {
                lastError = e
                Thread.sleep(RETRY_DELAY_MILLIS)
            }
        }
        throw lastError ?: IllegalStateException("Server did not become ready")
    }

    private fun post(
        url: String,
        body: String,
    ): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        return status to text
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 100L
    }
}
