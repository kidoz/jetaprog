package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.common.text.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Index-backed navigation used to build every target as line 0 with the character
 * offset in the column field, so Go to Class, file structure and the definition
 * fallback all landed on the first line for non-Kotlin languages.
 */
class IndexedSymbolPositionTest {
    private val source =
        listOf(
            "package sample;",
            "",
            "public class Greeter {",
            "    public String greet(String name) { return name; }",
            "}",
        ).joinToString("\n")

    @Test
    fun `extractor records the line and column of each symbol name`() {
        val symbols = JavaSymbolExtractor().extractSymbols(source, "/workspace/Greeter.java")
        val byName = symbols.associateBy { it.name }

        val greeter = byName.getValue("Greeter")
        assertEquals(2, greeter.line)
        assertEquals("public class ".length, greeter.column)
        assertEquals(source.indexOf("Greeter"), greeter.offset)

        val greet = byName.getValue("greet")
        assertEquals(3, greet.line)
        assertEquals("    public String ".length, greet.column)
    }

    @Test
    fun `navigation target points at the symbol name, not line one`() {
        val symbols = JavaSymbolExtractor().extractSymbols(source, "/workspace/Greeter.java")
        val greet = symbols.first { it.name == "greet" }

        assertEquals(TextPosition(line = 3, column = "    public String ".length), greet.toNavigationTarget().position)
    }
}
