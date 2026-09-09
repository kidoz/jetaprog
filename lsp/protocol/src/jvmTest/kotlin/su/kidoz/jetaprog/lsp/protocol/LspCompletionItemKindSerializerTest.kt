package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A completion item sent back for `completionItem/resolve` must carry its kind as the
 * number the protocol defines; the enum's generated serializer wrote it as a string.
 */
class LspCompletionItemKindSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun kindIsWrittenAsANumber() {
        val encoded =
            json.encodeToString(
                LspCompletionItem.serializer(),
                LspCompletionItem(label = "x", kind = LspCompletionItemKind.Class),
            )

        assertTrue(encoded.contains("\"kind\":7"), encoded)
    }

    @Test
    fun kindIsReadFromNumbersAndStringsAndUnknownValuesFallBackToText() {
        fun decode(kind: String) =
            json
                .decodeFromJsonElement<LspCompletionItem>(
                    json.parseToJsonElement("""{"label": "x", "kind": $kind}"""),
                ).kind

        assertEquals(LspCompletionItemKind.Class, decode("7"))
        assertEquals(LspCompletionItemKind.Class, decode("\"7\""))
        assertEquals(LspCompletionItemKind.Text, decode("99"))
    }
}
