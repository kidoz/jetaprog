package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import java.util.UUID

/** Role of a persisted conversation message. */
public enum class AgentHistoryRole {
    USER,
    AGENT,
}

/**
 * One text message of a persisted agent conversation.
 *
 * @property role Who sent the message.
 * @property text The message text.
 * @property id The user-turn id when [role] is USER, so a checkpoint can find the message again.
 *   Null for agent text and for records saved before turn ids were stored.
 */
@Serializable
public data class AgentHistoryMessage(
    val role: AgentHistoryRole,
    val text: String,
    val id: String? = null,
)

/**
 * One file captured for a message checkpoint.
 *
 * @property path Absolute path inside the project.
 * @property existed Whether the file was on disk.
 * @property content The file text, or null when [existed] is false.
 */
@Serializable
public data class AgentCheckpointFile(
    val path: String,
    val existed: Boolean,
    val content: String? = null,
)

/**
 * File baselines for one user message.
 *
 * @property turnId The user turn this checkpoint belongs to.
 * @property files The first recorded contents of files written at this message.
 * @property commandRan Whether a command ran during this message.
 */
@Serializable
public data class AgentMessageCheckpoint(
    val turnId: String,
    val files: List<AgentCheckpointFile> = emptyList(),
    val commandRan: Boolean = false,
)

/** Pending file review metadata. File contents and executable approvals are not replayed. */
@Serializable
public data class AgentPendingReview(
    /** Absolute file path used to open the review. */
    val path: String,
    /** File name shown in the review. */
    val fileName: String,
    /** Parent path shown under the file name. */
    val parent: String,
)

/**
 * A persisted agent conversation.
 *
 * The file stores the conversation text and the message checkpoints Restore uses.
 * Tool cards, diff cards, and approvals stay out of the file.
 *
 * @property id Stable identifier (also the file name).
 * @property title Short display title, derived from the first user message.
 * @property projectPath Workspace root the conversation belonged to.
 * @property savedAtEpochMillis Last update time.
 * @property agentSessionId The ACP session identifier, used to reload the agent's
 *   conversation context. Null for records saved before this was tracked.
 * @property messages The transcript, oldest first.
 * @property addedLines Lines added across the session's diffs at the last save.
 * @property removedLines Lines removed across the session's diffs at the last save.
 * @property status Settled outcome of the last save. The open chat overlays a live status.
 * @property goal The session objective, when one was set. Null for older records.
 * @property checkpoints File baselines for each user message, oldest first.
 * @property checkpointDisk File states a later restore still recognizes as the agent's writes.
 * @property agentId The agent that owned this conversation. Null for records saved before the
 *   agent picker. Those chats launch Claude.
 */
@Serializable
public data class AgentSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Agent session",
    val projectPath: String = "",
    val savedAtEpochMillis: Long = 0,
    val agentSessionId: String? = null,
    val messages: List<AgentHistoryMessage> = emptyList(),
    val addedLines: Int = 0,
    val removedLines: Int = 0,
    val status: AgentSessionStatus = AgentSessionStatus.IDLE,
    val goal: AgentGoal? = null,
    val checkpoints: List<AgentMessageCheckpoint> = emptyList(),
    val checkpointDisk: List<AgentCheckpointFile> = emptyList(),
    val agentId: String? = null,
    /** Files still requiring review; empty for records predating persistent review. */
    val pendingReviews: List<AgentPendingReview> = emptyList(),
    /** Last visible failure, retained when reopening the conversation. */
    val failure: String? = null,
    /** Whether execution stopped before normal completion, independently of pending review. */
    val interrupted: Boolean = false,
)

/**
 * Settled outcome stored with a saved session.
 *
 * Running and waiting-for-approval are live states. Saved rows retain pending review,
 * interruption, and failure; they never imply that an old permission request is still executable.
 */
@Serializable
public enum class AgentSessionStatus {
    /** The chat is saved and nothing is waiting. */
    IDLE,

    /** The open chat has a turn in flight. */
    RUNNING,

    /** The open chat is waiting for an approval or a diff review. */
    NEEDS_YOU,

    /** A live permission request requires a user decision. */
    WAITING_APPROVAL,

    /** Execution has ended and files still need review. */
    REVIEW_READY,

    /** The turn stopped before normal completion. */
    INTERRUPTED,

    /** The last turn failed. */
    FAILED,
}

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

    /** Saves [record], retaining the previous valid version if an overwrite is interrupted. */
    public suspend fun save(record: AgentSessionRecord) {
        ignoringIoFailure {
            if (!fileSystem.exists(dir)) fileSystem.createDirectory(dir).getOrThrow()
            val path = "$dir/${record.id}.json"
            val previous = readRecord(path)
            if (previous != null) {
                fileSystem
                    .writeText("$path.bak", json.encodeToString(AgentSessionRecord.serializer(), previous))
                    .getOrThrow()
            }
            fileSystem
                .writeText(
                    path,
                    json.encodeToString(AgentSessionRecord.serializer(), record),
                ).getOrThrow()
        }
    }

    /** Lists sessions, recovering a backup only when the current record cannot be read. */
    public suspend fun list(): List<AgentSessionRecord> =
        ignoringIoFailure {
            if (!fileSystem.exists(dir)) return@ignoringIoFailure emptyList()
            fileSystem
                .listDirectory(dir)
                .getOrThrow()
                .filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".json.bak")) }
                .sortedBy { it.name.endsWith(".bak") }
                .mapNotNull { readRecord(it.path) }
                .distinctBy { it.id }
                .sortedByDescending { it.savedAtEpochMillis }
        } ?: emptyList()

    /** Deletes the session with [id], ignoring any I/O failure. */
    public suspend fun remove(id: String) {
        ignoringIoFailure {
            val backup = "$dir/$id.json.bak"
            // Remove the fallback first so a failed delete cannot resurrect an older version.
            if (fileSystem.exists(backup)) fileSystem.delete(backup).getOrThrow()
            fileSystem.delete("$dir/$id.json").getOrThrow()
        }
    }

    private suspend fun readRecord(path: String): AgentSessionRecord? =
        ignoringIoFailure {
            json.decodeFromString(AgentSessionRecord.serializer(), fileSystem.readText(path).getOrThrow())
        }

    private suspend fun <T> ignoringIoFailure(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
