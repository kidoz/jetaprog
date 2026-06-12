package su.kidoz.jetaprog.plugins.api.services

/**
 * Service for plugin storage.
 */
public interface StorageService {
    /**
     * Gets a value from global storage.
     * @param key The key
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    public fun <T> getGlobal(
        key: String,
        defaultValue: T,
    ): T

    /**
     * Sets a value in global storage.
     * @param key The key
     * @param value The value to store
     */
    public suspend fun setGlobal(
        key: String,
        value: Any?,
    )

    /**
     * Gets a value from workspace storage.
     * @param key The key
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    public fun <T> getWorkspace(
        key: String,
        defaultValue: T,
    ): T

    /**
     * Sets a value in workspace storage.
     * @param key The key
     * @param value The value to store
     */
    public suspend fun setWorkspace(
        key: String,
        value: Any?,
    )

    /**
     * Gets all keys in global storage.
     */
    public fun globalKeys(): Set<String>

    /**
     * Gets all keys in workspace storage.
     */
    public fun workspaceKeys(): Set<String>

    /**
     * Deletes a key from global storage.
     */
    public suspend fun deleteGlobal(key: String)

    /**
     * Deletes a key from workspace storage.
     */
    public suspend fun deleteWorkspace(key: String)
}
