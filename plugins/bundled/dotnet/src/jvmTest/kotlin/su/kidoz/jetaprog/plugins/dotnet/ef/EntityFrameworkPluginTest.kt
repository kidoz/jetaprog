package su.kidoz.jetaprog.plugins.dotnet.ef

import su.kidoz.jetaprog.plugins.dotnet.DotNetPlugin
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityFrameworkPluginTest {
    @Test
    fun manifestDependsOnDotNetPlugin() {
        val manifest = EntityFrameworkPlugin().manifest
        assertEquals(EntityFrameworkPlugin.PLUGIN_ID, manifest.id)
        assertTrue(DotNetPlugin.PLUGIN_ID in manifest.dependencies)
    }

    @Test
    fun manifestActivatesOnDotNetWorkspaces() {
        val events = EntityFrameworkPlugin().manifest.activationEvents
        assertTrue("workspaceContains:*.csproj" in events)
        assertTrue("workspaceContains:*.sln" in events)
    }

    @Test
    fun defaultMigrationNameIsAValidIdentifier() {
        val name =
            EntityFrameworkPlugin.defaultMigrationName(
                LocalDateTime.of(2026, 8, 9, 15, 30, 45),
            )
        assertEquals("Migration_20260809_153045", name)
        assertTrue(name.all { it.isLetterOrDigit() || it == '_' })
    }
}
