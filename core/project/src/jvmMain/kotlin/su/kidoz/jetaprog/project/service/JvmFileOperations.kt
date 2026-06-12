package su.kidoz.jetaprog.project.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * JVM implementation of [FileOperations].
 */
public class JvmFileOperations : FileOperations {
    override suspend fun exists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            File(path).exists()
        }

    override suspend fun createDirectory(path: String): Unit =
        withContext(Dispatchers.IO) {
            File(path).mkdirs()
        }

    override suspend fun readText(path: String): String =
        withContext(Dispatchers.IO) {
            File(path).readText(Charsets.UTF_8)
        }

    override suspend fun writeText(
        path: String,
        content: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }

    override suspend fun deleteRecursively(path: String): Unit =
        withContext(Dispatchers.IO) {
            File(path).deleteRecursively()
        }
}
