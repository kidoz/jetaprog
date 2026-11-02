package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AcpBooleanConfigTest {
    private val json = AcpConnection.defaultJson

    @Test
    fun capabilityIsOptInAndUsesAnEmptyObject() {
        assertFalse("session" in json.encodeToJsonElement(ClientCapabilities()).jsonObject)
        val capabilities =
            ClientCapabilities(
                session = ClientSessionCapabilities(SessionConfigCapabilities(SessionConfigBooleanCapability)),
            )
        val session =
            json
                .encodeToJsonElement(capabilities)
                .jsonObject
                .getValue("session")
                .jsonObject
        assertEquals(JsonObject(emptyMap()), session.getValue("configOptions").jsonObject.getValue("boolean"))
    }

    @Test
    fun trueAndFalseHaveRequiredTypeAndRemainJsonBooleans() {
        for (value in listOf(true, false)) {
            val request = json.encodeToJsonElement(SetSessionBooleanConfigRequest("s1", "compact", value)).jsonObject
            assertEquals(JsonPrimitive("boolean"), request["type"])
            assertEquals(JsonPrimitive(value), request["value"])
        }
        val select = json.encodeToJsonElement(SetSessionConfigRequest("s1", "select", "false")).jsonObject
        assertEquals(JsonPrimitive("false"), select["value"])
        assertFalse("type" in select)
    }
}
