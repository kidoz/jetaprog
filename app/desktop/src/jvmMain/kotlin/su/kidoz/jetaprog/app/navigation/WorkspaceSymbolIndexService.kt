package su.kidoz.jetaprog.app.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
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
                .onEnter { dir -> !dir.name.startsWith(".") && dir.name !in EXCLUDED_DIRECTORIES }
                .filter { it.isFile && it.length() <= MAX_INDEXED_FILE_BYTES && indexer.canIndex(it.path) }
                .forEach { file -> indexSafely(file) }
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

    private companion object {
        val EXCLUDED_DIRECTORIES = setOf("build", "out", "dist", "node_modules", "target", "bin", "obj")

        /** Files larger than this are skipped to keep indexing fast. */
        const val MAX_INDEXED_FILE_BYTES = 1_000_000L
    }
}

/**
 * Reads file content from disk for index-based navigation fallbacks.
 */
public class DiskFileContentProvider : FileContentProvider {
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
        try {
            File(filePath).takeIf { it.isFile }?.readText()
        } catch (_: IOException) {
            null
        }
}
