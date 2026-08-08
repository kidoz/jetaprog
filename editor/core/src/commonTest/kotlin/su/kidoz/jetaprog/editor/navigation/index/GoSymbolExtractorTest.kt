package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoSymbolExtractorTest {
    @Test
    fun `extracts Go declarations without duplicate type aliases`() {
        val source =
            """
            package sample

            type User struct {}
            type Loader interface {}
            type UserID = string
            func Load() {}
            func (user User) Name() string { return "" }
            const DefaultName = "guest"
            var Current User
            """.trimIndent()

        val symbols = GoSymbolExtractor().extractSymbols(source, "/workspace/sample.go")
        val symbolsByName = symbols.groupBy { it.name }

        assertEquals(listOf(NavigationSymbolKind.STRUCT), symbolsByName.getValue("User").map { it.kind })
        assertEquals(listOf(NavigationSymbolKind.INTERFACE), symbolsByName.getValue("Loader").map { it.kind })
        assertEquals(NavigationSymbolKind.TYPE_ALIAS, symbolsByName.getValue("UserID").single().kind)
        assertEquals(NavigationSymbolKind.FUNCTION, symbolsByName.getValue("Load").single().kind)
        assertEquals(NavigationSymbolKind.METHOD, symbolsByName.getValue("Name").single().kind)
        assertEquals(NavigationSymbolKind.CONSTANT, symbolsByName.getValue("DefaultName").single().kind)
        assertEquals(NavigationSymbolKind.VARIABLE, symbolsByName.getValue("Current").single().kind)
        assertTrue(symbols.all { it.languageId == "go" })
    }
}
