package su.kidoz.jetaprog.platform.filesystem

import kotlinx.coroutines.flow.Flow

/**
 * Represents a file or directory entry.
 */
public data class FileEntry(
    /**
     * The name of the file or directory.
     */
    val name: String,
    /**
     * The full path to the file or directory.
     */
    val path: String,
    /**
     * Whether this entry is a directory.
     */
    val isDirectory: Boolean,
    /**
     * Whether this entry is a regular file.
     */
    val isFile: Boolean,
    /**
     * Whether this entry is a symbolic link.
     */
    val isSymbolicLink: Boolean,
    /**
     * The size of the file in bytes (0 for directories).
     */
    val size: Long,
    /**
     * The last modified time in milliseconds since epoch.
     */
    val lastModified: Long,
    /**
     * Whether the file is hidden.
     */
    val isHidden: Boolean,
)

/**
 * Types of file system events.
 */
public enum class FileSystemEventType {
    CREATED,
    MODIFIED,
    DELETED,
}

/**
 * Represents a file system change event.
 */
public data class FileSystemEvent(
    /**
     * The type of event.
     */
    val type: FileSystemEventType,
    /**
     * The path that was affected.
     */
    val path: String,
    /**
     * Whether the path is a directory.
     */
    val isDirectory: Boolean,
)

/**
 * Abstract file system interface for cross-platform file operations.
 */
public interface FileSystem {
    /**
     * Reads the contents of a file as bytes.
     * @param path The path to the file
     * @return Result containing the file bytes or an error
     */
    public suspend fun readBytes(path: String): Result<ByteArray>

    /**
     * Reads the contents of a file as text.
     * @param path The path to the file
     * @param charset The character set to use (default UTF-8)
     * @return Result containing the file text or an error
     */
    public suspend fun readText(
        path: String,
        charset: String = "UTF-8",
    ): Result<String>

    /**
     * Writes bytes to a file, creating it if it doesn't exist.
     * @param path The path to the file
     * @param content The bytes to write
     * @return Result indicating success or failure
     */
    public suspend fun writeBytes(
        path: String,
        content: ByteArray,
    ): Result<Unit>

    /**
     * Writes text to a file, creating it if it doesn't exist.
     * @param path The path to the file
     * @param content The text to write
     * @param charset The character set to use (default UTF-8)
     * @return Result indicating success or failure
     */
    public suspend fun writeText(
        path: String,
        content: String,
        charset: String = "UTF-8",
    ): Result<Unit>

    /**
     * Deletes a file or empty directory.
     * @param path The path to delete
     * @return Result indicating success or failure
     */
    public suspend fun delete(path: String): Result<Unit>

    /**
     * Deletes a file or directory recursively.
     * @param path The path to delete
     * @return Result indicating success or failure
     */
    public suspend fun deleteRecursively(path: String): Result<Unit>

    /**
     * Creates a directory, including parent directories if needed.
     * @param path The path to the directory
     * @return Result indicating success or failure
     */
    public suspend fun createDirectory(path: String): Result<Unit>

    /**
     * Lists the contents of a directory.
     * @param path The path to the directory
     * @return Result containing the list of entries or an error
     */
    public suspend fun listDirectory(path: String): Result<List<FileEntry>>

    /**
     * Checks if a path exists.
     * @param path The path to check
     * @return true if the path exists
     */
    public suspend fun exists(path: String): Boolean

    /**
     * Checks if a path is a directory.
     * @param path The path to check
     * @return true if the path is a directory
     */
    public suspend fun isDirectory(path: String): Boolean

    /**
     * Checks if a path is a regular file.
     * @param path The path to check
     * @return true if the path is a regular file
     */
    public suspend fun isFile(path: String): Boolean

    /**
     * Gets information about a file or directory.
     * @param path The path to get info for
     * @return Result containing the file entry or an error
     */
    public suspend fun getInfo(path: String): Result<FileEntry>

    /**
     * Copies a file or directory.
     * @param source The source path
     * @param destination The destination path
     * @param overwrite Whether to overwrite existing files
     * @return Result indicating success or failure
     */
    public suspend fun copy(
        source: String,
        destination: String,
        overwrite: Boolean = false,
    ): Result<Unit>

    /**
     * Moves a file or directory.
     * @param source The source path
     * @param destination The destination path
     * @return Result indicating success or failure
     */
    public suspend fun move(
        source: String,
        destination: String,
    ): Result<Unit>

    /**
     * Watches a path for changes.
     * @param path The path to watch
     * @param recursive Whether to watch subdirectories
     * @return A Flow of file system events
     */
    public fun watch(
        path: String,
        recursive: Boolean = false,
    ): Flow<FileSystemEvent>

    /**
     * Resolves a path relative to a base path.
     * @param base The base path
     * @param relative The relative path
     * @return The resolved absolute path
     */
    public fun resolve(
        base: String,
        relative: String,
    ): String

    /**
     * Gets the parent path of a path.
     * @param path The path
     * @return The parent path, or null if there is no parent
     */
    public fun parent(path: String): String?

    /**
     * Gets the file name from a path.
     * @param path The path
     * @return The file name
     */
    public fun fileName(path: String): String

    /**
     * Gets the file extension from a path.
     * @param path The path
     * @return The extension (without dot), or empty string if none
     */
    public fun extension(path: String): String
}
