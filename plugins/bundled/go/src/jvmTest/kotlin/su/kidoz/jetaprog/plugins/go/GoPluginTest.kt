package su.kidoz.jetaprog.plugins.go

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class GoPluginTest {
    @Test
    fun `manifest contributes Go module files and toolchain commands`() {
        val manifest = GoPlugin().manifest
        val language = manifest.contributes.languages.first { it.id == "go" }
        val filenames =
            manifest.contributes.languages
                .flatMap { it.filenames }
                .toSet()

        assertEquals(GoPlugin.PLUGIN_ID, manifest.id)
        assertEquals(GoPlugin.PLUGIN_DESCRIPTION, manifest.description)
        assertEquals("go", language.id)
        assertContains(language.extensions, ".go")
        assertEquals(setOf("go.mod", "go.work", "go.sum"), filenames)
        assertContains(manifest.activationEvents, "onLanguage:go")
        assertContains(manifest.activationEvents, "workspaceContains:*.go")
        assertContains(manifest.activationEvents, "workspaceContains:go.mod")
        assertContains(manifest.activationEvents, "workspaceContains:go.work")
        assertEquals(GoPlugin.COMMANDS.map { it.first }, manifest.contributes.commands.map { it.command })
    }
}
