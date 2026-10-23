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

/** Second line when Claude launches through the adapter because `claude` was not found. */
internal const val CLAUDE_FALLBACK_DETAIL: String = "Adapter only. The claude CLI was not found."

/** Second line when the Codex CLI is not on the search path. */
internal const val CODEX_MISSING_DETAIL: String = "codex was not found."

/** Second line when a CLI is present and `npx` is not. */
internal const val NPX_MISSING_DETAIL: String = "Needs npx."

/** Second line when the Grok CLI is not on the search path. */
internal const val GROK_MISSING_DETAIL: String = "grok was not found."

/** Second line when the Kimi CLI is not on the search path. */
internal const val KIMI_MISSING_DETAIL: String = "kimi was not found."

/** Second line when the ZCode app is present and the ACP bridge is not. */
internal const val ZCODE_BRIDGE_DETAIL: String = "App found. Needs zcode-acp-server."

/** Second line when the ZCode app is not installed. */
internal const val ZCODE_APP_MISSING_DETAIL: String = "The ZCode app was not found."

/** Command copied for the ZCode bridge. The IDE does not run it. */
internal const val ZCODE_INSTALL_COMMAND: String = "npm install -g zcode-acp-server"

/** Shown after the ZCode install command is copied. */
internal const val ZCODE_INSTALL_COPIED_NOTICE: String = "Copied the ZCode bridge install command."

/** Shown when the ZCode install command could not be copied. */
internal const val ZCODE_INSTALL_COPY_FAILED_NOTICE: String = "Could not copy the install command."

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
 * One row of the agent catalog, including an agent that cannot launch yet.
 *
 * @property id Stable id stored with the chat.
 * @property label Name shown on the menu row.
 * @property detail The discovered path, or the reason this agent cannot launch.
 * @property launch Process to start when the row is chosen. Null when the row cannot switch.
 * @property countsAsInstalled When true, [launch] is an installation the scan found on disk.
 * @property installCommand Command the Install action copies. Null when the row has no install action.
 * @property warning When true, [detail] is the Claude adapter fallback.
 */
public data class AgentCatalogEntry(
    val id: String,
    val label: String,
    val detail: String,
    val launch: AgentLaunch? = null,
    val countsAsInstalled: Boolean = false,
    val installCommand: String? = null,
    val warning: Boolean = false,
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
): List<AgentLaunch> =
    discoverAgentCatalog(path, pathSeparator, extraDirectories, fileExists, zcodeBin).mapNotNull { entry ->
        if (entry.countsAsInstalled) entry.launch else null
    }

/**
 * Claude, Codex, Grok, Kimi, and ZCode, in that order, with a detail for every row.
 *
 * A missing `claude` stays selectable through the npx adapter. Other missing tools stay visible
 * and cannot launch. Nothing is downloaded and no process is started.
 */
internal fun discoverAgentCatalog(
    path: String? = System.getenv("PATH"),
    pathSeparator: String = File.pathSeparator,
    extraDirectories: List<String> = listOf("/opt/homebrew/bin", "/usr/local/bin"),
    fileExists: (String) -> Boolean = { candidate -> File(candidate).isFile },
    zcodeBin: String? = System.getenv("ZCODE_BIN"),
): List<AgentCatalogEntry> {
    val directories = agentSearchDirectories(path, pathSeparator, extraDirectories)
    val npx = resolveExecutable("npx", directories, fileExists)
    val claude = resolveExecutable("claude", directories, fileExists)
    val codex = resolveExecutable("codex", directories, fileExists)
    val grok = resolveExecutable("grok", directories, fileExists)
    val kimi = resolveExecutable("kimi", directories, fileExists)
    val zcodeCli =
        zcodeBin?.takeIf { it.isNotBlank() && fileExists(it) }
            ?: ZCODE_BUNDLE.takeIf(fileExists)
    val bridge = resolveExecutable("zcode-acp-server", directories, fileExists)
    return listOf(
        claudeCatalogEntry(npx, claude),
        codexCatalogEntry(npx, codex),
        commandCatalogEntry(GROK_AGENT_ID, "Grok", grok, listOf("agent", "stdio"), GROK_MISSING_DETAIL),
        commandCatalogEntry(KIMI_AGENT_ID, "Kimi", kimi, listOf("acp"), KIMI_MISSING_DETAIL),
        zcodeCatalogEntry(zcodeCli, bridge),
    )
}

private fun claudeCatalogEntry(
    npx: String?,
    claude: String?,
): AgentCatalogEntry {
    if (npx != null && claude != null) {
        return AgentCatalogEntry(
            id = CLAUDE_AGENT_ID,
            label = "Claude",
            detail = claude,
            launch =
                AgentLaunch(
                    id = CLAUDE_AGENT_ID,
                    label = "Claude",
                    command = listOf(npx, "-y", CLAUDE_ACP_PACKAGE),
                    environment = mapOf("CLAUDE_CODE_EXECUTABLE" to claude),
                ),
            countsAsInstalled = true,
        )
    }
    if (claude == null) {
        return AgentCatalogEntry(
            id = CLAUDE_AGENT_ID,
            label = "Claude",
            detail = CLAUDE_FALLBACK_DETAIL,
            launch = legacyClaudeLaunch(),
            warning = true,
        )
    }
    return AgentCatalogEntry(id = CLAUDE_AGENT_ID, label = "Claude", detail = NPX_MISSING_DETAIL)
}

private fun codexCatalogEntry(
    npx: String?,
    codex: String?,
): AgentCatalogEntry {
    if (npx != null && codex != null) {
        return AgentCatalogEntry(
            id = CODEX_AGENT_ID,
            label = "Codex",
            detail = codex,
            launch =
                AgentLaunch(
                    id = CODEX_AGENT_ID,
                    label = "Codex",
                    command = listOf(npx, "-y", CODEX_ACP_PACKAGE),
                    environment = mapOf("CODEX_PATH" to codex),
                ),
            countsAsInstalled = true,
        )
    }
    val detail = if (codex == null) CODEX_MISSING_DETAIL else NPX_MISSING_DETAIL
    return AgentCatalogEntry(id = CODEX_AGENT_ID, label = "Codex", detail = detail)
}

private fun commandCatalogEntry(
    id: String,
    label: String,
    executable: String?,
    arguments: List<String>,
    missingDetail: String,
): AgentCatalogEntry {
    if (executable == null) {
        return AgentCatalogEntry(id = id, label = label, detail = missingDetail)
    }
    val command = listOf(executable) + arguments
    return AgentCatalogEntry(
        id = id,
        label = label,
        detail = command.joinToString(" "),
        launch = AgentLaunch(id = id, label = label, command = command),
        countsAsInstalled = true,
    )
}

private fun zcodeCatalogEntry(
    zcodeCli: String?,
    bridge: String?,
): AgentCatalogEntry {
    if (zcodeCli != null && bridge != null) {
        return AgentCatalogEntry(
            id = ZCODE_AGENT_ID,
            label = "ZCode",
            detail = bridge,
            launch =
                AgentLaunch(
                    id = ZCODE_AGENT_ID,
                    label = "ZCode",
                    command = listOf(bridge),
                    environment = mapOf("ZCODE_BIN" to zcodeCli),
                ),
            countsAsInstalled = true,
        )
    }
    if (zcodeCli != null) {
        return AgentCatalogEntry(
            id = ZCODE_AGENT_ID,
            label = "ZCode",
            detail = ZCODE_BRIDGE_DETAIL,
            installCommand = ZCODE_INSTALL_COMMAND,
        )
    }
    return AgentCatalogEntry(id = ZCODE_AGENT_ID, label = "ZCode", detail = ZCODE_APP_MISSING_DETAIL)
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
