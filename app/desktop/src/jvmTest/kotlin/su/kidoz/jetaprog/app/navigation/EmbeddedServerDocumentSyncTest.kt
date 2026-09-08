package su.kidoz.jetaprog.app.navigation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.lsp.server.EmbeddedLspServer
import su.kidoz.jetaprog.lsp.server.EmbeddedServerRegistry
import kotlin.test.Test

/**
 * The embedded Kotlin server used to read every document from disk because nothing
 * forwarded the editor's open/change events to it.
 */
class EmbeddedServerDocumentSyncTest {
    private val server = mockk<EmbeddedLspServer>(relaxed = true)
    private val registry = mockk<EmbeddedServerRegistry>()
    private val uri = "file:///w/A.kt"

    @Test
    fun openAndChangeReachTheServerWithFullTextAndIncreasingVersions() =
        runTest {
            coEvery { registry.getServer("kotlin") } returns server
            val sync = EmbeddedServerDocumentSync(registry)

            sync.documentOpened(uri, "kotlin", "fun a() {}")
            sync.documentChanged(uri, "kotlin", "fun a() {}\nfun b() {}")
            sync.documentClosed(uri, "kotlin")

            coVerify(exactly = 1) {
                server.didOpen(match { it.textDocument.text == "fun a() {}" && it.textDocument.version == 1 })
            }
            coVerify(exactly = 1) {
                server.didChange(
                    match {
                        it.textDocument.version == 2 && it.contentChanges.single().text == "fun a() {}\nfun b() {}"
                    },
                )
            }
            coVerify(exactly = 1) { server.didClose(match { it.textDocument.uri == uri }) }
        }

    @Test
    fun languagesWithoutAnEmbeddedServerAreIgnored() =
        runTest {
            coEvery { registry.getServer("java") } returns null
            val sync = EmbeddedServerDocumentSync(registry)

            sync.documentOpened("file:///w/A.java", "java", "class A {}")
            sync.documentChanged("file:///w/A.java", "java", "class A { }")

            coVerify(exactly = 0) { server.didOpen(any()) }
            coVerify(exactly = 0) { server.didChange(any()) }
        }
}
