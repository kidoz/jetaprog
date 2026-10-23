package su.kidoz.jetaprog.app.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * The persisted agent preferences for a project.
 *
 * @property permissions Per-kind authorization policies.
 * @property modelId The model the user last selected, if any.
 * @property docked Whether the surface was last docked.
 * @property agentId The agent a new chat launches. Null for preferences saved before the picker.
 */
public data class AgentPrefs(
    val permissions: Map<PermissionKind, PermissionPolicy>,
    val modelId: String?,
    val docked: Boolean,
    val agentId: String? = null,
)

@Serializable
private data class AgentPrefsDto(
    val read: String = PermissionPolicy.AUTO.name,
    val edit: String = PermissionPolicy.ASK.name,
    val run: String = PermissionPolicy.ASK.name,
    val modelId: String? = null,
    val docked: Boolean = false,
    val agentId: String? = null,
)

/**
 * Best-effort persistence of agent preferences in `.jetaprog/agent-prefs.json`
 * under the project root. All failures are swallowed: preferences are a
 * convenience, never a correctness requirement.
 *
 * @param fileSystem The file system used to read and write the prefs file.
 * @param projectPath The project root.
 */
public class AgentPrefsStore(
    private val fileSystem: FileSystem,
    private val projectPath: String,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    private val dir = "$projectPath/.jetaprog"
    private val file = "$dir/agent-prefs.json"

    /** Loads preferences, falling back to defaults when absent or unreadable. */
    public suspend fun load(): AgentPrefs {
        val dto =
            runCatching {
                if (!fileSystem.exists(file)) return@runCatching AgentPrefsDto()
                val text = fileSystem.readText(file).getOrThrow()
                json.decodeFromString(AgentPrefsDto.serializer(), text)
            }.getOrDefault(AgentPrefsDto())
        return AgentPrefs(
            permissions =
                mapOf(
                    PermissionKind.READ to policyOf(dto.read),
                    PermissionKind.EDIT to policyOf(dto.edit),
                    PermissionKind.RUN to policyOf(dto.run),
                ),
            modelId = dto.modelId,
            docked = dto.docked,
            agentId = dto.agentId,
        )
    }

    /** Persists [prefs], ignoring any I/O failure. */
    public suspend fun save(prefs: AgentPrefs) {
        runCatching {
            if (!fileSystem.exists(dir)) fileSystem.createDirectory(dir)
            val dto =
                AgentPrefsDto(
                    read = prefs.permissions.policyName(PermissionKind.READ),
                    edit = prefs.permissions.policyName(PermissionKind.EDIT),
                    run = prefs.permissions.policyName(PermissionKind.RUN),
                    modelId = prefs.modelId,
                    docked = prefs.docked,
                    agentId = prefs.agentId,
                )
            fileSystem.writeText(file, json.encodeToString(AgentPrefsDto.serializer(), dto))
        }
    }

    private fun Map<PermissionKind, PermissionPolicy>.policyName(kind: PermissionKind): String =
        (this[kind] ?: PermissionPolicy.ASK).name

    private fun policyOf(name: String): PermissionPolicy =
        runCatching { PermissionPolicy.valueOf(name) }.getOrDefault(PermissionPolicy.ASK)
}
