package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/** An attached file or selection retained with an unsent prompt. */
@Serializable
public data class AgentDraftAttachment(
    /** Chip label. */
    val label: String,
    /** Workspace file path. */
    val path: String,
    /** Selected text snapshot, or null for a whole-file attachment. */
    val excerpt: String? = null,
)

/** Unsent text and context; never replayed automatically to an agent. */
@Serializable
public data class AgentDraft(
    /** Composer text. */
    val text: String = "",
    /** Attached files and selections. */
    val attachments: List<AgentDraftAttachment> = emptyList(),
)

/** Organization and draft state for a saved conversation. */
@Serializable
public data class AgentTaskNavigation(
    /** Whether the task sorts ahead of other saved tasks. */
    val pinned: Boolean = false,
    /** Whether the task appears in Archived instead of Recent. */
    val archived: Boolean = false,
    /** Unsent prompt for this task. */
    val draft: AgentDraft = AgentDraft(),
)

/** Navigation state belonging only to one checkout. */
@Serializable
public data class AgentNavigationState(
    /** Organization and drafts keyed by conversation record id. */
    val tasks: Map<String, AgentTaskNavigation> = emptyMap(),
    /** Draft for the new, not-yet-submitted conversation. */
    val newChatDraft: AgentDraft = AgentDraft(),
)

/** Stores navigation separately so transcript snapshots cannot overwrite organization or drafts. */
internal class AgentNavigationStore(
    private val fileSystem: FileSystem,
    projectPath: String,
) {
    private val directory = "$projectPath/.jetaprog"
    private val path = "$directory/agent-navigation.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): AgentNavigationState = read(path) ?: read("$path.bak") ?: AgentNavigationState()

    suspend fun save(state: AgentNavigationState) {
        tolerateIo {
            if (!fileSystem.exists(directory)) fileSystem.createDirectory(directory).getOrThrow()
            read(path)?.let { previous ->
                fileSystem.writeText("$path.bak", json.encodeToString(previous)).getOrThrow()
            }
            fileSystem.writeText(path, json.encodeToString(state)).getOrThrow()
        }
    }

    private suspend fun read(file: String): AgentNavigationState? =
        tolerateIo {
            json.decodeFromString<AgentNavigationState>(fileSystem.readText(file).getOrThrow())
        }

    private suspend fun <T> tolerateIo(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
