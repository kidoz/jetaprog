package su.kidoz.jetaprog.platform.filesystem

import java.io.File

/**
 * Constrains file access to a workspace root.
 *
 * AI agents supply paths the IDE did not author, so every agent-facing read and write is
 * resolved through this guard. Paths are canonicalized before the containment check, so
 * `..` traversal and symlinks that escape the root are rejected rather than followed.
 */
public class WorkspacePathGuard(
    /** Supplies the workspace root; null when no project is open. */
    private val workspaceRoot: () -> String?,
) {
    /**
     * Resolves [path] against the workspace root and returns its canonical absolute path.
     *
     * Relative paths are resolved against the root; absolute paths are accepted only when
     * they fall inside it.
     *
     * @throws WorkspacePathException when no project is open or the path escapes the root.
     */
    public fun resolve(path: String): String {
        val base = workspaceRoot() ?: throw WorkspacePathException("No project is open")
        val root = File(base).canonicalFile
        val target = (if (File(path).isAbsolute) File(path) else File(root, path)).canonicalFile
        if (!target.isContainedIn(root)) {
            throw WorkspacePathException("Path is outside the open project: $path")
        }
        return target.path
    }

    /**
     * Whether [path] resolves to a location inside the workspace root.
     */
    public fun isInsideWorkspace(path: String): Boolean =
        try {
            resolve(path)
            true
        } catch (_: WorkspacePathException) {
            false
        }

    private fun File.isContainedIn(root: File): Boolean = this == root || path.startsWith(root.path + File.separator)
}

/**
 * Thrown when a path cannot be resolved inside the current workspace.
 */
public class WorkspacePathException(
    message: String,
) : Exception(message)
