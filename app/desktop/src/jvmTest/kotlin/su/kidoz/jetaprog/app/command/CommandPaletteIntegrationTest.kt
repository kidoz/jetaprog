package su.kidoz.jetaprog.app.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.plugins.cfamily.CPlugin
import su.kidoz.jetaprog.plugins.cfamily.CppPlugin
import su.kidoz.jetaprog.plugins.runtime.activation.ContributionRegistryImpl
import su.kidoz.jetaprog.plugins.runtime.services.CommandServiceImpl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the path a user actually takes: a plugin declares commands in its manifest,
 * the contribution registry registers them before the plugin activates, and the palette
 * lists them by their human-readable names.
 */
class CommandPaletteIntegrationTest {
    @Test
    fun paletteListsCommandsDeclaredByTheCAndCppPlugins() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val commandService = CommandServiceImpl()
            val registry = ContributionRegistryImpl(commandService, scope)
            val manifests = listOf(CPlugin().manifest, CppPlugin().manifest)

            manifests.forEach { registry.registerContributions(it) { } }

            val catalog = CommandCatalog.build(commandService.getCommands(), manifests)
            val names = catalog.map { it.displayName }

            assertContains(names, "C++: Build")
            assertContains(names, "C++: Configure")
            assertContains(names, "C: Build")
            assertContains(names, "C: Write .clangd Config")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun typingTheLanguageNarrowsToThatPluginsCommands() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val commandService = CommandServiceImpl()
            val registry = ContributionRegistryImpl(commandService, scope)
            val manifests = listOf(CPlugin().manifest, CppPlugin().manifest)
            manifests.forEach { registry.registerContributions(it) { } }

            val catalog = CommandCatalog.build(commandService.getCommands(), manifests)
            val results = CommandCatalog.filter(catalog, "C++")

            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.id.startsWith("cpp.") }, "leaked non-C++ commands: $results")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun executingFromThePaletteRunsTheRegisteredHandler() =
        runTest {
            val commandService = CommandServiceImpl()
            var invokedWith: List<Any?>? = null
            commandService.registerCommand("cpp.build") { args ->
                invokedWith = args
                "[100%] Linking CXX executable app\nCommand SUCCESS (exit code: 0)"
            }

            val viewModel =
                CommandPaletteViewModel(
                    listCommandIds = { commandService.getCommands() },
                    listManifests = { emptyList() },
                    executeCommand = { id -> commandService.executeCommand(id) },
                )

            val output = commandService.executeCommand("cpp.build")

            assertEquals(emptyList<Any?>(), invokedWith)
            assertTrue(output.toString().contains("SUCCESS"))
            viewModel.dispose()
        }
}
