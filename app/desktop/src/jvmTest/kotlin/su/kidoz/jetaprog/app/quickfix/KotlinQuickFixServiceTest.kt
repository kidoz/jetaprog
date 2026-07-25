package su.kidoz.jetaprog.app.quickfix

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.quickfix.applyReplacements
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinQuickFixServiceTest {
    private lateinit var projectDir: File
    private lateinit var symbolIndex: KotlinSymbolIndex
    private lateinit var service: KotlinQuickFixService

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("quickfix-test").toFile()
        symbolIndex = KotlinSymbolIndex()
        service = KotlinQuickFixService(symbolIndex)
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    private fun write(
        name: String,
        content: String,
    ): File = File(projectDir, name).apply { writeText(content) }

    /** Indexes a `sample.Widget` declaration the fixes can import. */
    private suspend fun indexWidget() {
        write(
            "Widget.kt",
            """
            package sample.ui

            class Widget
            """.trimIndent(),
        )
        symbolIndex.indexDirectory(projectDir.absolutePath)
    }

    @Test
    fun `offers an import for an unresolved project type`() =
        runTest {
            indexWidget()
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")
            val content = usage.readText()

            // Caret on the `Widget` return type.
            val fixes = service.quickFixes(usage.absolutePath, content, TextPosition(2, 14))

            assertEquals(1, fixes.size, "expected one import fix, got ${fixes.map { it.title }}")
            assertEquals("Import sample.ui.Widget", fixes.single().title)

            val fixed = applyReplacements(content, fixes.single().edits)
            assertEquals(
                "package app\n\nimport sample.ui.Widget\n\nfun make(): Widget = Widget()\n",
                fixed,
            )
        }

    @Test
    fun `inserts into an existing import block in sorted order`() =
        runTest {
            indexWidget()
            val usage =
                write(
                    "Main.kt",
                    "package app\n\nimport java.io.File\nimport zzz.Last\n\nfun make(): Widget = Widget()\n",
                )
            val content = usage.readText()

            val fixes = service.quickFixes(usage.absolutePath, content, TextPosition(5, 14))

            val fixed = applyReplacements(content, fixes.single().edits)
            val imports = fixed.lines().filter { it.startsWith("import ") }
            assertEquals(
                listOf("import java.io.File", "import sample.ui.Widget", "import zzz.Last"),
                imports,
                "new import must land in lexicographic position",
            )
        }

    @Test
    fun `offers nothing when the type is already imported`() =
        runTest {
            indexWidget()
            val usage =
                write(
                    "Main.kt",
                    "package app\n\nimport sample.ui.Widget\n\nfun make(): Widget = Widget()\n",
                )

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(4, 14))

            assertTrue(fixes.isEmpty(), "already imported, got ${fixes.map { it.title }}")
        }

    @Test
    fun `offers nothing for a star import covering the package`() =
        runTest {
            indexWidget()
            val usage =
                write(
                    "Main.kt",
                    "package app\n\nimport sample.ui.*\n\nfun make(): Widget = Widget()\n",
                )

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(4, 14))

            assertTrue(fixes.isEmpty(), "star import already covers it, got ${fixes.map { it.title }}")
        }

    @Test
    fun `offers nothing for a type in the same package`() =
        runTest {
            write(
                "Widget.kt",
                """
                package app

                class Widget
                """.trimIndent(),
            )
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(2, 14))

            assertTrue(fixes.isEmpty(), "same package needs no import, got ${fixes.map { it.title }}")
        }

    @Test
    fun `offers one fix per candidate when the name is ambiguous`() =
        runTest {
            write("A.kt", "package one\n\nclass Widget\n")
            write("B.kt", "package two\n\nclass Widget\n")
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(2, 14))

            assertEquals(
                listOf("Import one.Widget", "Import two.Widget"),
                fixes.map { it.title },
                "both candidates should be offered, sorted",
            )
        }

    @Test
    fun `offers nothing away from an identifier`() =
        runTest {
            indexWidget()
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")

            // Column 0 of a blank line.
            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(1, 0))

            assertTrue(fixes.isEmpty())
        }

    @Test
    fun `ignores non-Kotlin files`() =
        runTest {
            indexWidget()
            val usage = write("notes.md", "Widget\n")

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(0, 2))

            assertTrue(fixes.isEmpty())
        }

    @Test
    fun `offers to remove an unused import under the caret`() =
        runTest {
            val usage =
                write(
                    "Main.kt",
                    "package app\n\nimport java.io.File\nimport kotlin.math.PI\n\nfun area(): Double = PI\n",
                )
            val content = usage.readText()

            // Caret on the `java.io.File` import, which nothing uses.
            val fixes = service.quickFixes(usage.absolutePath, content, TextPosition(2, 8))

            assertEquals(listOf("Remove unused import java.io.File"), fixes.map { it.title })
            val fixed = applyReplacements(content, fixes.single().edits)
            assertEquals(
                "package app\n\nimport kotlin.math.PI\n\nfun area(): Double = PI\n",
                fixed,
                "the used import and surrounding blank lines must survive",
            )
        }

    @Test
    fun `keeps an import that is still referenced`() =
        runTest {
            val usage =
                write(
                    "Main.kt",
                    "package app\n\nimport kotlin.math.PI\n\nfun area(): Double = PI\n",
                )

            val fixes = service.quickFixes(usage.absolutePath, usage.readText(), TextPosition(2, 8))

            assertTrue(fixes.isEmpty(), "PI is used, got ${fixes.map { it.title }}")
        }

    @Test
    fun `imports the accepted completion when exactly one candidate matches`() =
        runTest {
            indexWidget()
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")
            val content = usage.readText()

            val edit = assertNotNull(service.importEditFor(usage.absolutePath, content, "Widget"))

            assertEquals(
                "package app\n\nimport sample.ui.Widget\n\nfun make(): Widget = Widget()\n",
                applyReplacements(content, listOf(edit)),
            )
        }

    @Test
    fun `does not guess an import when the name is ambiguous`() =
        runTest {
            write("A.kt", "package one\n\nclass Widget\n")
            write("B.kt", "package two\n\nclass Widget\n")
            symbolIndex.indexDirectory(projectDir.absolutePath)
            val usage = write("Main.kt", "package app\n\nfun make(): Widget = Widget()\n")

            val edit = service.importEditFor(usage.absolutePath, usage.readText(), "Widget")

            assertEquals(null, edit, "ambiguous names must be left to Alt+Enter")
        }
}
