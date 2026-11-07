package su.kidoz.jetaprog.platform.filesystem

/**
 * Whether [candidate] is [root] or a file inside it.
 *
 * Both paths must already be canonical. The check uses a separator boundary, so
 * `/proj-extra` is not inside `/proj`.
 */
public fun isCanonicalPathInside(
    root: String,
    candidate: String,
): Boolean {
    if (candidate == root) return true
    val separator = if ('\\' in root && '/' !in root) '\\' else '/'
    val prefix = if (root.endsWith(separator)) root else root + separator
    return candidate.startsWith(prefix)
}

/**
 * Walks files under a root and refuses symbolic links whose targets leave that root.
 *
 * Directory links that stay inside the root are descended once. [FileSystem.canonicalize]
 * must resolve links; the interface default does not.
 *
 * @param fileSystem The file system used to list, canonicalize, and identify links.
 */
public class ContainedFileWalker(
    private val fileSystem: FileSystem,
) {
    /**
     * Visits regular files under [rootPath].
     *
     * @param onDirectory Return false to skip a directory. The root itself is not offered.
     * @param onFile Return false to stop the walk.
     */
    public suspend fun walk(
        rootPath: String,
        onDirectory: (FileEntry) -> Boolean = { true },
        onFile: suspend (FileEntry) -> Boolean,
    ) {
        val rootCanonical = runCatching { fileSystem.canonicalize(rootPath).getOrThrow() }.getOrDefault(rootPath)
        val visitedDirectories = mutableSetOf(rootCanonical)
        var proceed = true

        suspend fun recurse(path: String) {
            if (!proceed) return
            val entries = fileSystem.listDirectory(path).getOrNull() ?: return
            for (entry in entries) {
                if (!proceed) return
                if (entry.isDirectory && !onDirectory(entry)) continue
                if (entry.isSymbolicLink) {
                    val canonical = fileSystem.canonicalize(entry.path).getOrNull() ?: continue
                    if (!isCanonicalPathInside(rootCanonical, canonical)) continue
                    if (entry.isDirectory) {
                        if (visitedDirectories.add(canonical)) recurse(canonical)
                    } else if (entry.isFile) {
                        proceed = onFile(entry)
                    }
                    continue
                }
                if (entry.isDirectory) {
                    recurse(entry.path)
                } else if (entry.isFile) {
                    proceed = onFile(entry)
                }
            }
        }

        recurse(rootPath)
    }
}
