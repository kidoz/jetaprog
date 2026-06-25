package su.kidoz.jetaprog.settings.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmIdeHomeTest {
    @Test
    fun configuredJetaprogHomeOverridesPlatformDefault() {
        val home = File("/tmp/jetaprog-custom").absolutePath

        val resolved =
            JvmIdeHome.resolve(
                property = properties("jetaprog.home" to home),
                environment = emptyEnvironment(),
            )

        assertEquals(File(home).absoluteFile, resolved)
    }

    @Test
    fun macDefaultUsesApplicationSupportWhenUserDirIsRoot() {
        val userHome = File("/tmp/jetaprog-user").absolutePath

        val resolved =
            JvmIdeHome.resolve(
                property =
                    properties(
                        "os.name" to "Mac OS X",
                        "user.home" to userHome,
                        "user.dir" to "/",
                    ),
                environment = emptyEnvironment(),
            )

        assertEquals(File(userHome, "Library/Application Support/JetaProg").absoluteFile, resolved)
    }

    @Test
    fun windowsDefaultUsesAppDataWhenAvailable() {
        val appData = File("/tmp/jetaprog-appdata").absolutePath

        val resolved =
            JvmIdeHome.resolve(
                property =
                    properties(
                        "os.name" to "Windows 11",
                        "user.home" to "/tmp/jetaprog-user",
                    ),
                environment = environment("APPDATA" to appData),
            )

        assertEquals(File(appData, "JetaProg").absoluteFile, resolved)
    }

    @Test
    fun unixDefaultUsesXdgConfigHomeWhenAvailable() {
        val configHome = File("/tmp/jetaprog-xdg").absolutePath

        val resolved =
            JvmIdeHome.resolve(
                property =
                    properties(
                        "os.name" to "Linux",
                        "user.home" to "/tmp/jetaprog-user",
                    ),
                environment = environment("XDG_CONFIG_HOME" to configHome),
            )

        assertEquals(File(configHome, "jetaprog").absoluteFile, resolved)
    }

    private fun properties(vararg entries: Pair<String, String>): (String) -> String? {
        val values = entries.toMap()
        return { key -> values[key] }
    }

    private fun environment(vararg entries: Pair<String, String>): (String) -> String? {
        val values = entries.toMap()
        return { key -> values[key] }
    }

    private fun emptyEnvironment(): (String) -> String? = { null }
}
