package su.kidoz.jetaprog.app.navigation

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationHistory
import su.kidoz.jetaprog.editor.navigation.NavigationService
import su.kidoz.jetaprog.editor.navigation.index.InMemorySymbolIndex
import su.kidoz.jetaprog.editor.navigation.index.IndexedNavigationService
import su.kidoz.jetaprog.editor.navigation.index.PrologSymbolExtractor
import su.kidoz.jetaprog.editor.navigation.index.SymbolIndexer
import su.kidoz.jetaprog.lsp.server.DefaultServerRegistry
import su.kidoz.jetaprog.lsp.server.EmbeddedServerConfig
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Go to Declaration on a Prolog call goes through the whole production chain:
 * generic index → Kotlin layer → default service (no server, no providers) and
 * back to the index fallback. Prolog has no language server, so this is the
 * only path that can answer.
 */
class PrologNavigationChainTest {
    private lateinit var projectDir: File
    private lateinit var file: File
    private lateinit var serverRegistry: DefaultServerRegistry
    private lateinit var service: NavigationService

    private val source =
        """
        % Natural language demo.

        :- initialization(main).

        main :-
            demo_statement("every cat chases a mouse"),
            demo_statement("a dog sleeps"),
            demo_question("who chases a mouse?").

        % A declarative sentence: parse it, show the tree and the logical form.

        demo_statement(Text) :-
            tokenize(Text, Words),
            show_text('statement:', Text).

        demo_question(Text) :-
            tokenize(Text, Words).

        tokenize(Text, Words) :- split_string(Text, " ", "", Words).
        show_text(Label, Text) :- format("~w ~w~n", [Label, Text]).
        """.trimIndent()

    @BeforeTest
    fun setUp() =
        runTest {
            projectDir = createTempDirectory("prolog-nav").toFile()
            file = File(projectDir, "natural_language.pl").apply { writeText(source) }
            val fileSystem = JvmFileSystem()
            val root = projectDir.absolutePath
            serverRegistry = DefaultServerRegistry(EmbeddedServerConfig(rootUri = "file://$root"))

            val workspaceIndex = InMemorySymbolIndex()
            val indexer = SymbolIndexer(workspaceIndex).apply { registerExtractor(PrologSymbolExtractor()) }
            WorkspaceSymbolIndexService(indexer).indexWorkspace(root)

            val history = NavigationHistory()
            service =
                IndexedNavigationService(
                    symbolIndex = workspaceIndex,
                    history = history,
                    lspDelegate =
                        KotlinIndexNavigationService(
                            delegate =
                                DefaultNavigationService(
                                    lspClient = null,
                                    fileSystem = fileSystem,
                                    embeddedServerRegistry = serverRegistry,
                                    workspacePath = root,
                                    history = history,
                                ),
                            symbolIndex = KotlinSymbolIndex(),
                            fileSystem = fileSystem,
                            workspacePath = root,
                        ),
                    fileContentProvider = DiskFileContentProvider(),
                )
        }

    @AfterTest
    fun tearDown() {
        serverRegistry.dispose()
        projectDir.deleteRecursively()
    }

    @Test
    fun `go to declaration from a call jumps to the first clause`() =
        runTest {
            val callLine = source.lines().indexOfFirst { it.contains("demo_statement(\"every") }
            val column = source.lines()[callLine].indexOf("demo_statement") + 3

            val target = service.getDefinition(file.absolutePath, TextPosition(callLine, column))

            assertNotNull(target, "expected the fallback to resolve demo_statement")
            assertEquals("demo_statement", target.name)
            assertEquals(file.absolutePath, target.filePath)
            assertEquals(source.lines().indexOfFirst { it.startsWith("demo_statement(Text)") }, target.position.line)
            assertEquals(0, target.position.column)
        }

    @Test
    fun `structure and usages come from the index`() =
        runTest {
            val structure = service.getFileStructure(file.absolutePath)
            assertEquals(
                listOf("main", "demo_statement", "demo_question", "tokenize", "show_text"),
                structure.map { it.target.name },
            )

            val declarationLine = source.lines().indexOfFirst { it.startsWith("demo_statement(Text)") }
            val usages = service.findUsages(file.absolutePath, TextPosition(declarationLine, 2))
            assertNotNull(usages)
            assertTrue(usages.totalCount == 3, "expected the declaration and two calls, got ${usages.totalCount}")
        }
}
