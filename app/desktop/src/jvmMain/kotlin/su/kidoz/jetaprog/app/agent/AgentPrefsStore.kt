package su.kidoz.jetaprog.app.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.app.ui.agent.AGENT_MODELS
import su.kidoz.jetaprog.app.ui.agent.Effort
import su.kidoz.jetaprog.app.ui.agent.ModelOption
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * The persisted agent preferences for a project.
 *
 * @property permissions Per-kind authorization policies.
 * @property model The selected model.
 * @property effort The selected reasoning effort.
 * @property docked Whether the surface was last docked.
 */
public data class AgentPrefs(
    val permissions: Map<PermissionKind, PermissionPolicy>,
    val model: ModelOption,
    val effort: Effort,
    val docked: Boolean,
)

@Serializable
private data class AgentPrefsDto(
    val read: String = PermissionPolicy.AUTO.name,
    val edit: String = PermissionPolicy.ASK.name,
    val run: String = PermissionPolicy.ASK.name,
    val modelId: String = AGENT_MODELS.first().id,
    val effort: String = Effort.HIGH.name,
    val docked: Boolean = false,
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
            model = AGENT_MODELS.firstOrNull { it.id == dto.modelId } ?: AGENT_MODELS.first(),
            effort = effortOf(dto.effort),
            docked = dto.docked,
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
                    modelId = prefs.model.id,
                    effort = prefs.effort.name,
                    docked = prefs.docked,
                )
            fileSystem.writeText(file, json.encodeToString(AgentPrefsDto.serializer(), dto))
        }
    }

    private fun Map<PermissionKind, PermissionPolicy>.policyName(kind: PermissionKind): String =
        (this[kind] ?: PermissionPolicy.ASK).name

    private fun policyOf(name: String): PermissionPolicy =
        runCatching { PermissionPolicy.valueOf(name) }.getOrDefault(PermissionPolicy.ASK)

    private fun effortOf(name: String): Effort = runCatching { Effort.valueOf(name) }.getOrDefault(Effort.HIGH)
}
