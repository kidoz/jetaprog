package su.kidoz.jetaprog.lint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.config.LintConfigurationData
import su.kidoz.jetaprog.lint.config.LintConfigurationStorage
import java.io.File

/**
 * JVM implementation of [LintConfigurationStorage] using the file system.
 */
public class JvmLintConfigurationStorage : LintConfigurationStorage {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun load(projectPath: String): Result<LintConfiguration> =
        withContext(Dispatchers.IO) {
            runCatching {
                val configFile = getConfigFile(projectPath)
                if (!configFile.exists()) {
                    return@runCatching LintConfiguration.DEFAULT
                }

                val content = configFile.readText()
                val data = json.decodeFromString<LintConfigurationData>(content)
                data.toConfiguration()
            }
        }

    override suspend fun save(
        projectPath: String,
        configuration: LintConfiguration,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val configDir = getConfigDir(projectPath)
                if (!configDir.exists()) {
                    configDir.mkdirs()
                }

                val configFile = getConfigFile(projectPath)
                val data = LintConfigurationData.from(configuration)
                val content = json.encodeToString(LintConfigurationData.serializer(), data)
                configFile.writeText(content)
            }
        }

    override suspend fun exists(projectPath: String): Boolean =
        withContext(Dispatchers.IO) {
            getConfigFile(projectPath).exists()
        }

    override suspend fun delete(projectPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val configFile = getConfigFile(projectPath)
                if (configFile.exists()) {
                    configFile.delete()
                }
            }
        }

    private fun getConfigDir(projectPath: String): File = File(projectPath, LintConfigurationStorage.CONFIG_DIR_NAME)

    private fun getConfigFile(projectPath: String): File =
        File(getConfigDir(projectPath), LintConfigurationStorage.CONFIG_FILE_NAME)
}
