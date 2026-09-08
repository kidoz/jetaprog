package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Decodes the alternative arms of the completion, hover and signature unions the way
 * the client does (from a parsed element, not a string). Each form here is one a real
 * server sends; before the union serializers any of them failed the whole response.
 */
class LspUnionDecodingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private inline fun <reified T> decode(text: String): T = json.decodeFromJsonElement(json.parseToJsonElement(text))

    @Test
    fun completionItemAcceptsNumericKindStringDocumentationAndInsertReplaceEdit() {
        val item =
            decode<LspCompletionItem>(
                """
                {
                  "label": "Greeter",
                  "kind": 7,
                  "documentation": "A greeter.",
                  "textEdit": {
                    "newText": "com.example.Greeter",
                    "insert": {"start": {"line": 3, "character": 4}, "end": {"line": 3, "character": 6}},
                    "replace": {"start": {"line": 3, "character": 4}, "end": {"line": 3, "character": 9}}
                  }
                }
                """.trimIndent(),
            )

        assertEquals(LspCompletionItemKind.Class, item.kind)
        assertEquals(MarkupContent("plaintext", "A greeter."), item.documentation)
        val edit = item.textEdit!!
        assertEquals("com.example.Greeter", edit.newText)
        assertEquals(6, edit.insert.end.character)
        assertEquals(9, edit.replace.end.character)
    }

    @Test
    fun plainTextEditCoversBothModesAndUnknownKindIsDropped() {
        val item =
            decode<LspCompletionItem>(
                """
                {
                  "label": "x",
                  "kind": 99,
                  "documentation": {"kind": "markdown", "value": "**x**"},
                  "textEdit": {"newText": "x", "range": {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 1}}}
                }
                """.trimIndent(),
            )

        assertNull(item.kind)
        assertEquals("markdown", item.documentation?.kind)
        val edit = item.textEdit!!
        assertEquals(edit.insert, edit.replace)
    }

    @Test
    fun completionResultAcceptsBareItemArray() {
        val list =
            json.decodeFromJsonElement(
                LspCompletionResultSerializer,
                json.parseToJsonElement("""[{"label": "a"}, {"label": "b"}]"""),
            )

        assertFalse(list.isIncomplete)
        assertEquals(listOf("a", "b"), list.items.map { it.label })

        val wrapped =
            json.decodeFromJsonElement(
                LspCompletionResultSerializer,
                json.parseToJsonElement("""{"isIncomplete": true, "items": [{"label": "c"}]}"""),
            )
        assertEquals(true, wrapped.isIncomplete)
    }

    @Test
    fun parameterLabelAcceptsOffsetPair() {
        val signature =
            decode<LspSignatureInformation>(
                """
                {
                  "label": "greet(name: String, times: Int)",
                  "documentation": "Greets.",
                  "parameters": [{"label": [6, 18]}, {"label": "times: Int", "documentation": {"kind": "plaintext", "value": "n"}}]
                }
                """.trimIndent(),
            )

        val labels = signature.parameters!!.map { it.label.resolve(signature.label) }
        assertEquals(listOf("name: String", "times: Int"), labels)
        assertEquals("Greets.", signature.documentation?.value)
    }

    @Test
    fun hoverContentsAcceptMarkedStringForms() {
        val fromString = decode<LspHover>("""{"contents": "just text"}""")
        assertEquals(MarkupContent("markdown", "just text"), fromString.contents)

        val fromArray =
            decode<LspHover>(
                """{"contents": [{"language": "kotlin", "value": "fun a()"}, "Docs here"]}""",
            )
        assertEquals("markdown", fromArray.contents.kind)
        assertEquals("```kotlin\nfun a()\n```\n\nDocs here", fromArray.contents.value)

        val fromMarkup = decode<LspHover>("""{"contents": {"kind": "plaintext", "value": "p"}}""")
        assertEquals(MarkupContent("plaintext", "p"), fromMarkup.contents)
    }
}
