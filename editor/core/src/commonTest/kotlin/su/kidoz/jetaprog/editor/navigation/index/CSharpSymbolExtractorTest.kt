package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CSharpSymbolExtractorTest {
    @Test
    fun `extracts C sharp declarations for navigation fallback`() {
        val source =
            """
            namespace Sample;

            public record User(string Name);
            public interface IUserService {}
            public sealed class UserService
            {
                private readonly IUserStore store;
                public string DisplayName { get; init; }
                public async Task<User> LoadAsync(int id) => await store.LoadAsync(id);
            }
            public enum UserState { Active, Disabled }
            public readonly struct UserId {}
            """.trimIndent()

        val symbols = CSharpSymbolExtractor().extractSymbols(source, "/workspace/UserService.cs")
        val symbolsByName = symbols.groupBy { it.name }

        assertEquals(NavigationSymbolKind.NAMESPACE, symbolsByName.getValue("Sample").single().kind)
        assertEquals(NavigationSymbolKind.CLASS, symbolsByName.getValue("User").single().kind)
        assertEquals(NavigationSymbolKind.INTERFACE, symbolsByName.getValue("IUserService").single().kind)
        assertEquals(NavigationSymbolKind.CLASS, symbolsByName.getValue("UserService").single().kind)
        assertEquals(NavigationSymbolKind.FIELD, symbolsByName.getValue("store").single().kind)
        assertEquals(NavigationSymbolKind.PROPERTY, symbolsByName.getValue("DisplayName").single().kind)
        assertEquals(NavigationSymbolKind.METHOD, symbolsByName.getValue("LoadAsync").single().kind)
        assertEquals(NavigationSymbolKind.ENUM, symbolsByName.getValue("UserState").single().kind)
        assertEquals(NavigationSymbolKind.STRUCT, symbolsByName.getValue("UserId").single().kind)
        assertTrue(symbols.all { it.languageId == "csharp" })
    }
}
