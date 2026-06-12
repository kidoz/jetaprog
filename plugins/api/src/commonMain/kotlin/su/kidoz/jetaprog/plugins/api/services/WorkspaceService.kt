package su.kidoz.jetaprog.plugins.api.services

import kotlinx.coroutines.flow.Flow
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent

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
     * Gets a configuration value.
     * @param section The configuration section
     * @param key The configuration key
     * @return The configuration value, or null if not set
     */
    public fun <T> getConfiguration(
        section: String,
        key: String,
    ): T?

    /**
     * Updates a configuration value.
     * @param section The configuration section
     * @param key The configuration key
     * @param value The new value
     * @param global Whether to update globally or for the workspace
     */
    public suspend fun updateConfiguration(
        section: String,
        key: String,
        value: Any?,
        global: Boolean = false,
    ): Result<Unit>
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
