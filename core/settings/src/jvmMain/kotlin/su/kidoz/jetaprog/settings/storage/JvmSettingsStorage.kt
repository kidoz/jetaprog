package su.kidoz.jetaprog.settings.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.settings.SettingsScope
import su.kidoz.jetaprog.settings.model.AllSettings
import java.io.File
import java.io.IOException

/**
 * JVM implementation of [SettingsStorage] using JSON files.
 */
public class JvmSettingsStorage : SettingsStorage {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    override suspend fun load(
        scope: SettingsScope,
        projectPath: String?,
    ): Result<AllSettings?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getFile(scope, projectPath)
                if (!file.exists()) {
                    return@runCatching null
                }
                val content = file.readText()
                json.decodeFromString<AllSettings>(content)
            }
        }

    override suspend fun save(
        scope: SettingsScope,
        settings: AllSettings,
        projectPath: String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getFile(scope, projectPath)
                file.parentFile?.mkdirs()
                val content = json.encodeToString(settings)
                file.writeText(content)
            }
        }

    override suspend fun exists(
        scope: SettingsScope,
        projectPath: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            getFile(scope, projectPath).exists()
        }

    override suspend fun delete(
        scope: SettingsScope,
        projectPath: String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getFile(scope, projectPath)
                if (file.exists() && !file.delete()) {
                    throw IOException("Failed to delete settings file: ${file.absolutePath}")
                }
            }
        }

    override fun getPath(
        scope: SettingsScope,
        projectPath: String?,
    ): String = getFile(scope, projectPath).absolutePath

    private fun getFile(
        scope: SettingsScope,
        projectPath: String?,
    ): File =
        when (scope) {
            SettingsScope.IDE -> {
                getIdeSettingsFile()
            }

            SettingsScope.USER -> {
                getUserSettingsFile()
            }

            SettingsScope.WORKSPACE -> {
                requireNotNull(projectPath) { "Project path is required for WORKSPACE scope" }
                getWorkspaceSettingsFile(projectPath)
            }
        }

    private fun getIdeSettingsFile(): File {
        val ideHome = System.getProperty("jetaprog.home", System.getProperty("user.dir"))
        return File(ideHome, IDE_SETTINGS_PATH)
    }

    private fun getUserSettingsFile(): File {
        val userHome = System.getProperty("user.home")
        return File(userHome, USER_SETTINGS_PATH)
    }

    private fun getWorkspaceSettingsFile(projectPath: String): File = File(projectPath, WORKSPACE_SETTINGS_PATH)

    private companion object {
        const val IDE_SETTINGS_PATH = "config/settings.json"
        const val USER_SETTINGS_PATH = ".jetaprog/settings.json"
        const val WORKSPACE_SETTINGS_PATH = ".jetaprog/settings.json"
    }
}
