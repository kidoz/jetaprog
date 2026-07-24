package su.kidoz.jetaprog.plugins.kotlin.analysis

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSemanticReferencesTest {
    private val stdlibPath: String =
        File(
            Unit::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).absolutePath

    private val analyzer = KotlinSemanticAnalyzer(classpathProvider = { listOf(stdlibPath) })
    private val projectDir: File = createTempDirectory("kotlin-references-test").toFile()

    @AfterTest
    fun tearDown() {
        analyzer.dispose()
        projectDir.deleteRecursively()
    }

    private fun write(
        name: String,
        content: String,
    ): String = File(projectDir, name).apply { writeText(content) }.absolutePath

    @Test
    fun `finds uses of a declaration in the analyzed file`() {
        val text =
            """
            package sample

            class Counter {
                fun bump(): Int = 1
            }

            fun run(): Int {
                val c = Counter()
                return c.bump() + c.bump()
            }
            """.trimIndent()

        // Cursor on the `bump` declaration name.
        val offset = text.indexOf("bump")
        val references = analyzer.references(text, offset)

        assertEquals(1, references.count { it.isDeclaration }, "expected the declaration itself")
        val uses = references.filterNot { it.isDeclaration }
        assertEquals(2, uses.size, "expected both c.bump() calls, got $uses")
        assertTrue(uses.all { it.filePath == null }, "same-file references carry a null path")
    }

    @Test
    fun `finds uses in context files and ignores same-named unrelated symbols`() {
        val userPath =
            write(
                "User.kt",
                """
                package sample

                fun useBeta(b: Beta): Int = b.ping()

                class Decoy {
                    fun ping(): Int = 99
                }

                fun useDecoy(d: Decoy): Int = d.ping()
                """.trimIndent(),
            )

        val text =
            """
            package sample

            class Beta {
                fun ping(): Int = 2
            }
            """.trimIndent()

        // Cursor on Beta's `ping` declaration name.
        val offset = text.indexOf("ping")
        val references = analyzer.references(text, offset, listOf(userPath))

        val uses = references.filterNot { it.isDeclaration }
        assertEquals(1, uses.size, "expected only Beta.ping's use, not Decoy.ping's, got $uses")
        assertEquals(userPath, uses.single().filePath)
    }

    @Test
    fun `ignores occurrences in comments and strings`() {
        val text =
            """
            package sample

            class Widget

            // Widget mentioned in a comment
            val label: String = "Widget in a string"

            fun make(): Widget = Widget()
            """.trimIndent()

        // Cursor on the `Widget` class declaration name.
        val offset = text.indexOf("class Widget") + "class ".length
        val references = analyzer.references(text, offset)

        val uses = references.filterNot { it.isDeclaration }
        // The return type and the constructor call are real uses; the comment and
        // the string literal are not.
        assertEquals(2, uses.size, "expected the return type and constructor call only, got $uses")
        uses.forEach { use ->
            val snippet = text.substring(use.startOffset, use.endOffset)
            assertEquals("Widget", snippet)
        }
    }

    @Test
    fun `resolves from a use site as well as a declaration`() {
        val text =
            """
            package sample

            class Gadget

            fun build(): Gadget = Gadget()
            """.trimIndent()

        // Cursor on the `Gadget` return type — a use, not the declaration.
        val offset = text.indexOf("): Gadget") + 3
        val references = analyzer.references(text, offset)

        assertEquals(1, references.count { it.isDeclaration }, "declaration should be reported")
        assertEquals(2, references.count { !it.isDeclaration }, "both uses should be reported")
    }
}
