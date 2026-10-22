package su.kidoz.jetaprog.acp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcpSessionModelTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun newSessionResponseDecodesModels() {
        val response =
            json.decodeFromString(
                NewSessionResponse.serializer(),
                """
                {
                  "sessionId": "session-1",
                  "models": {
                    "availableModels": [
                      {"modelId": "gpt-6.1-sol[high]", "name": "6.1 Sol (high)"}
                    ],
                    "currentModelId": "gpt-6.1-sol[high]"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("session-1", response.sessionId)
        assertEquals("gpt-6.1-sol[high]", response.models?.currentModelId)
        assertEquals(
            "6.1 Sol (high)",
            response.models
                ?.availableModels
                ?.single()
                ?.name,
        )
    }

    @Test
    fun newSessionResponseWithoutModelsStaysCompatible() {
        val response =
            json.decodeFromString(
                NewSessionResponse.serializer(),
                """{"sessionId":"session-1"}""",
            )

        assertNull(response.models)
    }

    @Test
    fun loadSessionResponseDecodesModels() {
        val response =
            json.decodeFromString(
                LoadSessionResponse.serializer(),
                """
                {
                  "models": {
                    "availableModels": [{"modelId": "m1", "name": "Model One"}],
                    "currentModelId": "m1"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("m1", response.models?.currentModelId)
        assertEquals(
            "Model One",
            response.models
                ?.availableModels
                ?.single()
                ?.name,
        )
    }

    @Test
    fun setModelRequestUsesTheAcpWireShape() {
        val encoded =
            json.encodeToString(
                SetSessionModelRequest.serializer(),
                SetSessionModelRequest(sessionId = "session-1", modelId = "m1"),
            )

        assertEquals(
            json.parseToJsonElement("""{"sessionId":"session-1","modelId":"m1"}"""),
            json.parseToJsonElement(encoded),
        )
    }
}
