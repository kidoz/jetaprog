package su.kidoz.jetaprog.app.refactoring

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KotlinRenameServiceTest {
    private lateinit var projectDir: File
    private lateinit var analyzer: KotlinSemanticAnalyzer
    private lateinit var symbolIndex: KotlinSymbolIndex
    private lateinit var service: KotlinRenameService

    private val stdlibPath: String =
        File(
            Unit::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).absolutePath

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("kotlin-rename-test").toFile()
        analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })
        symbolIndex = KotlinSymbolIndex()
        service =
            KotlinRenameService(
                fileSystem = JvmFileSystem(),
                symbolIndex = symbolIndex,
                semanticAnalyzer = analyzer,
                workspacePath = projectDir.absolutePath,
            )
    }

    @AfterTest
    fun tearDown() {
        analyzer.dispose()
        projectDir.deleteRecursively()
    }

    private fun write(
        name: String,
        content: String,
    ): File = File(projectDir, name).apply { writeText(content) }

    @Test
    fun `renames a declaration and its uses across files`() =
        runTest {
            val userFile =
                write(
                    "User.kt",
                    """
                    package sample

                    fun useBeta(b: Beta): Int = b.ping()

                    class Decoy {
                        fun ping(): Int = 99
                    }

                    fun useDecoy(d: Decoy): Int = d.ping()

                    // ping in a comment
                    val note: String = "ping in a string"
                    """.trimIndent(),
                )
            val betaFile =
                write(
                    "Beta.kt",
                    """
                    package sample

                    class Beta {
                        fun ping(): Int = 2
                    }
                    """.trimIndent(),
                )
            val betaContent = betaFile.readText()

            // Caret on Beta.ping's declaration name.
            val position = TextPosition(3, 8)
            val preparation = service.prepare(betaFile.absolutePath, position, betaContent)

            val ready = assertIs<RenamePreparation.Ready>(preparation)
            assertEquals("ping", ready.plan.symbolName)
            assertEquals(2, ready.plan.occurrenceCount, "declaration plus the single real use")

            val outcome = service.apply(ready.plan, "pong")

            assertIs<RenameOutcome.Applied>(outcome)
            assertEquals(2, outcome.occurrencesReplaced)

            val renamedBeta = betaFile.readText()
            assertTrue("fun pong(): Int = 2" in renamedBeta, "declaration renamed: $renamedBeta")

            val renamedUser = userFile.readText()
            assertTrue("b.pong()" in renamedUser, "use site renamed: $renamedUser")
            // Everything that merely shares the name must be untouched.
            assertTrue("fun ping(): Int = 99" in renamedUser, "Decoy.ping must not be renamed")
            assertTrue("d.ping()" in renamedUser, "Decoy call must not be renamed")
            assertTrue("// ping in a comment" in renamedUser, "comments must not be renamed")
            assertTrue("\"ping in a string\"" in renamedUser, "strings must not be renamed")
        }

    @Test
    fun `refuses to rename when the caret is not on a symbol`() =
        runTest {
            val file = write("Blank.kt", "package sample\n\n\nclass Thing\n")

            val preparation = service.prepare(file.absolutePath, TextPosition(2, 0), file.readText())

            val unavailable = assertIs<RenamePreparation.Unavailable>(preparation)
            assertTrue("caret" in unavailable.reason, "reason should guide the user: ${unavailable.reason}")
        }

    @Test
    fun `refuses to rename symbols declared outside the project`() =
        runTest {
            val file =
                write(
                    "Lib.kt",
                    """
                    package sample

                    fun size(list: List<String>): Int = list.count()
                    """.trimIndent(),
                )

            symbolIndex.indexDirectory(projectDir.absolutePath)

            // Caret on `count`, declared in the Kotlin standard library.
            val content = file.readText()
            val position = TextPosition(2, content.lines()[2].indexOf("count") + 1)
            val preparation = service.prepare(file.absolutePath, position, content)

            val unavailable = assertIs<RenamePreparation.Unavailable>(preparation)
            assertTrue(
                "outside this project" in unavailable.reason,
                "expected an out-of-project refusal, got: ${unavailable.reason}",
            )
        }

    @Test
    fun `rejects invalid new names without touching files`() =
        runTest {
            val file =
                write(
                    "Counter.kt",
                    """
                    package sample

                    class Counter {
                        fun bump(): Int = 1
                    }
                    """.trimIndent(),
                )
            val original = file.readText()
            val preparation = service.prepare(file.absolutePath, TextPosition(3, 8), original)
            val ready = assertIs<RenamePreparation.Ready>(preparation)

            val reserved = service.apply(ready.plan, "class")
            val malformed = service.apply(ready.plan, "2bump")
            val unchanged = service.apply(ready.plan, "bump")

            assertIs<RenameOutcome.Failed>(reserved)
            assertIs<RenameOutcome.Failed>(malformed)
            assertIs<RenameOutcome.Failed>(unchanged)
            assertEquals(original, file.readText(), "no file may be written for a rejected name")
        }

    @Test
    fun `aborts without writing when a file changed after preparation`() =
        runTest {
            val betaFile =
                write(
                    "Beta.kt",
                    """
                    package sample

                    class Beta {
                        fun ping(): Int = 2
                    }
                    """.trimIndent(),
                )
            val otherFile =
                write(
                    "Other.kt",
                    """
                    package sample

                    fun call(b: Beta): Int = b.ping()
                    """.trimIndent(),
                )
            val ready =
                assertIs<RenamePreparation.Ready>(
                    service.prepare(betaFile.absolutePath, TextPosition(3, 8), betaFile.readText()),
                )

            // Someone edits the other file, shifting its offsets.
            otherFile.writeText("package sample\n\n// inserted\n\nfun call(b: Beta): Int = b.ping()\n")
            val betaBefore = betaFile.readText()
            val otherBefore = otherFile.readText()

            val outcome = service.apply(ready.plan, "pong")

            assertIs<RenameOutcome.Failed>(outcome)
            assertEquals(betaBefore, betaFile.readText(), "origin file must be untouched")
            assertEquals(otherBefore, otherFile.readText(), "stale file must be untouched")
            assertFalse("pong" in betaFile.readText(), "no partial rename may be written")
        }
}
