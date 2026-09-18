package su.kidoz.jetaprog.app.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.navigation.index.FileContentProvider
import su.kidoz.jetaprog.editor.navigation.index.SymbolIndexer
import java.io.File
import java.io.IOException

private val logger = KotlinLogging.logger {}

/**
 * Indexes workspace source files into the generic symbol index.
 *
 * Powers Go to Class/Symbol, file structure, and definition fallback for languages that
 * have a registered symbol extractor but no native analyzer and no running language server.
 */
public class WorkspaceSymbolIndexService(
    private val indexer: SymbolIndexer,
) {
    /**
     * Walks the workspace and indexes every file a registered extractor understands.
     */
    public suspend fun indexWorkspace(rootPath: String): Unit =
        withContext(Dispatchers.IO) {
            val root = File(rootPath)
            if (!root.isDirectory) return@withContext

            root
                .walkTopDown()
                .onEnter { dir -> dir == root || (!dir.name.startsWith(".") && dir.name !in EXCLUDED_DIRECTORIES) }
                .filter { it.isFile && it.length() <= MAX_INDEXED_FILE_BYTES && indexer.canIndex(it.path) }
                .forEach { file ->
                    // Closing the project cancels the scope; stop the walk instead of finishing it.
                    ensureActive()
                    indexSafely(file)
                }
        }

    /**
     * Re-indexes one file after a save or open; removes it from the index when it no
     * longer exists.
     */
    public suspend fun indexFile(path: String): Unit =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.isFile) {
                indexer.removeFile(path)
                return@withContext
            }
            if (!indexer.canIndex(path) || file.length() > MAX_INDEXED_FILE_BYTES) return@withContext
            indexSafely(file)
        }

    private fun indexSafely(file: File) {
        try {
            indexer.indexFile(file.absolutePath, file.readText())
        } catch (error: IOException) {
            logger.debug { "Could not index ${file.path}: ${error.message}" }
        }
    }

    public companion object {
        public val EXCLUDED_DIRECTORIES: Set<String> =
            setOf("build", "out", "dist", "node_modules", "target", "bin", "obj")

        /** Files larger than this are skipped to keep indexing fast. */
        public const val MAX_INDEXED_FILE_BYTES: Long = 1_000_000L

        /**
         * Whether [path] lies under a directory the workspace walk skips (hidden
         * directories, build output, dependency caches) relative to [rootPath].
         * Single-file re-indexing applies the same rule, so saving a file under
         * `build/` no longer adds its symbols to the index.
         */
        public fun isExcluded(
            rootPath: String,
            path: String,
        ): Boolean {
            val relative = path.removePrefix(rootPath).trimStart('/', '\\')
            val directories = relative.split('/', '\\').dropLast(1)
            return directories.any { it.startsWith(".") || it in EXCLUDED_DIRECTORIES }
        }
    }
}

/**
 * Reads file content from disk for index-based navigation fallbacks.
 */
public class DiskFileContentProvider(
    /** Text of a file as the editor currently holds it, or null when it is not open. */
    private val liveContent: (String) -> String? = { null },
) : FileContentProvider {
    override fun getOffset(
        filePath: String,
        position: TextPosition,
    ): Int? {
        val content = getContent(filePath) ?: return null
        var index = 0
        var line = 0
        while (index < content.length && line < position.line) {
            if (content[index] == '\n') line++
            index++
        }
        return (index + position.column).coerceAtMost(content.length)
    }

    override fun getPosition(
        filePath: String,
        offset: Int,
    ): TextPosition? {
        val content = getContent(filePath) ?: return null
        val safeOffset = offset.coerceIn(0, content.length)
        var line = 0
        var column = 0
        for (index in 0 until safeOffset) {
            if (content[index] == '\n') {
                line++
                column = 0
            } else {
                column++
            }
        }
        return TextPosition(line, column)
    }

    override fun getContent(filePath: String): String? =
        liveContent(filePath)
            ?: try {
                File(filePath).takeIf { it.isFile }?.readText()
            } catch (_: IOException) {
                null
            }
}
