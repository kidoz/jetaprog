package su.kidoz.jetaprog.settings.recent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.settings.storage.JvmIdeHome
import java.io.File

/**
 * A single recently-opened project entry, persisted to the IDE-scoped store.
 *
 * @property name Display name of the project (its directory name).
 * @property path Absolute path of the project root.
 * @property lastOpenedEpochMillis Wall-clock time the project was last opened, in epoch milliseconds.
 */
@Serializable
public data class RecentProjectEntry(
    val name: String,
    val path: String,
    val lastOpenedEpochMillis: Long,
)

/** Serializable wrapper so the on-disk format can evolve without breaking. */
@Serializable
private data class RecentProjectsFile(
    val projects: List<RecentProjectEntry> = emptyList(),
)

/**
 * IDE-scoped persistence for the Welcome Hub's recent-projects list.
 *
 * Backed by a single JSON file (`config/recent-projects.json`) under the IDE home,
 * mirroring the location convention used by
 * [su.kidoz.jetaprog.settings.storage.JvmSettingsStorage] for IDE-scoped settings.
 */
public class RecentProjectsService {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /**
     * Loads the recent projects, most-recent first, dropping any whose path no
     * longer exists on disk.
     */
    public suspend fun load(): List<RecentProjectEntry> =
        withContext(Dispatchers.IO) {
            val file = storeFile()
            if (!file.exists()) {
                return@withContext emptyList()
            }
            val parsed =
                runCatching { json.decodeFromString<RecentProjectsFile>(file.readText()) }
                    .getOrElse { return@withContext emptyList() }
            parsed.projects
                .filter { File(it.path).isDirectory }
                .sortedByDescending { it.lastOpenedEpochMillis }
                .take(MAX_RECENT_PROJECTS)
        }

    /**
     * Records [projectPath] as the most-recently-opened project: moves it to the
     * front, refreshes its timestamp, and caps the list at [MAX_RECENT_PROJECTS].
     *
     * @param nowEpochMillis Current wall-clock time in epoch milliseconds.
     */
    public suspend fun push(
        projectPath: String,
        nowEpochMillis: Long,
    ): List<RecentProjectEntry> =
        withContext(Dispatchers.IO) {
            val normalized = File(projectPath).absolutePath
            val name = File(normalized).name.ifBlank { normalized }
            val existing = load().filterNot { it.path == normalized }
            val updated =
                (listOf(RecentProjectEntry(name, normalized, nowEpochMillis)) + existing)
                    .take(MAX_RECENT_PROJECTS)
            persist(updated)
            updated
        }

    /** Removes the entry with the given [projectPath], if present. */
    public suspend fun remove(projectPath: String): List<RecentProjectEntry> =
        withContext(Dispatchers.IO) {
            val normalized = File(projectPath).absolutePath
            val updated = load().filterNot { it.path == normalized }
            persist(updated)
            updated
        }

    private fun persist(projects: List<RecentProjectEntry>) {
        val file = storeFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(RecentProjectsFile(projects)))
    }

    private fun storeFile(): File = File(JvmIdeHome.current(), STORE_PATH)

    private companion object {
        const val STORE_PATH = "config/recent-projects.json"
        const val MAX_RECENT_PROJECTS = 15
    }
}
