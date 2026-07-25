package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards against the `initialize` response failing to deserialize.
 *
 * Several capabilities are `boolean | Options` in the specification and servers mix the two
 * forms freely. When the model accepted only one form the whole response failed to parse and
 * the language server silently never started.
 */
class ServerCapabilitiesTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    /** Captured verbatim from Apple clangd 21.0.0. */
    private val clangdCapabilities: String =
        checkNotNull(javaClass.getResourceAsStream("/clangd-capabilities.json")) {
            "missing clangd-capabilities.json test resource"
        }.bufferedReader().use { it.readText() }

    @Test
    fun decodesRealClangdCapabilities() {
        val caps = json.decodeFromString<ServerCapabilities>(clangdCapabilities)

        // Object form collapses to true.
        assertEquals(true, caps.codeActionProvider)
        assertEquals(true, caps.documentRangeFormattingProvider)
        // Boolean form is preserved.
        assertEquals(true, caps.hoverProvider)
        assertEquals(true, caps.definitionProvider)
        assertEquals(true, caps.renameProvider)
    }

    @Test
    fun decodesSemanticTokensGivenAsAnObject() {
        val caps = json.decodeFromString<ServerCapabilities>(clangdCapabilities)

        assertEquals(true, caps.semanticTokensProvider?.full)
        assertEquals(false, caps.semanticTokensProvider?.range)
    }

    @Test
    fun decodesTextDocumentSyncWithBooleanSave() {
        val caps = json.decodeFromString<ServerCapabilities>(clangdCapabilities)
        val sync = assertNotNull(caps.textDocumentSync)

        assertEquals(2, sync.change)
        assertEquals(true, sync.openClose)
        // "save": true must not be rejected for not being an object.
        assertTrue(sync.save != null)
    }

    @Test
    fun decodesTextDocumentSyncGivenAsAPlainKind() {
        val caps = json.decodeFromString<ServerCapabilities>("""{"textDocumentSync":1}""")

        assertEquals(1, caps.textDocumentSync?.change)
    }

    @Test
    fun decodesSaveGivenAsAnObject() {
        val caps =
            json.decodeFromString<ServerCapabilities>(
                """{"textDocumentSync":{"save":{"includeText":true}}}""",
            )

        assertEquals(true, caps.textDocumentSync?.save?.includeText)
    }

    @Test
    fun capabilitiesDisabledAsFalseStayFalse() {
        val caps = json.decodeFromString<ServerCapabilities>("""{"hoverProvider":false}""")

        assertEquals(false, caps.hoverProvider)
    }

    @Test
    fun absentCapabilitiesStayNull() {
        val caps = json.decodeFromString<ServerCapabilities>("{}")

        assertEquals(null, caps.hoverProvider)
        assertEquals(null, caps.textDocumentSync)
    }

    @Test
    fun roundTripsThroughEncoding() {
        val caps = json.decodeFromString<ServerCapabilities>(clangdCapabilities)
        val reparsed = json.decodeFromString<ServerCapabilities>(json.encodeToString(caps))

        assertEquals(caps, reparsed)
    }
}
