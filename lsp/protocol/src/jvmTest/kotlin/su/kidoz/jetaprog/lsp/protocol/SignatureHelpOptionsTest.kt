package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

/** The signature help trigger characters a server advertises were not modelled at all. */
class SignatureHelpOptionsTest {
    @Test
    fun signatureHelpTriggersAreDecodedFromServerCapabilities() {
        val json = Json { ignoreUnknownKeys = true }
        val capabilities =
            json.decodeFromJsonElement<ServerCapabilities>(
                json.parseToJsonElement(
                    """{"signatureHelpProvider": {"triggerCharacters": ["(", ","], "retriggerCharacters": [")"]}}""",
                ),
            )

        assertEquals(listOf("(", ","), capabilities.signatureHelpProvider?.triggerCharacters)
        assertEquals(listOf(")"), capabilities.signatureHelpProvider?.retriggerCharacters)
    }
}
