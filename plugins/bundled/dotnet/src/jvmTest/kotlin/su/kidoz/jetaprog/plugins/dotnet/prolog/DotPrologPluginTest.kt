package su.kidoz.jetaprog.plugins.dotnet.prolog

import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DotPrologPluginTest {
    @Test
    fun manifestDeclaresIdentityAndDependency() {
        val manifest = DotPrologPlugin().manifest

        assertEquals(DotPrologPlugin.PLUGIN_ID, manifest.id)
        assertContains(manifest.dependencies.keys, DotNetPlugin.PLUGIN_ID)
    }

    @Test
    fun manifestActivatesOnDotPrologWorkspaces() {
        val manifest = DotPrologPlugin().manifest

        assertContains(manifest.activationEvents, "workspaceContains:*.dplproj")
        assertContains(manifest.activationEvents, "onLanguage:dotprolog")
    }

    @Test
    fun manifestContributesThePrologLanguage() {
        val manifest = DotPrologPlugin().manifest
        val language = manifest.contributes.languages.single()

        assertEquals("dotprolog", language.id)
        assertContains(language.extensions, ".pl")
        assertContains(language.extensions, ".dpli")
    }
}
