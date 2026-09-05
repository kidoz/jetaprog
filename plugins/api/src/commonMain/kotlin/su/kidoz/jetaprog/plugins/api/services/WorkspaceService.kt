package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import kotlin.reflect.KClass

/**
 * Service for workspace operations.
 */
public interface WorkspaceService {
    /**
     * The root path of the current workspace (null if no workspace is open).
     */
    public val rootPath: String?

    /**
     * The name of the current workspace.
     */
    public val name: String?

    /**
     * Gets the workspace folders.
     */
    public suspend fun getWorkspaceFolders(): List<WorkspaceFolder>

    /**
     * Finds files matching a glob pattern.
     * @param pattern The glob pattern to match
     * @param exclude Optional patterns to exclude
     * @param maxResults Maximum number of results
     * @return List of matching file paths
     */
    public suspend fun findFiles(
        pattern: String,
        exclude: String? = null,
        maxResults: Int = Int.MAX_VALUE,
    ): List<String>

    /**
     * Reads a file's contents as text.
     */
    public suspend fun readFile(path: String): Result<String>

    /**
     * Writes text to a file.
     */
    public suspend fun writeFile(
        path: String,
        content: String,
    ): Result<Unit>

    /**
     * Lists the contents of a directory.
     */
    public suspend fun listDirectory(path: String): Result<List<FileEntry>>

    /**
     * Checks if a path exists.
     */
    public suspend fun exists(path: String): Boolean

    /**
     * Creates a directory.
     */
    public suspend fun createDirectory(path: String): Result<Unit>

    /**
     * Deletes a file or directory.
     */
    public suspend fun delete(path: String): Result<Unit>

    /**
     * Watches for file system changes.
     * @param pattern Glob pattern to watch
     * @return Flow of file system events
     */
    public fun watchFiles(pattern: String): Flow<FileSystemEvent>

    /**
     * Registers a file system watcher.
     * @param pattern Glob pattern to watch
     * @param handler Handler for file system events
     * @return Disposable to stop watching
     */
    public fun onDidChangeWatchedFiles(
        pattern: String,
        handler: suspend (List<FileSystemEvent>) -> Unit,
    ): Disposable

    /**
     * Gets a configuration value from the IDE settings (flat "section.key"
     * space backed by the Settings dialog's typed models).
     *
     * Supported types: [String], [Int], [Long], [Boolean], [Double], [Float].
     *
     * @param section The configuration section (e.g. "editor")
     * @param key The configuration key (e.g. "tabSize")
     * @param type The expected value type; a mismatching stored value returns null
     * @return The configuration value, or null if not set or not convertible
     */
    public fun getConfigurationValue(
        section: String,
        key: String,
        type: KClass<*>,
    ): Any?

    /**
     * Updates a configuration value. IDE settings are typed models managed in
     * the Settings dialog — not a generic key-value store — so this call
     * **fails explicitly** with [UnsupportedOperationException] rather than
     * silently doing nothing.
     *
     * @param section The configuration section
     * @param key The configuration key
     * @param value The new value (ignored)
     * @param global Whether to update globally or for the workspace (ignored)
     */
    public suspend fun updateConfiguration(
        section: String,
        key: String,
        value: Any?,
        global: Boolean = false,
    ): Result<Unit>
}

/**
 * Typed convenience wrapper over [WorkspaceService.getConfigurationValue].
 *
 * @see WorkspaceService.getConfigurationValue
 */
public inline fun <reified T : Any> WorkspaceService.getConfiguration(
    section: String,
    key: String,
): T? {
    val value = getConfigurationValue(section, key, T::class) ?: return null
    @Suppress("UNCHECKED_CAST")
    return value as? T
}

/**
 * Represents a workspace folder.
 */
public data class WorkspaceFolder(
    /**
     * The URI of the folder.
     */
    val uri: String,
    /**
     * The name of the folder.
     */
    val name: String,
    /**
     * The index of this folder in the workspace.
     */
    val index: Int,
)
