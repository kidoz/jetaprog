package su.kidoz.jetaprog.platform.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name

/**
 * JVM implementation of FileSystem using Java NIO.
 */
public class JvmFileSystem : FileSystem {
    override suspend fun readBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching { Files.readAllBytes(Paths.get(path)) }
        }

    override suspend fun readText(
        path: String,
        charset: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching { Files.readString(Paths.get(path), Charset.forName(charset)) }
        }

    override suspend fun writeBytes(
        path: String,
        content: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = Paths.get(path)
                p.parent?.let { Files.createDirectories(it) }
                Files.write(p, content)
                Unit
            }
        }

    override suspend fun writeText(
        path: String,
        content: String,
        charset: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = Paths.get(path)
                p.parent?.let { Files.createDirectories(it) }
                Files.writeString(p, content, Charset.forName(charset))
                Unit
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Files.deleteIfExists(Paths.get(path))
                Unit
            }
        }

    override suspend fun deleteRecursively(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = Paths.get(path)
                if (Files.exists(p)) {
                    Files.walkFileTree(
                        p,
                        object : SimpleFileVisitor<Path>() {
                            override fun visitFile(
                                file: Path,
                                attrs: BasicFileAttributes,
                            ): FileVisitResult {
                                Files.delete(file)
                                return FileVisitResult.CONTINUE
                            }

                            override fun postVisitDirectory(
                                dir: Path,
                                exc: IOException?,
                            ): FileVisitResult {
                                Files.delete(dir)
                                return FileVisitResult.CONTINUE
                            }
                        },
                    )
                }
                Unit
            }
        }

    override suspend fun createDirectory(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Files.createDirectories(Paths.get(path))
                Unit
            }
        }

    override suspend fun listDirectory(path: String): Result<List<FileEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Files.list(Paths.get(path)).use { stream ->
                    stream.map { it.toFileEntry() }.toList()
                }
            }
        }

    override suspend fun exists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            Paths.get(path).exists()
        }

    override suspend fun isDirectory(path: String): Boolean =
        withContext(Dispatchers.IO) {
            Paths.get(path).isDirectory()
        }

    override suspend fun isFile(path: String): Boolean =
        withContext(Dispatchers.IO) {
            Paths.get(path).isRegularFile()
        }

    override suspend fun getInfo(path: String): Result<FileEntry> =
        withContext(Dispatchers.IO) {
            runCatching { Paths.get(path).toFileEntry() }
        }

    override suspend fun copy(
        source: String,
        destination: String,
        overwrite: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val options =
                    if (overwrite) {
                        arrayOf(StandardCopyOption.REPLACE_EXISTING)
                    } else {
                        emptyArray()
                    }
                Files.copy(Paths.get(source), Paths.get(destination), *options)
                Unit
            }
        }

    override suspend fun move(
        source: String,
        destination: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Files.move(Paths.get(source), Paths.get(destination), StandardCopyOption.REPLACE_EXISTING)
                Unit
            }
        }

    override fun watch(
        path: String,
        recursive: Boolean,
    ): Flow<FileSystemEvent> =
        callbackFlow {
            val watchService = FileSystems.getDefault().newWatchService()
            val watchedPath = Paths.get(path)

            fun registerPath(p: Path) {
                p.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )
            }

            registerPath(watchedPath)
            if (recursive && Files.isDirectory(watchedPath)) {
                Files.walk(watchedPath).filter { Files.isDirectory(it) }.forEach { registerPath(it) }
            }

            val thread =
                Thread {
                    try {
                        while (!Thread.currentThread().isInterrupted) {
                            val key: WatchKey = watchService.take()
                            val watchablePath = key.watchable() as Path

                            for (event in key.pollEvents()) {
                                @Suppress("UNCHECKED_CAST")
                                val ev = event as WatchEvent<Path>
                                val eventPath = watchablePath.resolve(ev.context())
                                val eventType =
                                    when (event.kind()) {
                                        StandardWatchEventKinds.ENTRY_CREATE -> FileSystemEventType.CREATED
                                        StandardWatchEventKinds.ENTRY_MODIFY -> FileSystemEventType.MODIFIED
                                        StandardWatchEventKinds.ENTRY_DELETE -> FileSystemEventType.DELETED
                                        else -> continue
                                    }
                                trySend(
                                    FileSystemEvent(
                                        type = eventType,
                                        path = eventPath.toString(),
                                        isDirectory = Files.isDirectory(eventPath),
                                    ),
                                )
                            }
                            key.reset()
                        }
                    } catch (_: InterruptedException) {
                        // Expected when closing
                    }
                }
            thread.isDaemon = true
            thread.start()

            awaitClose {
                thread.interrupt()
                watchService.close()
            }
        }

    override fun resolve(
        base: String,
        relative: String,
    ): String =
        Paths
            .get(base)
            .resolve(relative)
            .normalize()
            .toString()

    override fun parent(path: String): String? = Paths.get(path).parent?.toString()

    override fun fileName(path: String): String = Paths.get(path).name

    override fun extension(path: String): String = Paths.get(path).extension

    private fun Path.toFileEntry(): FileEntry {
        val attrs = runCatching { Files.readAttributes(this, BasicFileAttributes::class.java) }.getOrNull()
        return FileEntry(
            name = this.name,
            path = this.toString(),
            isDirectory = this.isDirectory(),
            isFile = this.isRegularFile(),
            isSymbolicLink = this.isSymbolicLink(),
            size = attrs?.size() ?: 0L,
            lastModified = attrs?.lastModifiedTime()?.toMillis() ?: 0L,
            isHidden = runCatching { this.isHidden() }.getOrDefault(false),
        )
    }
}
