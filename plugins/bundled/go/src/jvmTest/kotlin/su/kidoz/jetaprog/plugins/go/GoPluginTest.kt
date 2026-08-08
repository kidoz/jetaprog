package su.kidoz.jetaprog.plugins.go

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class GoPluginTest {
    @Test
    fun `manifest contributes Go and activates for Go workspaces`() {
        val manifest = GoPlugin().manifest
        val language = manifest.contributes.languages.single()

        assertEquals(GoPlugin.PLUGIN_ID, manifest.id)
        assertEquals("go", language.id)
        assertContains(language.extensions, ".go")
        assertContains(manifest.activationEvents, "onLanguage:go")
        assertContains(manifest.activationEvents, "workspaceContains:*.go")
        assertContains(manifest.activationEvents, "workspaceContains:go.mod")
        assertContains(manifest.activationEvents, "workspaceContains:go.work")
    }
}
