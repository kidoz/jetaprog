package su.kidoz.jetaprog.plugins.runtime.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.filesystem.FileSystemEvent
import su.kidoz.jetaprog.plugins.api.services.WorkspaceFolder
import su.kidoz.jetaprog.plugins.api.services.WorkspaceService
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of WorkspaceService using the platform FileSystem.
 */
public class WorkspaceServiceImpl(
    private val fileSystem: FileSystem,
    private val workspacePath: String,
) : WorkspaceService {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val watchJobs = ConcurrentHashMap<String, Job>()

    override val rootPath: String? = workspacePath

    override val name: String? = workspacePath.substringAfterLast('/')

    override suspend fun getWorkspaceFolders(): List<WorkspaceFolder> =
        listOf(
            WorkspaceFolder(
                uri = "file://$workspacePath",
                name = name ?: "workspace",
                index = 0,
            ),
        )

    override suspend fun findFiles(
        pattern: String,
        exclude: String?,
        maxResults: Int,
    ): List<String> {
        val files = mutableListOf<String>()

        suspend fun searchDir(dir: String) {
            if (files.size >= maxResults) return

            val entriesResult = fileSystem.listDirectory(dir)
            val entries = entriesResult.getOrNull() ?: return

            for (entry in entries) {
                if (files.size >= maxResults) return

                if (entry.isDirectory) {
                    if (exclude != null && matchesPattern(entry.name, exclude)) continue
                    searchDir(entry.path)
                } else {
                    if (matchesPattern(entry.name, pattern)) {
                        files.add(entry.path)
                    }
                }
            }
        }

        searchDir(workspacePath)
        return files
    }

    override suspend fun readFile(path: String): Result<String> = fileSystem.readText(path)

    override suspend fun writeFile(
        path: String,
        content: String,
    ): Result<Unit> = fileSystem.writeText(path, content)

    override suspend fun listDirectory(path: String): Result<List<FileEntry>> = fileSystem.listDirectory(path)

    override suspend fun exists(path: String): Boolean = fileSystem.exists(path)

    override suspend fun createDirectory(path: String): Result<Unit> = fileSystem.createDirectory(path)

    override suspend fun delete(path: String): Result<Unit> = fileSystem.delete(path)

    override fun watchFiles(pattern: String): Flow<FileSystemEvent> {
        // Watch the workspace root recursively and filter by pattern
        return fileSystem
            .watch(workspacePath, recursive = true)
            .filter { event -> matchesPattern(event.path.substringAfterLast('/'), pattern) }
    }

    override fun onDidChangeWatchedFiles(
        pattern: String,
        handler: suspend (List<FileSystemEvent>) -> Unit,
    ): Disposable {
        val watchKey = "watch:$pattern:${System.nanoTime()}"
        val events = mutableListOf<FileSystemEvent>()

        val job =
            scope.launch {
                watchFiles(pattern).collect { event ->
                    events.add(event)
                    // Batch events and deliver them
                    if (events.size >= 10 || events.isNotEmpty()) {
                        val batch = events.toList()
                        events.clear()
                        handler(batch)
                    }
                }
            }

        watchJobs[watchKey] = job

        return Disposable {
            job.cancel()
            watchJobs.remove(watchKey)
        }
    }

    override fun <T> getConfiguration(
        section: String,
        key: String,
    ): T? {
        // TODO: Implement configuration access
        return null
    }

    override suspend fun updateConfiguration(
        section: String,
        key: String,
        value: Any?,
        global: Boolean,
    ): Result<Unit> =
        runCatching {
            // TODO: Implement configuration updates
        }

    private fun matchesPattern(
        name: String,
        pattern: String,
    ): Boolean {
        val regex =
            pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
                .toRegex()
        return regex.matches(name)
    }
}
