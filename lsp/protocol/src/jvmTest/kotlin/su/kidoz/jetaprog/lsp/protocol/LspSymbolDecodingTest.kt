package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Symbol responses come in two shapes each; a server answering with the flat shape
 * used to fail decoding and left structure and symbol search empty.
 */
class LspSymbolDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun documentSymbolsAcceptHierarchicalAndFlatForms() {
        val hierarchical =
            json.decodeFromJsonElement(
                LspDocumentSymbolResultSerializer,
                json.parseToJsonElement(
                    """
                    [{"name": "Greeter", "kind": 5,
                      "range": {"start": {"line": 1, "character": 0}, "end": {"line": 5, "character": 1}},
                      "selectionRange": {"start": {"line": 1, "character": 6}, "end": {"line": 1, "character": 13}},
                      "children": [{"name": "greet", "kind": 6,
                        "range": {"start": {"line": 2, "character": 4}, "end": {"line": 4, "character": 5}},
                        "selectionRange": {"start": {"line": 2, "character": 8}, "end": {"line": 2, "character": 13}}}]}]
                    """.trimIndent(),
                ),
            )
        assertEquals(
            "greet",
            hierarchical
                .single()
                .children
                ?.single()
                ?.name,
        )

        val flat =
            json.decodeFromJsonElement(
                LspDocumentSymbolResultSerializer,
                json.parseToJsonElement(
                    """
                    [{"name": "greet", "kind": 6, "containerName": "Greeter",
                      "location": {"uri": "file:///w/A.kt",
                        "range": {"start": {"line": 2, "character": 4}, "end": {"line": 4, "character": 5}}}}]
                    """.trimIndent(),
                ),
            )
        val symbol = flat.single()
        assertEquals("greet", symbol.name)
        assertEquals(LspSymbolKind.Method, symbol.kind)
        assertEquals(2, symbol.range.start.line)
        assertEquals("Greeter", symbol.detail)
    }

    @Test
    fun workspaceSymbolsAcceptLocationsWithoutARange() {
        val symbols =
            json.decodeFromJsonElement(
                LspWorkspaceSymbolResultSerializer,
                json.parseToJsonElement(
                    """
                    [{"name": "Greeter", "kind": 5, "location": {"uri": "file:///w/A.kt"}},
                     {"name": "Other", "kind": 5, "containerName": "pkg",
                      "location": {"uri": "file:///w/B.kt",
                        "range": {"start": {"line": 3, "character": 0}, "end": {"line": 3, "character": 5}}}}]
                    """.trimIndent(),
                ),
            )

        assertEquals(listOf("Greeter", "Other"), symbols.map { it.name })
        assertEquals(LspPosition(0, 0), symbols[0].location.range.start)
        assertEquals(
            3,
            symbols[1]
                .location.range.start.line,
        )
        assertEquals("pkg", symbols[1].containerName)
    }
}
