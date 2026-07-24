package su.kidoz.jetaprog.plugins.kotlin.server

import kotlinx.coroutines.runBlocking
import su.kidoz.jetaprog.lsp.protocol.DidOpenTextDocumentParams
import su.kidoz.jetaprog.lsp.protocol.DocumentSymbolParams
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspSymbolKind
import su.kidoz.jetaprog.lsp.protocol.TextDocumentIdentifier
import su.kidoz.jetaprog.lsp.protocol.TextDocumentItem
import su.kidoz.jetaprog.lsp.protocol.TextDocumentPositionParams
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinEmbeddedServerTest {
    private lateinit var projectDir: File
    private lateinit var symbolIndex: KotlinSymbolIndex
    private lateinit var server: KotlinEmbeddedServer

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("kotlin-server-test").toFile()
        File(projectDir, "Greeter.kt").writeText(
            """
            package sample

            class Greeter {
                fun greet(name: String): String = "Hello, ${'$'}name"
            }
            """.trimIndent(),
        )
        File(projectDir, "Main.kt").writeText(
            """
            package sample

            fun main() {
                val greeter = Greeter()
                println(greeter.greet("world"))
            }
            """.trimIndent(),
        )
        symbolIndex = KotlinSymbolIndex()
        server = KotlinEmbeddedServer(symbolIndex)
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    private fun uriOf(fileName: String): String = "file://${File(projectDir, fileName).absolutePath}"

    @Test
    fun `definition resolves reference across files`() =
        runBlocking {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            // Cursor on "Greeter" in `val greeter = Greeter()` (line 3, column 19)
            val locations =
                server.definition(
                    TextDocumentPositionParams(
                        textDocument = TextDocumentIdentifier(uriOf("Main.kt")),
                        position = LspPosition(3, 19),
                    ),
                )

            assertEquals(1, locations.size)
            assertEquals(uriOf("Greeter.kt"), locations.first().uri)
            assertEquals(
                2,
                locations
                    .first()
                    .range.start.line,
            )
        }

    @Test
    fun `documentSymbol returns hierarchical outline and self-indexes`() =
        runBlocking {
            // No explicit indexing: the server should index the file on demand.
            val symbols =
                server.documentSymbol(
                    DocumentSymbolParams(textDocument = TextDocumentIdentifier(uriOf("Greeter.kt"))),
                )

            val greeter = symbols.single { it.name == "Greeter" }
            assertEquals(LspSymbolKind.Class, greeter.kind)
            val childNames = greeter.children.orEmpty().map { it.name }
            assertTrue("greet" in childNames, "expected greet nested under Greeter, got $childNames")
        }

    @Test
    fun `hover returns declaration preview with qualified name`() =
        runBlocking {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            // Cursor on the "Greeter" class name declaration (line 2, column 6)
            val hover =
                server.hover(
                    TextDocumentPositionParams(
                        textDocument = TextDocumentIdentifier(uriOf("Greeter.kt")),
                        position = LspPosition(2, 6),
                    ),
                )

            assertNotNull(hover, "expected hover for Greeter")
            assertTrue("sample.Greeter" in hover.contents.value, "expected fqName in hover: ${hover.contents.value}")
            assertTrue("class Greeter" in hover.contents.value, "expected declaration in hover")
        }

    @Test
    fun `documentHighlight marks all identifier occurrences in the file`() =
        runBlocking {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            // Cursor on "greeter" in `val greeter = Greeter()` (line 3, column 8)
            val highlights =
                server.documentHighlight(
                    TextDocumentPositionParams(
                        textDocument = TextDocumentIdentifier(uriOf("Main.kt")),
                        position = LspPosition(3, 8),
                    ),
                )

            // `greeter` appears on line 3 (declaration) and line 4 (call receiver);
            // the whole-word match must not include "Greeter".
            assertEquals(listOf(3, 4), highlights.map { it.range.start.line })
        }

    @Test
    fun `semantic analyzer resolves local variable definition`() =
        runBlocking {
            val stdlibPath =
                File(
                    Unit::class.java.protectionDomain.codeSource.location
                        .toURI(),
                ).absolutePath
            val analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })
            val semanticServer = KotlinEmbeddedServer(symbolIndex, analyzer)
            try {
                val uri = uriOf("Calc.kt")
                semanticServer.didOpen(
                    DidOpenTextDocumentParams(
                        textDocument =
                            TextDocumentItem(
                                uri = uri,
                                languageId = "kotlin",
                                version = 1,
                                text = "fun compute(): Int {\n    val answer = 41\n    return answer + 1\n}\n",
                            ),
                    ),
                )

                // Cursor on "answer" in `return answer + 1` — a local the index cannot resolve.
                val locations =
                    semanticServer.definition(
                        TextDocumentPositionParams(
                            textDocument = TextDocumentIdentifier(uri),
                            position = LspPosition(2, 11),
                        ),
                    )

                assertEquals(1, locations.size)
                assertEquals(uri, locations.first().uri)
                assertEquals(LspPosition(1, 8), locations.first().range.start)
            } finally {
                analyzer.dispose()
            }
        }

    @Test
    fun `didOpen keeps unsaved content for lookups`() =
        runBlocking {
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val uri = uriOf("Main.kt")

            server.didOpen(
                DidOpenTextDocumentParams(
                    textDocument =
                        TextDocumentItem(
                            uri = uri,
                            languageId = "kotlin",
                            version = 1,
                            text = "fun other() {}\nval x = Greeter()",
                        ),
                ),
            )

            // Definition resolved against the in-memory content, not the file on disk.
            val locations =
                server.definition(
                    TextDocumentPositionParams(
                        textDocument = TextDocumentIdentifier(uri),
                        position = LspPosition(1, 10),
                    ),
                )

            assertEquals(1, locations.size)
            assertEquals(uriOf("Greeter.kt"), locations.first().uri)
        }
}
