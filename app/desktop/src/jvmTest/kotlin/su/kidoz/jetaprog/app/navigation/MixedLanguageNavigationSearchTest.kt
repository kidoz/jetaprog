package su.kidoz.jetaprog.app.navigation

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.SearchCategory
import su.kidoz.jetaprog.editor.navigation.index.InMemorySymbolIndex
import su.kidoz.jetaprog.editor.navigation.index.IndexedNavigationService
import su.kidoz.jetaprog.editor.navigation.index.JavaSymbolExtractor
import su.kidoz.jetaprog.editor.navigation.index.SymbolIndexer
import su.kidoz.jetaprog.editor.navigation.index.TypeScriptSymbolExtractor
import su.kidoz.jetaprog.lsp.server.DefaultServerRegistry
import su.kidoz.jetaprog.lsp.server.EmbeddedServerConfig
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.server.KotlinEmbeddedServer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises mixed-language search through the same service layers used by ProjectSession. */
class MixedLanguageNavigationSearchTest {
    @Test
    fun kotlinProviderResultsDoNotHideJavaAndTypeScriptIndexResults() =
        runTest {
            val root = createTempDirectory("mixed-navigation").toFile()
            val registry = DefaultServerRegistry(EmbeddedServerConfig(rootUri = "file://${root.absolutePath}"))
            try {
                File(root, "KotlinGreeter.kt").writeText("class KotlinGreeter {\n    fun greetFromKotlin() = Unit\n}")
                File(
                    root,
                    "JavaGreeter.java",
                ).writeText("public class JavaGreeter {\n    public void greetFromJava() {}\n}")
                File(
                    root,
                    "TypeScriptGreeter.ts",
                ).writeText("export class TypeScriptGreeter {}\nexport function greetFromTypeScript() {}")
                val kotlinIndex = KotlinSymbolIndex()
                kotlinIndex.indexDirectory(root.absolutePath)
                registry.registerServerFactory("kotlin") { KotlinEmbeddedServer(kotlinIndex) }

                val workspaceIndex = InMemorySymbolIndex()
                val indexer =
                    SymbolIndexer(workspaceIndex).apply {
                        registerExtractor(JavaSymbolExtractor())
                        registerExtractor(TypeScriptSymbolExtractor())
                    }
                WorkspaceSymbolIndexService(indexer).indexWorkspace(root.absolutePath)
                val fileSystem = JvmFileSystem()
                val service =
                    IndexedNavigationService(
                        symbolIndex = workspaceIndex,
                        lspDelegate =
                            KotlinIndexNavigationService(
                                delegate =
                                    DefaultNavigationService(
                                        lspClient = null,
                                        fileSystem = fileSystem,
                                        embeddedServerRegistry = registry,
                                        workspacePath = root.absolutePath,
                                    ),
                                symbolIndex = kotlinIndex,
                                fileSystem = fileSystem,
                                workspacePath = root.absolutePath,
                            ),
                    )

                val classes = setOf("KotlinGreeter", "JavaGreeter", "TypeScriptGreeter")
                assertEquals(classes, service.searchClasses("Greeter").map { it.target.name }.toSet())
                assertEquals(
                    setOf("greetFromKotlin", "greetFromJava", "greetFromTypeScript"),
                    service.searchSymbols("greetFrom").map { it.target.name }.toSet(),
                )
                val everywhere = service.searchEverywhere("Greeter")
                for (category in listOf(SearchCategory.CLASSES, SearchCategory.SYMBOLS)) {
                    assertEquals(
                        classes,
                        everywhere
                            .getValue(category)
                            .filter { it.target.kind == NavigationSymbolKind.CLASS }
                            .map { it.target.name }
                            .toSet(),
                    )
                }
            } finally {
                registry.dispose()
                root.deleteRecursively()
            }
        }
}
