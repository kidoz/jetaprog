package su.kidoz.jetaprog.app.navigation

import su.kidoz.jetaprog.lsp.protocol.DidChangeTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidCloseTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DidSaveTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.TextDocumentContentChangeEvent
import su.kidoz.jetaprog.lsp.protocol.TextDocumentIdentifier
import su.kidoz.jetaprog.lsp.protocol.TextDocumentItem
import su.kidoz.jetaprog.lsp.protocol.VersionedTextDocumentIdentifier
import su.kidoz.jetaprog.lsp.server.EmbeddedServerRegistry
import su.kidoz.jetaprog.plugins.support.DocumentSyncListener

/**
 * Forwards the editor's document lifecycle to the embedded language servers.
 *
 * The embedded Kotlin server answered definition, hover and structure from the file
 * on disk because nothing ever told it what the editor held; unsaved edits shifted
 * every line it reported. Servers are created on first use for their language, the
 * same as a navigation query would.
 */
internal class EmbeddedServerDocumentSync(
    private val servers: EmbeddedServerRegistry,
) : DocumentSyncListener {
    private val versions = mutableMapOf<String, Int>()

    override suspend fun documentOpened(
        uri: String,
        languageId: String,
        content: String,
    ) {
        val server = servers.getServer(languageId) ?: return
        versions[uri] = 1
        server.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, languageId, version = 1, text = content)))
    }

    override suspend fun documentChanged(
        uri: String,
        languageId: String,
        content: String,
    ) {
        val server = servers.getServer(languageId) ?: return
        val version = (versions[uri] ?: 0) + 1
        versions[uri] = version
        server.didChange(
            DidChangeTextDocumentParams(
                textDocument = VersionedTextDocumentIdentifier(uri, version),
                contentChanges = listOf(TextDocumentContentChangeEvent(text = content)),
            ),
        )
    }

    override suspend fun documentSaved(
        uri: String,
        languageId: String,
        content: String?,
    ) {
        val server = servers.getServer(languageId) ?: return
        server.didSave(DidSaveTextDocumentParams(TextDocumentIdentifier(uri), content))
    }

    override suspend fun documentClosed(
        uri: String,
        languageId: String,
    ) {
        val server = servers.getServer(languageId) ?: return
        versions.remove(uri)
        server.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
    }
}
