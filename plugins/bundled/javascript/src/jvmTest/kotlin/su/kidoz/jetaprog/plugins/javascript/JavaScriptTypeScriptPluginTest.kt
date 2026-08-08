package su.kidoz.jetaprog.plugins.javascript

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JavaScriptTypeScriptPluginTest {
    @Test
    fun `manifest contributes modern JavaScript and TypeScript extensions`() {
        val manifest = JavaScriptTypeScriptPlugin().manifest
        val languages = manifest.contributes.languages.associateBy { it.id }

        assertEquals(JavaScriptTypeScriptPlugin.PLUGIN_ID, manifest.id)
        assertContains(languages.getValue("javascript").extensions, ".mjs")
        assertContains(languages.getValue("javascript").extensions, ".jsx")
        assertContains(languages.getValue("typescript").extensions, ".mts")
        assertContains(languages.getValue("typescript").extensions, ".tsx")
    }

    @Test
    fun `manifest activates for both languages and Node project markers`() {
        val activationEvents = JavaScriptTypeScriptPlugin().manifest.activationEvents

        assertContains(activationEvents, "onLanguage:javascript")
        assertContains(activationEvents, "onLanguage:typescript")
        assertContains(activationEvents, "workspaceContains:package.json")
        assertContains(activationEvents, "workspaceContains:tsconfig.json")
        assertContains(activationEvents, "workspaceContains:*.jsx")
        assertContains(activationEvents, "workspaceContains:*.tsx")
    }
}
