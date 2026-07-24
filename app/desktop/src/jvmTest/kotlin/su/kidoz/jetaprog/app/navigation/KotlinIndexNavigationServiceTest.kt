package su.kidoz.jetaprog.app.navigation

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.UsageKind
import su.kidoz.jetaprog.lsp.server.DefaultServerRegistry
import su.kidoz.jetaprog.lsp.server.EmbeddedServerConfig
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer
import su.kidoz.jetaprog.plugins.kotlin.server.KotlinEmbeddedServer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinIndexNavigationServiceTest {
    private lateinit var projectDir: File
    private lateinit var symbolIndex: KotlinSymbolIndex
    private lateinit var serverRegistry: DefaultServerRegistry
    private lateinit var service: KotlinIndexNavigationService

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("nav-test").toFile()
        File(projectDir, "Greeter.kt").writeText(
            """
            package sample

            class Greeter {
                fun greet(name: String): String = "Hello, ${'$'}name"

                val greeting: String = "Hello"
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

        val fileSystem = JvmFileSystem()
        symbolIndex = KotlinSymbolIndex()

        // Symbol search is served through the embedded Kotlin server, exactly as
        // ProjectSession wires it in the running application.
        serverRegistry =
            DefaultServerRegistry(
                EmbeddedServerConfig(rootUri = "file://${projectDir.absolutePath}"),
            ).apply {
                registerServerFactory("kotlin") { KotlinEmbeddedServer(symbolIndex) }
            }

        service =
            KotlinIndexNavigationService(
                delegate =
                    DefaultNavigationService(
                        lspClient = null,
                        fileSystem = fileSystem,
                        embeddedServerRegistry = serverRegistry,
                        workspacePath = projectDir.absolutePath,
                    ),
                symbolIndex = symbolIndex,
                fileSystem = fileSystem,
                workspacePath = projectDir.absolutePath,
            )
    }

    @AfterTest
    fun tearDown() {
        serverRegistry.dispose()
        projectDir.deleteRecursively()
    }

    @Test
    fun `searchClasses finds indexed Kotlin class`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            val results = service.searchClasses("Greet")

            assertTrue(results.isNotEmpty(), "expected Greeter in class search results")
            assertEquals("Greeter", results.first().target.name)
            assertEquals(NavigationSymbolKind.CLASS, results.first().target.kind)
        }

    @Test
    fun `symbol search reports each symbol once`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            val results = service.searchClasses("Greeter")

            // Served solely by the embedded server's workspace/symbol; a second
            // in-process source would surface the same declaration twice.
            assertEquals(
                1,
                results.count { it.target.name == "Greeter" },
                "expected exactly one Greeter result, got ${results.map { it.target.qualifiedName }}",
            )
        }

    @Test
    fun `searchSymbols finds functions and properties`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)

            val names = service.searchSymbols("greet").map { it.target.name }

            assertTrue("greet" in names, "expected greet function in $names")
            assertTrue("greeting" in names, "expected greeting property in $names")
        }

    @Test
    fun `getFileStructure nests members under their class`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val greeterFile = File(projectDir, "Greeter.kt").absolutePath

            val structure = service.getFileStructure(greeterFile)

            val greeter = structure.single { it.target.name == "Greeter" }
            val childNames = greeter.children.map { it.target.name }
            assertTrue("greet" in childNames, "expected greet nested under Greeter, got $childNames")
        }

    @Test
    fun `getDefinition resolves reference across files`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val mainFile = File(projectDir, "Main.kt").absolutePath
            val greeterFile = File(projectDir, "Greeter.kt").absolutePath

            // Cursor on "Greeter" in `val greeter = Greeter()` (line 3, inside the constructor call)
            val target = service.getDefinition(mainFile, TextPosition(3, 19))

            assertNotNull(target, "expected a definition for Greeter reference")
            assertEquals(greeterFile, target.filePath)
            assertEquals("Greeter", target.name)
        }

    @Test
    fun `findUsages reports whole-word matches across the project`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val greeterFile = File(projectDir, "Greeter.kt").absolutePath

            // Cursor on the "Greeter" class name declaration (line 2, column 6)
            val result = service.findUsages(greeterFile, TextPosition(2, 6))

            assertNotNull(result, "expected usages for Greeter")
            assertEquals("Greeter", result.symbol.name)
            val files = result.groups.map { it.fileName }.toSet()
            assertTrue("Main.kt" in files, "expected a usage in Main.kt, got $files")
            assertTrue(result.totalCount >= 2, "expected declaration + usage, got ${result.totalCount}")
        }

    @Test
    fun `findUsages resolves semantically when an analyzer is available`() =
        runTest {
            File(projectDir, "Beta.kt").writeText(
                """
                package sample

                class Beta {
                    fun ping(): Int = 2
                }

                class Decoy {
                    fun ping(): Int = 99
                }

                fun useBeta(b: Beta): Int = b.ping()

                fun useDecoy(d: Decoy): Int = d.ping()

                // ping mentioned in a comment
                val note: String = "ping in a string"
                """.trimIndent(),
            )

            val stdlibPath =
                File(
                    Unit::class.java.protectionDomain.codeSource.location
                        .toURI(),
                ).absolutePath
            val analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })
            val fileSystem = JvmFileSystem()
            val semanticService =
                KotlinIndexNavigationService(
                    delegate =
                        DefaultNavigationService(
                            lspClient = null,
                            fileSystem = fileSystem,
                            embeddedServerRegistry = null,
                            workspacePath = projectDir.absolutePath,
                        ),
                    symbolIndex = symbolIndex,
                    fileSystem = fileSystem,
                    workspacePath = projectDir.absolutePath,
                    semanticAnalyzer = analyzer,
                )
            try {
                symbolIndex.indexDirectory(projectDir.absolutePath)
                val betaFile = File(projectDir, "Beta.kt").absolutePath

                // Cursor on Beta.ping's declaration name (line 3, column 8).
                val result = semanticService.findUsages(betaFile, TextPosition(3, 8))

                assertNotNull(result, "expected usages for Beta.ping")
                val usages = result.groups.flatMap { it.usages }
                // Declaration + b.ping(); NOT Decoy.ping, its call, the comment or the string.
                val contextLines = usages.map { it.contextLine }
                assertEquals(2, usages.size, "expected declaration and one real use, got $contextLines")
                assertEquals(1, usages.count { it.usageKind == UsageKind.DEFINITION })
                assertTrue(
                    usages.any { it.contextLine.contains("useBeta") },
                    "expected the useBeta call site, got ${usages.map { it.contextLine }}",
                )
                assertTrue(
                    usages.none { it.contextLine.contains("useDecoy") || it.contextLine.contains("in a string") },
                    "text-only false positives must be filtered, got ${usages.map { it.contextLine }}",
                )
                assertTrue(usages.all { it.contextLine.isNotBlank() }, "context lines must be populated")
            } finally {
                analyzer.dispose()
            }
        }

    @Test
    fun `getBreadcrumbs returns containing class and member`() =
        runTest {
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val greeterFile = File(projectDir, "Greeter.kt").absolutePath

            // Inside the greet function body (line 3)
            val crumbs = service.getBreadcrumbs(greeterFile, TextPosition(3, 10))

            val names = crumbs.map { it.name }
            assertEquals(listOf("Greeter.kt", "Greeter", "greet"), names)
        }
}
