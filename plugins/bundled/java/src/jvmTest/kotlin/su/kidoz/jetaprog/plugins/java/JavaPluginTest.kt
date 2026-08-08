package su.kidoz.jetaprog.plugins.java

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JavaPluginTest {
    @Test
    fun `manifest contributes Java and activates for Java workspaces`() {
        val manifest = JavaPlugin().manifest
        val language = manifest.contributes.languages.single()

        assertEquals(JavaPlugin.PLUGIN_ID, manifest.id)
        assertEquals("java", language.id)
        assertContains(language.extensions, ".java")
        assertContains(manifest.activationEvents, "onLanguage:java")
        assertContains(manifest.activationEvents, "workspaceContains:*.java")
    }
}
