package su.kidoz.jetaprog.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Storage format for run configurations.
 */
@Serializable
public data class ConfigurationStorageData(
    /** Storage format version. */
    val version: Int = CURRENT_VERSION,
    /** All configurations. */
    val configurations: List<RunConfiguration> = emptyList(),
    /** Active configuration ID. */
    val activeConfigurationId: ConfigurationId? = null,
    /** Recent configuration IDs. */
    val recentConfigurationIds: List<ConfigurationId> = emptyList(),
) {
    public companion object {
        public const val CURRENT_VERSION: Int = 1
    }
}

/**
 * Interface for configuration storage operations.
 */
public interface ConfigurationStorage {
    /**
     * Load configurations from storage.
     *
     * @param projectPath The project root path
     * @return Result with storage data or error
     */
    public suspend fun load(projectPath: String): Result<ConfigurationStorageData>

    /**
     * Save configurations to storage.
     *
     * @param projectPath The project root path
     * @param data The data to save
     * @return Result with success or error
     */
    public suspend fun save(
        projectPath: String,
        data: ConfigurationStorageData,
    ): Result<Unit>

    /**
     * Check if storage file exists.
     *
     * @param projectPath The project root path
     * @return true if exists
     */
    public suspend fun exists(projectPath: String): Boolean

    /**
     * Delete storage file.
     *
     * @param projectPath The project root path
     * @return Result with success or error
     */
    public suspend fun delete(projectPath: String): Result<Unit>
}

/**
 * JSON serializer configuration for configurations.
 */
public val ConfigurationJson: Json =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

/**
 * Encode configuration data to JSON string.
 */
public fun ConfigurationStorageData.toJson(): String = ConfigurationJson.encodeToString(this)

/**
 * Decode configuration data from JSON string.
 */
public fun String.toConfigurationStorageData(): ConfigurationStorageData = ConfigurationJson.decodeFromString(this)

/**
 * Storage file path relative to project root.
 */
public const val CONFIGURATION_STORAGE_PATH: String = ".jetaprog/runConfigurations.json"

/**
 * Individual configuration file directory.
 */
public const val CONFIGURATION_DIR: String = ".jetaprog/runConfigurations"
