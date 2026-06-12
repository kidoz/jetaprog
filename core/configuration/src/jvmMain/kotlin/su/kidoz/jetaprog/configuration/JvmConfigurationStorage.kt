package su.kidoz.jetaprog.configuration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * JVM implementation of configuration storage using file system.
 */
public class JvmConfigurationStorage : ConfigurationStorage {
    override suspend fun load(projectPath: String): Result<ConfigurationStorageData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getStorageFile(projectPath)
                if (!file.exists()) {
                    return@runCatching ConfigurationStorageData()
                }

                val json = file.readText()
                json.toConfigurationStorageData()
            }
        }

    override suspend fun save(
        projectPath: String,
        data: ConfigurationStorageData,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getStorageFile(projectPath)
                file.parentFile?.mkdirs()
                file.writeText(data.toJson())
            }
        }

    override suspend fun exists(projectPath: String): Boolean =
        withContext(Dispatchers.IO) {
            getStorageFile(projectPath).exists()
        }

    override suspend fun delete(projectPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getStorageFile(projectPath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }

    private fun getStorageFile(projectPath: String): File = File(projectPath, CONFIGURATION_STORAGE_PATH)
}
