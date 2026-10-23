package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.ui.agent.PermissionKind
import su.kidoz.jetaprog.app.ui.agent.PermissionPolicy
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentPrefsStoreTest {
    private val projectDir: File = Files.createTempDirectory("jetaprog-agent-prefs").toFile()
    private val store = AgentPrefsStore(JvmFileSystem(), projectDir.absolutePath)

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun modelIdRoundTrips() =
        runTest {
            store.save(
                AgentPrefs(
                    permissions = mapOf(PermissionKind.READ to PermissionPolicy.AUTO),
                    modelId = "gpt-6.1-sol[high]",
                    docked = true,
                ),
            )

            val prefs = store.load()
            assertEquals("gpt-6.1-sol[high]", prefs.modelId)
            assertEquals(true, prefs.docked)
            assertEquals(PermissionPolicy.AUTO, prefs.permissions[PermissionKind.READ])
            assertEquals(emptyMap(), prefs.modelIds)
        }

    @Test
    fun modelIdsRoundTripPerAgent() =
        runTest {
            store.save(
                AgentPrefs(
                    permissions = mapOf(PermissionKind.READ to PermissionPolicy.AUTO),
                    modelId = "claude-opus-4-8",
                    docked = false,
                    agentId = "grok",
                    modelIds = mapOf("claude" to "claude-opus-4-8", "grok" to "grok-4"),
                ),
            )

            val prefs = store.load()
            assertEquals(mapOf("claude" to "claude-opus-4-8", "grok" to "grok-4"), prefs.modelIds)
            assertEquals("grok", prefs.agentId)
        }

    @Test
    fun legacyEffortFieldIsIgnored() =
        runTest {
            File(projectDir, ".jetaprog").mkdirs()
            val file = File(projectDir, ".jetaprog/agent-prefs.json")
            file.writeText("""{"modelId":"claude-opus-4-8","effort":"HIGH","docked":false}""")

            val prefs = store.load()
            assertEquals("claude-opus-4-8", prefs.modelId)
            assertEquals(PermissionPolicy.AUTO, prefs.permissions[PermissionKind.READ])
            assertEquals(PermissionPolicy.ASK, prefs.permissions[PermissionKind.EDIT])
            assertNull(prefs.agentId)
            assertEquals(emptyMap(), prefs.modelIds)
        }
}
