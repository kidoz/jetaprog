package su.kidoz.jetaprog.editor.search

import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Outcome of a Replace-in-Files attempt on a single file.
 */
public enum class FileReplaceStatus {
    /** The file was rewritten on disk. */
    REPLACED,

    /** The file was left untouched at the caller's request (e.g. open with unsaved changes). */
    SKIPPED,

    /** The new content could not be written; [FileReplaceResult.error] says why. */
    FAILED,
}

/**
 * The result of a Replace-in-Files attempt on a single file.
 */
public data class FileReplaceResult(
    /** The file path. */
    val filePath: String,
    /** What happened to the file. */
    val status: FileReplaceStatus,
    /** Occurrences replaced; zero unless [status] is [FileReplaceStatus.REPLACED]. */
    val replaced: Int = 0,
    /** The write error message, when [status] is [FileReplaceStatus.FAILED]. */
    val error: String? = null,
)

/**
 * Performs project-wide text replacement ("Replace in Files").
 *
 * Walks the project tree with the same rules as [ProjectTextSearcher] (skipped
 * build/VCS directories, file-size cap) and rewrites every file whose *current*
 * content matches [TextSearchQuery]. Files are re-read at replace time, so a
 * stale search result can never overwrite newer content.
 */
public class ProjectTextReplacer(
    private val fileSystem: FileSystem,
    private val excludedDirectories: Set<String> = ProjectTextSearcher.DEFAULT_EXCLUDED_DIRECTORIES,
    private val maxFileSizeBytes: Long = ProjectTextSearcher.DEFAULT_MAX_FILE_SIZE_BYTES,
    private val maxFiles: Int = ProjectTextSearcher.DEFAULT_MAX_RESULTS,
) {
    /**
     * Replaces matches of [query] in all text files under [rootPath] with
     * [replacement].
     *
     * @param skipPaths files to leave untouched, reported as
     *   [FileReplaceStatus.SKIPPED]; used to protect open editors with
     *   unsaved changes.
     * @return one result per affected file — replaced, skipped, or failed.
     *   Files without matches are not reported.
     */
    public suspend fun replaceAll(
        rootPath: String,
        query: TextSearchQuery,
        replacement: String,
        skipPaths: Set<String> = emptySet(),
    ): List<FileReplaceResult> {
        if (TextSearchMatcher.compile(query) == null) return emptyList()
        val results = mutableListOf<FileReplaceResult>()

        suspend fun walk(path: String) {
            if (results.size >= maxFiles) return
            val entries = fileSystem.listDirectory(path).getOrNull() ?: return
            for (entry in entries) {
                if (results.size >= maxFiles) return
                when {
                    entry.isDirectory -> {
                        if (entry.name !in excludedDirectories) walk(entry.path)
                    }

                    entry.isFile && entry.size in 1..maxFileSizeBytes -> {
                        val result = replaceInFile(entry.path, query, replacement, skipPaths)
                        if (result != null) results += result
                    }
                }
            }
        }

        walk(rootPath)
        return results
    }

    /**
     * Replaces within a single file and writes it back, returning null when
     * the file had no matches (and therefore was not reported).
     */
    private suspend fun replaceInFile(
        path: String,
        query: TextSearchQuery,
        replacement: String,
        skipPaths: Set<String>,
    ): FileReplaceResult? {
        val content = fileSystem.readText(path).getOrNull() ?: return null
        val replaceResult = TextSearchMatcher.replaceInText(content, query, replacement)
        if (replaceResult.occurrences == 0) return null
        if (path in skipPaths) return FileReplaceResult(path, FileReplaceStatus.SKIPPED)
        return fileSystem
            .writeText(path, replaceResult.text)
            .fold(
                onSuccess = { FileReplaceResult(path, FileReplaceStatus.REPLACED, replaceResult.occurrences) },
                onFailure = { FileReplaceResult(path, FileReplaceStatus.FAILED, error = it.message) },
            )
    }
}
