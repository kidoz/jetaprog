package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import su.kidoz.jetaprog.plugins.api.services.StorageService
import java.io.File

/**
 * Implementation of StorageService for plugin key-value storage.
 *
 * Storage is persisted as JSON files in the plugin's storage directory.
 */
public class StorageServiceImpl(
    private val pluginId: String,
    private val basePath: String,
) : StorageService {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val globalStorageDir: File by lazy {
        File(basePath, "storage/global/$pluginId").also { it.mkdirs() }
    }

    private val workspaceStorageDir: File by lazy {
        File(basePath, "storage/workspace/$pluginId").also { it.mkdirs() }
    }

    private val globalStorageFile: File get() = File(globalStorageDir, "state.json")
    private val workspaceStorageFile: File get() = File(workspaceStorageDir, "state.json")

    private val globalStorage: MutableMap<String, Any?> by lazy { loadStorage(globalStorageFile) }
    private val workspaceStorage: MutableMap<String, Any?> by lazy { loadStorage(workspaceStorageFile) }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getGlobal(
        key: String,
        defaultValue: T,
    ): T = globalStorage.getOrDefault(key, defaultValue) as T

    override suspend fun setGlobal(
        key: String,
        value: Any?,
    ) {
        globalStorage[key] = value
        saveStorage(globalStorageFile, globalStorage)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getWorkspace(
        key: String,
        defaultValue: T,
    ): T = workspaceStorage.getOrDefault(key, defaultValue) as T

    override suspend fun setWorkspace(
        key: String,
        value: Any?,
    ) {
        workspaceStorage[key] = value
        saveStorage(workspaceStorageFile, workspaceStorage)
    }

    override fun globalKeys(): Set<String> = globalStorage.keys

    override fun workspaceKeys(): Set<String> = workspaceStorage.keys

    override suspend fun deleteGlobal(key: String) {
        globalStorage.remove(key)
        saveStorage(globalStorageFile, globalStorage)
    }

    override suspend fun deleteWorkspace(key: String) {
        workspaceStorage.remove(key)
        saveStorage(workspaceStorageFile, workspaceStorage)
    }

    private fun loadStorage(file: File): MutableMap<String, Any?> {
        if (!file.exists()) return mutableMapOf()

        return try {
            val content = file.readText()
            val element = json.decodeFromString<Map<String, JsonElement>>(content)
            element.mapValues { jsonElementToAny(it.value) }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveStorage(
        file: File,
        storage: Map<String, Any?>,
    ) {
        try {
            val element = storage.mapValues { anyToJsonElement(it.value) }
            file.writeText(json.encodeToString(element))
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    private fun jsonElementToAny(element: JsonElement): Any? =
        when (element) {
            is JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                when {
                    element.isString -> element.contentOrNull
                    element.booleanOrNull != null -> element.booleanOrNull
                    element.intOrNull != null -> element.intOrNull
                    element.longOrNull != null -> element.longOrNull
                    element.doubleOrNull != null -> element.doubleOrNull
                    else -> element.contentOrNull
                }
            }

            else -> {
                element.toString()
            }
        }

    private fun anyToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
}
