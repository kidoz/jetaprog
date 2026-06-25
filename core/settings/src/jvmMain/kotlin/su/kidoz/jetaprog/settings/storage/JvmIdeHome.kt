package su.kidoz.jetaprog.settings.storage

import java.io.File
import java.util.Locale

internal object JvmIdeHome {
    fun current(): File =
        resolve(
            property = { key -> System.getProperty(key) },
            environment = { key -> System.getenv(key) },
        )

    fun resolve(
        property: (String) -> String?,
        environment: (String) -> String?,
    ): File {
        val configuredHome = property(HOME_PROPERTY)?.takeIf { it.isNotBlank() }
        if (configuredHome != null) {
            return File(configuredHome).absoluteFile
        }

        val userHome = property(USER_HOME_PROPERTY)?.takeIf { it.isNotBlank() } ?: "."
        val osName = property(OS_NAME_PROPERTY).orEmpty().lowercase(Locale.ROOT)
        val isMacOs = osName.contains(MAC_OS_NAME) || osName.contains(DARWIN_OS_NAME)
        return when {
            isMacOs -> File(userHome, MAC_IDE_HOME)
            osName.startsWith(WINDOWS_OS_PREFIX) -> windowsIdeHome(userHome, environment)
            else -> unixIdeHome(userHome, environment)
        }.absoluteFile
    }

    private fun windowsIdeHome(
        userHome: String,
        environment: (String) -> String?,
    ): File {
        val appData = environment(WINDOWS_APP_DATA)?.takeIf { it.isNotBlank() }
        return if (appData == null) {
            File(userHome, WINDOWS_FALLBACK_IDE_HOME)
        } else {
            File(appData, WINDOWS_IDE_HOME)
        }
    }

    private fun unixIdeHome(
        userHome: String,
        environment: (String) -> String?,
    ): File {
        val configHome = environment(XDG_CONFIG_HOME)?.takeIf { it.isNotBlank() }
        return if (configHome == null) {
            File(File(userHome, UNIX_CONFIG_HOME), UNIX_IDE_HOME)
        } else {
            File(configHome, UNIX_IDE_HOME)
        }
    }

    private const val HOME_PROPERTY = "jetaprog.home"
    private const val USER_HOME_PROPERTY = "user.home"
    private const val OS_NAME_PROPERTY = "os.name"
    private const val MAC_OS_NAME = "mac"
    private const val DARWIN_OS_NAME = "darwin"
    private const val WINDOWS_OS_PREFIX = "win"
    private const val WINDOWS_APP_DATA = "APPDATA"
    private const val XDG_CONFIG_HOME = "XDG_CONFIG_HOME"
    private const val MAC_IDE_HOME = "Library/Application Support/JetaProg"
    private const val WINDOWS_IDE_HOME = "JetaProg"
    private const val WINDOWS_FALLBACK_IDE_HOME = "AppData/Roaming/JetaProg"
    private const val UNIX_CONFIG_HOME = ".config"
    private const val UNIX_IDE_HOME = "jetaprog"
}
