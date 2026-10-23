package su.kidoz.jetaprog.app.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentDiscoveryTest {
    @Test
    fun installedAgentsKeepTheRecipeOrderAndAbsolutePaths() {
        val files =
            setOf(
                file("/opt/bin", "npx"),
                file("/opt/bin", "claude"),
                file("/opt/bin", "codex"),
                file("/opt/bin", "grok"),
                file("/opt/bin", "kimi"),
                file("/opt/bin", "zcode-acp-server"),
                "/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs",
            )
        val found =
            discoverInstalledAgents(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = listOf("/opt/homebrew/bin"),
                fileExists = files::contains,
                zcodeBin = null,
            )

        assertEquals(listOf("claude", "codex", "grok", "kimi", "zcode"), found.map { it.id })
        val claude = found.first()
        assertEquals(listOf(file("/opt/bin", "npx"), "-y", "@agentclientprotocol/claude-agent-acp"), claude.command)
        assertEquals(file("/opt/bin", "claude"), claude.environment["CLAUDE_CODE_EXECUTABLE"])
        assertEquals(file("/opt/bin", "codex"), found[1].environment["CODEX_PATH"])
        assertEquals(listOf(file("/opt/bin", "grok"), "agent", "stdio"), found[2].command)
        assertEquals(listOf(file("/opt/bin", "kimi"), "acp"), found[3].command)
        assertEquals("/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs", found[4].environment["ZCODE_BIN"])
    }

    @Test
    fun adaptersStayHiddenUntilNpxAndTheCliBothExist() {
        val files = setOf(file("/opt/bin", "claude"), file("/opt/bin", "grok"))
        val found =
            discoverInstalledAgents(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = emptyList(),
                fileExists = files::contains,
                zcodeBin = null,
            )

        assertEquals(listOf("grok"), found.map { it.id })
    }

    @Test
    fun pathWinsAndHomebrewFillsWhatPathMisses() {
        val files =
            setOf(file("/usr/bin", "grok"), file("/opt/homebrew/bin", "grok"), file("/opt/homebrew/bin", "kimi"))
        val found =
            discoverInstalledAgents(
                path = "/usr/bin",
                pathSeparator = ":",
                extraDirectories = listOf("/opt/homebrew/bin", "/usr/local/bin"),
                fileExists = files::contains,
                zcodeBin = null,
            )

        assertEquals(listOf("grok", "kimi"), found.map { it.id })
        assertEquals(file("/usr/bin", "grok"), found.first().command.first())
        assertEquals(file("/opt/homebrew/bin", "kimi"), found[1].command.first())
    }

    @Test
    fun zcodeNeedsTheBridgeAndPrefersAnExplicitBinary() {
        val bundle = "/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs"
        val custom = file("/tools", "zcode.cjs")
        val bridge = file("/opt/bin", "zcode-acp-server")
        val withoutBridge =
            discoverInstalledAgents(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = emptyList(),
                fileExists = setOf(bundle)::contains,
                zcodeBin = null,
            )
        assertTrue(withoutBridge.isEmpty())

        val explicit =
            discoverInstalledAgents(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = emptyList(),
                fileExists = setOf(custom, bridge)::contains,
                zcodeBin = custom,
            )
        assertEquals(custom, explicit.single().environment["ZCODE_BIN"])
        assertEquals(bridge, explicit.single().command.single())
    }

    @Test
    fun aBlankSavedIdLaunchesClaudeAndAMissingIdDoesNotChangeAgent() {
        val claude =
            AgentLaunch(
                id = "claude",
                label = "Claude",
                command = listOf("/bin/npx", "-y", "@agentclientprotocol/claude-agent-acp"),
                environment = mapOf("CLAUDE_CODE_EXECUTABLE" to "/bin/claude"),
            )
        val grok = AgentLaunch(id = "grok", label = "Grok", command = listOf("/bin/grok", "agent", "stdio"))
        val fallback = legacyClaudeLaunch()
        val installed = listOf(claude, grok)

        assertEquals(claude, defaultLaunch(null, installed, fallback))
        assertEquals(grok, defaultLaunch("grok", installed, fallback))
        assertEquals(claude, restoredLaunch(null, installed, fallback))
        assertEquals(claude, restoredLaunch("claude", installed, fallback))
        assertEquals(grok, restoredLaunch("grok", installed, fallback))
        assertNull(restoredLaunch("kimi", installed, fallback))
        assertEquals(fallback, restoredLaunch(null, emptyList(), fallback))
        assertEquals("Kimi", agentLabel("kimi"))
        assertEquals("Kimi is not installed.", agentUnavailableNotice("Kimi"))
    }

    @Test
    fun catalogKeepsEveryAgentAndDescribesWhatTheScanFound() {
        val files =
            setOf(
                file("/opt/bin", "claude"),
                file("/opt/bin", "grok"),
                "/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs",
            )
        val rows =
            discoverAgentCatalog(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = emptyList(),
                fileExists = files::contains,
                zcodeBin = null,
            )

        assertEquals(listOf("claude", "codex", "grok", "kimi", "zcode"), rows.map { it.id })
        assertEquals(NPX_MISSING_DETAIL, rows[0].detail)
        assertFalse(rows[0].countsAsInstalled)
        assertNull(rows[0].launch)
        assertEquals(CODEX_MISSING_DETAIL, rows[1].detail)
        assertNull(rows[1].installCommand)
        assertEquals("${file("/opt/bin", "grok")} agent stdio", rows[2].detail)
        assertTrue(rows[2].countsAsInstalled)
        assertEquals(KIMI_MISSING_DETAIL, rows[3].detail)
        assertEquals(ZCODE_BRIDGE_DETAIL, rows[4].detail)
        assertEquals(ZCODE_INSTALL_COMMAND, rows[4].installCommand)
        assertNull(rows[4].launch)
    }

    @Test
    fun aMissingClaudeRowStaysSelectableAsTheAdapterFallback() {
        val files = setOf(file("/opt/bin", "npx"), file("/opt/bin", "codex"))
        val rows =
            discoverAgentCatalog(
                path = "/opt/bin",
                pathSeparator = ":",
                extraDirectories = emptyList(),
                fileExists = files::contains,
                zcodeBin = null,
            )

        val claude = rows.first()
        assertEquals(CLAUDE_FALLBACK_DETAIL, claude.detail)
        assertTrue(claude.warning)
        assertFalse(claude.countsAsInstalled)
        assertEquals(legacyClaudeLaunch().command, claude.launch?.command)
        assertEquals(file("/opt/bin", "codex"), rows[1].detail)
        assertTrue(rows[1].countsAsInstalled)
        assertEquals(ZCODE_APP_MISSING_DETAIL, rows.last().detail)
        assertNull(rows.last().installCommand)
    }

    private fun file(
        directory: String,
        name: String,
    ): String = File(directory, name).path
}
