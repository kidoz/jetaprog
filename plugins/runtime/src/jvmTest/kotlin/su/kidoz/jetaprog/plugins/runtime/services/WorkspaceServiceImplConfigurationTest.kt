package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.plugins.api.services.SettingsAccessService
import su.kidoz.jetaprog.plugins.api.services.getConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the plugin-facing workspace configuration access. */
class WorkspaceServiceImplConfigurationTest {
    private val settings = mutableMapOf("editor.tabSize" to "4", "appearance.theme" to "DARK", "lint.enabled" to "true")

    private val settingsAccess =
        object : SettingsAccessService {
            override fun getString(
                category: String,
                key: String,
                defaultValue: String,
            ): String = settings["$category.$key"] ?: defaultValue

            override fun getInt(
                category: String,
                key: String,
                defaultValue: Int,
            ): Int = settings["$category.$key"]?.toIntOrNull() ?: defaultValue

            override fun getBoolean(
                category: String,
                key: String,
                defaultValue: Boolean,
            ): Boolean = settings["$category.$key"]?.toBooleanStrictOrNull() ?: defaultValue

            override fun observeChanges(category: String?) = MutableStateFlow(category ?: "all")

            override fun getAllSettings(): Map<String, String> = settings
        }

    private val service =
        WorkspaceServiceImpl(
            fileSystem =
                su.kidoz.jetaprog.platform.filesystem
                    .JvmFileSystem(),
            workspacePath = "/",
            settingsAccess = settingsAccess,
        )

    @Test
    fun readsTypedConfigurationValues() {
        assertEquals("4", service.getConfiguration("editor", "tabSize"))
        assertEquals(4, service.getConfiguration<Int>("editor", "tabSize"))
        assertEquals("DARK", service.getConfiguration<String>("appearance", "theme"))
        assertEquals(true, service.getConfiguration<Boolean>("lint", "enabled"))
    }

    @Test
    fun missingOrUnconvertibleValuesReturnNull() {
        assertNull(service.getConfiguration<String>("editor", "missing"))
        assertNull(service.getConfiguration<Boolean>("editor", "tabSize")) // "4" is not a boolean
    }

    @Test
    fun updatesFailExplicitlyInsteadOfSilentlySucceeding() =
        runTest {
            val result = service.updateConfiguration("editor", "tabSize", 2, global = false)

            assertTrue(result.isFailure)
            assertTrue(
                result
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("editor.tabSize"),
            )
        }
}
