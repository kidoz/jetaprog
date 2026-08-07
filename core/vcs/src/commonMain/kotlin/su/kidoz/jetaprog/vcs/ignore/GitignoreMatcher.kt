package su.kidoz.jetaprog.vcs.ignore

/**
 * Decides whether paths in a working copy are ignored by Git.
 *
 * Rules are collected the way git collects them: `.git/info/exclude` first,
 * then every `.gitignore` from the repository root down to the path's own
 * directory, with deeper files overriding shallower ones. A path inside an
 * ignored directory is ignored too and cannot be re-included, matching git's
 * refusal to descend into excluded directories.
 *
 * Parsed files are cached, so a tree walk reads each `.gitignore` once. Call
 * [invalidate] after the working copy changes. Instances are not thread-safe;
 * use one per consumer.
 *
 * @param rootPath the working copy root the rules are anchored to
 * @param readFile reads a file's contents, returning null when it is absent
 */
public class GitignoreMatcher(
    rootPath: String,
    private val readFile: (path: String) -> String?,
) {
    private val root: String = normalize(rootPath).trimEnd('/')
    private val cache: MutableMap<String, GitignoreFile> = mutableMapOf()

    /**
     * Whether [path] is ignored. Paths outside the working copy are never
     * ignored, and the root itself is never ignored.
     *
     * @param isDirectory whether [path] denotes a directory
     */
    public fun isIgnored(
        path: String,
        isDirectory: Boolean,
    ): Boolean {
        val segments = relativize(path)?.split('/')?.filter { it.isNotEmpty() } ?: return false
        if (segments.isEmpty()) return false

        for (index in segments.indices) {
            if (segments[index] == GIT_DIRECTORY) return true
            val isLast = index == segments.lastIndex
            val prefix = segments.subList(0, index + 1).joinToString("/")
            val ignored = decide(prefix, isDirectory = !isLast || isDirectory)
            if (isLast) return ignored
            // Nothing below an ignored directory can be re-included.
            if (ignored) return true
        }
        return false
    }

    /** Drops the cached `.gitignore` contents, forcing a re-read on the next query. */
    public fun invalidate() {
        cache.clear()
    }

    /** Applies every rule file that governs [relativePath] to it, outermost first. */
    private fun decide(
        relativePath: String,
        isDirectory: Boolean,
    ): Boolean {
        var ignored = false
        rulesAt("$root/$GIT_DIRECTORY/$INFO_EXCLUDE_PATH")
            .match(relativePath, isDirectory)
            ?.let { ignored = it }

        val segments = relativePath.split('/')
        for (depth in segments.indices) {
            val directory =
                if (depth == 0) root else "$root/" + segments.subList(0, depth).joinToString("/")
            val rules = rulesAt("$directory/$GITIGNORE_FILE_NAME")
            if (rules.isEmpty) continue
            val relativeToDirectory = segments.subList(depth, segments.size).joinToString("/")
            rules.match(relativeToDirectory, isDirectory)?.let { ignored = it }
        }
        return ignored
    }

    private fun rulesAt(path: String): GitignoreFile =
        cache.getOrPut(path) {
            readFile(path)?.let { GitignoreFile.parse(it) } ?: GitignoreFile.EMPTY
        }

    /** Returns [path] relative to the root, or null when it lies outside it. */
    private fun relativize(path: String): String? {
        val normalized = normalize(path).trimEnd('/')
        if (normalized == root) return ""
        if (!normalized.startsWith("$root/")) return null
        return normalized.removePrefix("$root/")
    }

    private companion object {
        const val GITIGNORE_FILE_NAME = ".gitignore"
        const val GIT_DIRECTORY = ".git"
        const val INFO_EXCLUDE_PATH = "info/exclude"

        fun normalize(path: String): String = path.replace('\\', '/')
    }
}
