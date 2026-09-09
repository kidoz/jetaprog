package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Definition results come as a location, a list of locations, or a list of location
 * links. The client advertises link support, so rust-analyzer and clangd answer with
 * links, which used to fail decoding and show "No declaration found".
 */
class LspLocationDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String) =
        json.decodeFromJsonElement(LspLocationsResultSerializer, json.parseToJsonElement(text))

    @Test
    fun locationLinksLandOnTheTargetSelectionRange() {
        val locations =
            decode(
                """
                [{"targetUri": "file:///w/A.rs",
                  "targetRange": {"start": {"line": 3, "character": 0}, "end": {"line": 9, "character": 1}},
                  "targetSelectionRange": {"start": {"line": 3, "character": 7}, "end": {"line": 3, "character": 12}}}]
                """.trimIndent(),
            )

        assertEquals("file:///w/A.rs", locations.single().uri)
        assertEquals(LspPosition(3, 7), locations.single().range.start)
    }

    @Test
    fun singleLocationAndLocationArraysStillDecode() {
        val single =
            decode(
                """{"uri": "file:///w/A.kt", "range": {"start": {"line": 1, "character": 2}, "end": {"line": 1, "character": 5}}}""",
            )
        val array =
            decode(
                """[{"uri": "file:///w/A.kt", "range": {"start": {"line": 1, "character": 2}, "end": {"line": 1, "character": 5}}},
                    {"uri": "file:///w/B.kt", "range": {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 1}}}]""",
            )

        assertEquals(1, single.size)
        assertEquals(listOf("file:///w/A.kt", "file:///w/B.kt"), array.map { it.uri })
        assertEquals(emptyList(), decode("null"))
    }
}
