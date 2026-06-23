package su.kidoz.jetaprog.editor.search

import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Performs project-wide full-text search ("Find in Files").
 *
 * Walks the project tree via [fileSystem], skipping well-known build/VCS
 * directories and files that are too large or not decodable as text, and applies
 * [TextSearchMatcher] to each file's contents.
 */
public class ProjectTextSearcher(
    private val fileSystem: FileSystem,
    private val excludedDirectories: Set<String> = DEFAULT_EXCLUDED_DIRECTORIES,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
) {
    /**
     * Searches all text files under [rootPath] for [query].
     *
     * @param maxResults stops once this many total matches are collected.
     * @return per-file matches, in traversal order.
     */
    public suspend fun search(
        rootPath: String,
        query: TextSearchQuery,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<FileTextMatches> {
        if (TextSearchMatcher.compile(query) == null) return emptyList()
        val results = mutableListOf<FileTextMatches>()
        var total = 0

        suspend fun walk(path: String) {
            if (total >= maxResults) return
            val entries = fileSystem.listDirectory(path).getOrNull() ?: return
            for (entry in entries) {
                if (total >= maxResults) return
                when {
                    entry.isDirectory -> {
                        if (entry.name !in excludedDirectories) walk(entry.path)
                    }

                    entry.isFile && entry.size in 1..maxFileSizeBytes -> {
                        val content = fileSystem.readText(entry.path).getOrNull() ?: continue
                        val matches = TextSearchMatcher.matchesInText(content, query)
                        if (matches.isNotEmpty()) {
                            val capped = matches.take(maxResults - total)
                            results += FileTextMatches(entry.path, capped)
                            total += capped.size
                        }
                    }
                }
            }
        }

        walk(rootPath)
        return results
    }

    public companion object {
        /** Directories skipped during traversal. */
        public val DEFAULT_EXCLUDED_DIRECTORIES: Set<String> =
            setOf("build", "out", ".git", ".gradle", ".kotlin", ".idea", "node_modules", ".jetaprog")

        /** Files larger than this (bytes) are skipped. */
        public const val DEFAULT_MAX_FILE_SIZE_BYTES: Long = 2L * 1024 * 1024

        /** Default cap on total matches. */
        public const val DEFAULT_MAX_RESULTS: Int = 1000
    }
}
