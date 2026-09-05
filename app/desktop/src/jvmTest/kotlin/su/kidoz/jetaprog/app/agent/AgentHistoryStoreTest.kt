package su.kidoz.jetaprog.app.agent

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.platform.filesystem.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the per-project agent conversation store (JSON files, newest first). */
class AgentHistoryStoreTest {
    private val projectDir: File = Files.createTempDirectory("jetaprog-agent-history").toFile()
    private val store = AgentHistoryStore(JvmFileSystem(), projectDir.absolutePath)

    @BeforeTest
    fun setUp() {
        File(projectDir, ".jetaprog").mkdirs()
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun saveAndListRoundTrip() =
        runTest {
            val record =
                AgentSessionRecord(
                    id = "session-1",
                    title = "Fix the parser",
                    projectPath = projectDir.absolutePath,
                    savedAtEpochMillis = 100,
                    messages =
                        listOf(
                            AgentHistoryMessage(AgentHistoryRole.USER, "Fix the parser"),
                            AgentHistoryMessage(AgentHistoryRole.AGENT, "Done, tests pass."),
                        ),
                )
            store.save(record)

            val listed = store.list()
            assertEquals(listOf(record), listed)
        }

    @Test
    fun listIsSortedBySavedAtDescending() =
        runTest {
            store.save(AgentSessionRecord(id = "old", title = "Old", savedAtEpochMillis = 1))
            store.save(AgentSessionRecord(id = "new", title = "New", savedAtEpochMillis = 2))

            assertEquals(listOf("new", "old"), store.list().map { it.id })
        }

    @Test
    fun removeDeletesTheRecord() =
        runTest {
            store.save(AgentSessionRecord(id = "session-1", savedAtEpochMillis = 1))
            store.remove("session-1")

            assertTrue(store.list().isEmpty())
            assertNull(store.list().firstOrNull { it.id == "session-1" })
        }

    @Test
    fun listIsWhenNoHistoryExists() =
        runTest {
            assertTrue(store.list().isEmpty())
        }
}
