package su.kidoz.jetaprog.app.navigation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.SearchScope
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspPosition
import su.kidoz.jetaprog.lsp.protocol.LspRange
import su.kidoz.jetaprog.lsp.protocol.LspSymbolInformation
import su.kidoz.jetaprog.lsp.protocol.LspSymbolKind
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.support.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * External language servers answer file structure and symbol search through the
 * registry. Before this they were only asked for definitions and references, so a
 * running gopls or jdtls still left structure and Go to Symbol to the regex index.
 */
class DefaultNavigationServiceRegistrySymbolsTest {
    private val registry = mockk<LanguageRegistry>()
    private val service =
        DefaultNavigationService(
            lspClient = null,
            fileSystem = mockk<FileSystem>(relaxed = true),
            workspacePath = "/w",
            languageRegistryProvider = { registry },
        )

    private fun range(line: Int) = LspRange(LspPosition(line, 0), LspPosition(line, 10))

    @Test
    fun fileStructureComesFromTheRegistryWhenNoEmbeddedServerAnswers() =
        runTest {
            coEvery { registry.provideDocumentSymbols("go", "file:///w/main.go") } returns
                listOf(
                    LspDocumentSymbol(
                        name = "main",
                        kind = LspSymbolKind.Function,
                        range = range(3),
                        selectionRange = range(3),
                    ),
                )

            val structure = service.getFileStructure("/w/main.go")

            assertEquals(listOf("main"), structure.map { it.target.name })
            assertEquals(
                3,
                structure
                    .single()
                    .target.position.line,
            )
        }

    @Test
    fun symbolSearchIncludesRegistryResultsAndClassSearchFiltersByKind() =
        runTest {
            coEvery { registry.searchWorkspaceSymbols("Gre") } returns
                listOf(
                    LspSymbolInformation(
                        "Greeter",
                        LspSymbolKind.Class,
                        location = LspLocation("file:///w/A.java", range(1)),
                    ),
                    LspSymbolInformation(
                        "greet",
                        LspSymbolKind.Method,
                        location = LspLocation("file:///w/A.java", range(4)),
                    ),
                )

            val symbols = service.searchSymbols("Gre", SearchScope.PROJECT, limit = 10)
            val classes = service.searchClasses("Gre", SearchScope.PROJECT, limit = 10)

            assertEquals(setOf("Greeter", "greet"), symbols.map { it.target.name }.toSet())
            assertEquals(listOf("Greeter"), classes.map { it.target.name })
            assertEquals(NavigationSymbolKind.CLASS, classes.single().target.kind)
            assertEquals("/w/A.java", classes.single().target.filePath)
        }
}
