package su.kidoz.jetaprog.app.project

import su.kidoz.jetaprog.platform.filesystem.FileSystem

/** Result of a project-tree file operation. */
public sealed interface FileActionResult {
    /** The operation succeeded; [path] is the resulting file or directory. */
    public data class Success(
        val path: String,
    ) : FileActionResult

    /** Nothing changed on disk; [reason] is user-facing. */
    public data class Failure(
        val reason: String,
    ) : FileActionResult
}

/**
 * Create, rename and delete operations for the project tree.
 *
 * Names are validated before touching disk so the tree reports a clear reason
 * instead of surfacing a raw I/O failure, and existing files are never
 * silently overwritten.
 */
public class ProjectFileActions(
    private val fileSystem: FileSystem,
) {
    /** Creates an empty file named [name] inside [parentDirectory]. */
    public suspend fun createFile(
        parentDirectory: String,
        name: String,
    ): FileActionResult {
        val target = validateNewChild(parentDirectory, name) ?: return invalidName(name)
        if (fileSystem.exists(target)) return FileActionResult.Failure("\"$name\" already exists.")

        return fileSystem
            .writeText(target, "")
            .fold(
                onSuccess = { FileActionResult.Success(target) },
                onFailure = { FileActionResult.Failure("Could not create \"$name\": ${it.message}") },
            )
    }

    /** Creates a directory named [name] inside [parentDirectory]. */
    public suspend fun createDirectory(
        parentDirectory: String,
        name: String,
    ): FileActionResult {
        val target = validateNewChild(parentDirectory, name) ?: return invalidName(name)
        if (fileSystem.exists(target)) return FileActionResult.Failure("\"$name\" already exists.")

        return fileSystem
            .createDirectory(target)
            .fold(
                onSuccess = { FileActionResult.Success(target) },
                onFailure = { FileActionResult.Failure("Could not create \"$name\": ${it.message}") },
            )
    }

    /** Renames [path] to [newName], keeping it in the same directory. */
    public suspend fun rename(
        path: String,
        newName: String,
    ): FileActionResult {
        if (!fileSystem.exists(path)) {
            return FileActionResult.Failure("\"${path.fileName()}\" no longer exists.")
        }
        val parent = path.parentPath() ?: return FileActionResult.Failure("Cannot rename the project root.")
        val target = validateNewChild(parent, newName) ?: return invalidName(newName)
        if (target == path) return FileActionResult.Failure("The new name matches the current one.")
        if (fileSystem.exists(target)) return FileActionResult.Failure("\"$newName\" already exists.")

        return fileSystem
            .move(path, target)
            .fold(
                onSuccess = { FileActionResult.Success(target) },
                onFailure = { FileActionResult.Failure("Could not rename to \"$newName\": ${it.message}") },
            )
    }

    /** Deletes [path], including directory contents. */
    public suspend fun delete(path: String): FileActionResult {
        if (!fileSystem.exists(path)) {
            return FileActionResult.Failure("\"${path.fileName()}\" no longer exists.")
        }

        return fileSystem
            .deleteRecursively(path)
            .fold(
                onSuccess = { FileActionResult.Success(path) },
                onFailure = { FileActionResult.Failure("Could not delete \"${path.fileName()}\": ${it.message}") },
            )
    }

    /**
     * Returns the absolute path for a new child named [name], or null when the
     * name could not be used as a single file or directory name.
     */
    private fun validateNewChild(
        parentDirectory: String,
        name: String,
    ): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "." || trimmed == "..") return null
        if (trimmed.any { it in INVALID_NAME_CHARS || it.isISOControl() }) return null
        return "${parentDirectory.trimEnd('/')}/$trimmed"
    }

    private fun invalidName(name: String): FileActionResult =
        FileActionResult.Failure(
            if (name.isBlank()) "Enter a name." else "\"$name\" is not a valid file name.",
        )

    private fun String.fileName(): String = substringAfterLast('/')

    private fun String.parentPath(): String? = substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

    private companion object {
        /** Path separators; a name must be a single segment. Spaces are allowed. */
        val INVALID_NAME_CHARS = charArrayOf('/', '\\')
    }
}
