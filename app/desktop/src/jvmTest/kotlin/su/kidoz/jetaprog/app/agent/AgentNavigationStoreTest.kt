package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentNavigationStoreTest {
    private val project = Files.createTempDirectory("jetaprog-navigation").toFile()
    private val store = AgentNavigationStore(JvmFileSystem(), project.absolutePath)
    private val draft =
        AgentDraft("Explain this selection", listOf(AgentDraftAttachment("Main.kt", "/project/Main.kt", "val x = 1")))

    @AfterTest
    fun cleanUp() {
        project.deleteRecursively()
    }

    @Test
    fun navigationAndDraftsSurviveFreshStoreAndStayCheckoutLocal() =
        runTest {
            val navigation = AgentNavigationState(mapOf("task" to AgentTaskNavigation(true, true, draft)), draft)
            store.save(navigation)
            assertEquals(navigation, AgentNavigationStore(JvmFileSystem(), project.absolutePath).load())
            assertEquals(
                AgentNavigationState(),
                AgentNavigationStore(JvmFileSystem(), "${project.absolutePath}/linked").load(),
            )
            AgentHistoryStore(
                JvmFileSystem(),
                project.absolutePath,
            ).save(AgentSessionRecord(id = "task", title = "Updated transcript"))
            assertEquals(navigation, store.load())
        }

    @Test
    fun corruptWriteFallsBackWithoutReplacingTheGoodBackupDuringRepair() =
        runTest {
            val previous = AgentNavigationState(newChatDraft = draft)
            store.save(previous)
            store.save(previous.copy(newChatDraft = AgentDraft("Newer draft")))
            val file = File(project, ".jetaprog/agent-navigation.json")
            file.writeText("{broken")
            assertEquals(previous, store.load())
            store.save(AgentNavigationState())
            assertEquals(AgentNavigationState(), store.load())
            file.delete()
            assertEquals(previous, store.load())
        }

    @Test
    fun legacyCheckoutWithoutNavigationStartsEmpty() =
        runTest {
            assertEquals(AgentNavigationState(), store.load())
        }
}
