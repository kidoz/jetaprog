package su.kidoz.jetaprog.app.command

import su.kidoz.jetaprog.plugins.api.CommandContribution
import su.kidoz.jetaprog.plugins.api.Contributions
import su.kidoz.jetaprog.plugins.api.PluginManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandCatalogTest {
    private fun manifest(
        id: String,
        vararg commands: Pair<String, String>,
        category: String? = null,
    ) = PluginManifest(
        id = id,
        name = id,
        version = "1.0.0",
        contributes =
            Contributions(
                commands = commands.map { (cmd, title) -> CommandContribution(cmd, title, category) },
            ),
    )

    @Test
    fun usesManifestTitlesAndCategories() {
        val commands =
            CommandCatalog.build(
                registeredIds = listOf("cpp.build"),
                manifests = listOf(manifest("cpp", "cpp.build" to "Build", category = "C++")),
            )

        val command = commands.single()
        assertEquals("Build", command.title)
        assertEquals("C++", command.category)
        assertEquals("C++: Build", command.displayName)
    }

    @Test
    fun derivesATitleForCommandsWithoutAManifestContribution() {
        val commands = CommandCatalog.build(listOf("cpp.writeClangdConfig"), emptyList())

        val command = commands.single()
        assertEquals("Write Clangd Config", command.title)
        assertEquals("cpp", command.category)
    }

    @Test
    fun listsEveryRegisteredCommandEvenWhenNoPluginDeclaredIt() {
        // Runtime-registered commands must still be reachable from the palette.
        val commands = CommandCatalog.build(listOf("cargo.build", "meson.setup", "c.test"), emptyList())
        assertEquals(listOf("c.test", "cargo.build", "meson.setup"), commands.map { it.id }.sorted())
    }

    @Test
    fun deduplicatesRepeatedIds() {
        val commands = CommandCatalog.build(listOf("c.build", "c.build"), emptyList())
        assertEquals(1, commands.size)
    }

    @Test
    fun emptyQueryReturnsEverything() {
        val commands = CommandCatalog.build(listOf("a.one", "b.two"), emptyList())
        assertEquals(commands, CommandCatalog.filter(commands, "   "))
    }

    @Test
    fun ranksPrefixMatchesAboveSubstringMatches() {
        val commands =
            CommandCatalog.build(
                registeredIds = listOf("cpp.build", "gradle.rebuildProject"),
                manifests =
                    listOf(
                        manifest("cpp", "cpp.build" to "Build", category = "C++"),
                        manifest("g", "gradle.rebuildProject" to "Rebuild Project", category = "Gradle"),
                    ),
            )

        val results = CommandCatalog.filter(commands, "build")
        assertEquals(2, results.size)
        assertEquals("cpp.build", results.first().id)
    }

    @Test
    fun matchesAbbreviationsAsSubsequences() {
        val commands =
            CommandCatalog.build(
                listOf("cpp.writeClangdConfig"),
                listOf(manifest("cpp", "cpp.writeClangdConfig" to "Write .clangd Config", category = "C++")),
            )

        assertEquals(1, CommandCatalog.filter(commands, "wcc").size)
    }

    @Test
    fun matchesOnTheRawCommandId() {
        val commands = CommandCatalog.build(listOf("meson.setup"), emptyList())
        assertEquals(1, CommandCatalog.filter(commands, "meson.set").size)
    }

    @Test
    fun rejectsQueriesThatMatchNothing() {
        val commands = CommandCatalog.build(listOf("cpp.build"), emptyList())
        assertTrue(CommandCatalog.filter(commands, "zzzz").isEmpty())
        assertNull(CommandCatalog.score(commands.single(), "zzzz"))
    }

    @Test
    fun exactMatchOutranksEverythingElse() {
        val command = PaletteCommand(id = "c.build", title = "Build", category = "C")
        val exact = assertNotNull(CommandCatalog.score(command, "C: Build"))
        val prefix = assertNotNull(CommandCatalog.score(command, "C:"))
        assertTrue(exact < prefix)
    }

    @Test
    fun humanizesCamelCaseLeafSegments() {
        assertEquals("Build", CommandCatalog.humanize("cpp.build"))
        assertEquals("Write Clangd Config", CommandCatalog.humanize("cpp.writeClangdConfig"))
        assertEquals("Setup", CommandCatalog.humanize("setup"))
    }

    @Test
    fun subsequenceMatchingIsOrderSensitive() {
        assertTrue(CommandCatalog.isSubsequence("c++: build", "cb"))
        assertTrue(!CommandCatalog.isSubsequence("c++: build", "bc"))
    }
}
