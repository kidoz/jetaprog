package su.kidoz.jetaprog.app.agent

import java.io.File

/** Id stored for a Claude chat. Older records leave this blank and still launch Claude. */
internal const val CLAUDE_AGENT_ID: String = "claude"

/** Id stored for a Codex chat. */
internal const val CODEX_AGENT_ID: String = "codex"

/** Id stored for a Grok chat. */
internal const val GROK_AGENT_ID: String = "grok"

/** Id stored for a Kimi chat. */
internal const val KIMI_AGENT_ID: String = "kimi"

/** Id stored for a ZCode chat. */
internal const val ZCODE_AGENT_ID: String = "zcode"

/** Shown when a turn is running in the chat being switched. */
internal const val AGENT_BUSY_NOTICE: String = "Finish the running task before changing the agent."

/** Shown when the other checkout still has a turn running. */
internal const val AGENT_OTHER_BUSY_NOTICE: String =
    "Finish the running task in the other checkout before changing the agent."

private const val CLAUDE_ACP_PACKAGE: String = "@agentclientprotocol/claude-agent-acp"
private const val CODEX_ACP_PACKAGE: String = "@agentclientprotocol/codex-acp"
private const val ZCODE_BUNDLE: String = "/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs"

/**
 * One installed agent the panel can launch over ACP.
 *
 * @property id Stable id stored with the chat.
 * @property label Name shown on the agent chip.
 * @property command Program and arguments. Paths are absolute when discovery found them.
 * @property environment Variables for this process, such as the installed CLI the adapter should run.
 */
public data class AgentLaunch(
    val id: String,
    val label: String,
    val command: List<String>,
    val environment: Map<String, String> = emptyMap(),
)

/**
 * The agent a new chat uses. The main checkout and its worktree share one instance.
 */
public class AgentChoice(
    launch: AgentLaunch,
) {
    /** Launch used the next time a chat connects on the saved choice. */
    public var launch: AgentLaunch = launch
}

/**
 * The fallback Claude launch used when `claude` is not installed.
 *
 * [command] is split on whitespace. Discovery replaces this when the binary is present.
 */
internal fun legacyClaudeLaunch(command: String = "npx -y $CLAUDE_ACP_PACKAGE"): AgentLaunch =
    AgentLaunch(
        id = CLAUDE_AGENT_ID,
        label = "Claude",
        command = command.split(Regex("\\s+")).filter { it.isNotBlank() },
    )

/**
 * Agents whose launch files exist.
 *
 * The search walks [path], then [extraDirectories]. Claude and Codex are included when `npx` and
 * their CLI both resolve. ZCode is included when its bundled CLI and `zcode-acp-server` both exist.
 * Nothing is downloaded and no process is started.
 */
internal fun discoverInstalledAgents(
    path: String? = System.getenv("PATH"),
    pathSeparator: String = File.pathSeparator,
    extraDirectories: List<String> = listOf("/opt/homebrew/bin", "/usr/local/bin"),
    fileExists: (String) -> Boolean = { candidate -> File(candidate).isFile },
    zcodeBin: String? = System.getenv("ZCODE_BIN"),
): List<AgentLaunch> {
    val directories = agentSearchDirectories(path, pathSeparator, extraDirectories)
    val npx = resolveExecutable("npx", directories, fileExists)
    val found = mutableListOf<AgentLaunch>()
    if (npx != null) {
        resolveExecutable("claude", directories, fileExists)?.let { claude ->
            found +=
                AgentLaunch(
                    id = CLAUDE_AGENT_ID,
                    label = "Claude",
                    command = listOf(npx, "-y", CLAUDE_ACP_PACKAGE),
                    environment = mapOf("CLAUDE_CODE_EXECUTABLE" to claude),
                )
        }
        resolveExecutable("codex", directories, fileExists)?.let { codex ->
            found +=
                AgentLaunch(
                    id = CODEX_AGENT_ID,
                    label = "Codex",
                    command = listOf(npx, "-y", CODEX_ACP_PACKAGE),
                    environment = mapOf("CODEX_PATH" to codex),
                )
        }
    }
    resolveExecutable("grok", directories, fileExists)?.let { grok ->
        found += AgentLaunch(id = GROK_AGENT_ID, label = "Grok", command = listOf(grok, "agent", "stdio"))
    }
    resolveExecutable("kimi", directories, fileExists)?.let { kimi ->
        found += AgentLaunch(id = KIMI_AGENT_ID, label = "Kimi", command = listOf(kimi, "acp"))
    }
    val zcodeCli =
        zcodeBin?.takeIf { it.isNotBlank() && fileExists(it) }
            ?: ZCODE_BUNDLE.takeIf(fileExists)
    val bridge = resolveExecutable("zcode-acp-server", directories, fileExists)
    if (zcodeCli != null && bridge != null) {
        found +=
            AgentLaunch(
                id = ZCODE_AGENT_ID,
                label = "ZCode",
                command = listOf(bridge),
                environment = mapOf("ZCODE_BIN" to zcodeCli),
            )
    }
    return found
}

/**
 * The launch for a new chat.
 *
 * A saved id that is installed wins. Otherwise Claude wins when it is installed. Otherwise the
 * first installed agent wins, then [fallback].
 */
internal fun defaultLaunch(
    agentId: String?,
    installed: List<AgentLaunch>,
    fallback: AgentLaunch,
): AgentLaunch {
    val saved = agentId?.takeIf { it.isNotBlank() }
    if (saved != null) {
        installed.firstOrNull { it.id == saved }?.let { return it }
    }
    return installed.firstOrNull { it.id == CLAUDE_AGENT_ID } ?: installed.firstOrNull() ?: fallback
}

/**
 * The launch for a saved chat.
 *
 * A blank id means Claude. An explicit id that is not installed returns null so the chat is not
 * opened on a different agent.
 */
internal fun restoredLaunch(
    agentId: String?,
    installed: List<AgentLaunch>,
    fallback: AgentLaunch,
): AgentLaunch? {
    if (agentId.isNullOrBlank() || agentId == CLAUDE_AGENT_ID) {
        return installed.firstOrNull { it.id == CLAUDE_AGENT_ID } ?: fallback
    }
    return installed.firstOrNull { it.id == agentId }
}

/** The chip label for a stored agent id, including one that is no longer installed. */
internal fun agentLabel(agentId: String?): String =
    when (agentId) {
        null, "", CLAUDE_AGENT_ID -> "Claude"
        CODEX_AGENT_ID -> "Codex"
        GROK_AGENT_ID -> "Grok"
        KIMI_AGENT_ID -> "Kimi"
        ZCODE_AGENT_ID -> "ZCode"
        else -> agentId
    }

/** Notice shown when a saved chat's agent is no longer installed. */
internal fun agentUnavailableNotice(label: String): String = "$label is not installed."

internal fun agentSearchDirectories(
    path: String?,
    separator: String,
    extras: List<String>,
): List<String> {
    val listed =
        path
            .orEmpty()
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    return (listed + extras).distinct()
}

internal fun resolveExecutable(
    name: String,
    directories: List<String>,
    fileExists: (String) -> Boolean,
): String? {
    val fileNames = listOf(name, "$name.exe", "$name.cmd")
    for (directory in directories) {
        for (fileName in fileNames) {
            val candidate = File(directory, fileName).path
            if (fileExists(candidate)) return candidate
        }
    }
    return null
}
