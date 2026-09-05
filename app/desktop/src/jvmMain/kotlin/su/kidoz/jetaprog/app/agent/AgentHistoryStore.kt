package su.kidoz.jetaprog.app.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.util.UUID

/** Role of a persisted conversation message. */
public enum class AgentHistoryRole {
    USER,
    AGENT,
}

/** One text message of a persisted agent conversation. */
@Serializable
public data class AgentHistoryMessage(
    val role: AgentHistoryRole,
    val text: String,
)

/**
 * A persisted agent conversation: a text transcript of the session. Tool
 * calls, diffs and approvals are intentionally not persisted — only the
 * conversation text is restorable.
 *
 * @property id Stable identifier (also the file name).
 * @property title Short display title, derived from the first user message.
 * @property projectPath Workspace root the conversation belonged to.
 * @property savedAtEpochMillis Last update time.
 * @property messages The transcript, oldest first.
 */
@Serializable
public data class AgentSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Agent session",
    val projectPath: String = "",
    val savedAtEpochMillis: Long = 0,
    val messages: List<AgentHistoryMessage> = emptyList(),
)

/**
 * Persists agent conversations as one JSON file per session under the
 * project's `.jetaprog/agent-history` directory, mirroring [AgentPrefsStore].
 */
public class AgentHistoryStore(
    private val fileSystem: FileSystem,
    private val projectPath: String,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    private val dir = "$projectPath/.jetaprog/agent-history"

    /** Persists [record], ignoring any I/O failure. */
    public suspend fun save(record: AgentSessionRecord) {
        runCatching {
            if (!fileSystem.exists(dir)) fileSystem.createDirectory(dir)
            fileSystem.writeText(
                "$dir/${record.id}.json",
                json.encodeToString(AgentSessionRecord.serializer(), record),
            )
        }
    }

    /** Lists all persisted sessions, most recently saved first. */
    public suspend fun list(): List<AgentSessionRecord> =
        runCatching {
            if (!fileSystem.exists(dir)) return@runCatching emptyList()
            fileSystem
                .listDirectory(dir)
                .getOrDefault(emptyList())
                .filter { it.isFile && it.name.endsWith(".json") }
                .mapNotNull { entry ->
                    runCatching {
                        json.decodeFromString(
                            AgentSessionRecord.serializer(),
                            fileSystem.readText(entry.path).getOrThrow(),
                        )
                    }.getOrNull()
                }.sortedByDescending { it.savedAtEpochMillis }
        }.getOrDefault(emptyList())

    /** Deletes the session with [id], ignoring any I/O failure. */
    public suspend fun remove(id: String) {
        runCatching {
            fileSystem.delete("$dir/$id.json")
        }
    }
}
