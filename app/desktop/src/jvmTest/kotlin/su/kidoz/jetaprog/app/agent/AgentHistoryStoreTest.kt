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
                    addedLines = 4,
                    removedLines = 1,
                    status = AgentSessionStatus.FAILED,
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

    @Test
    fun legacyRecordWithoutCountsLoadsAsIdle() =
        runTest {
            val dir = File(projectDir, ".jetaprog/agent-history")
            dir.mkdirs()
            File(dir, "legacy.json").writeText(
                """{"id":"legacy","title":"Legacy","savedAtEpochMillis":5,"messages":[]}""",
            )

            val listed = store.list().single()
            assertEquals("legacy", listed.id)
            assertEquals(0, listed.addedLines)
            assertEquals(0, listed.removedLines)
            assertEquals(AgentSessionStatus.IDLE, listed.status)
            assertNull(listed.goal)
            assertEquals(emptyList(), listed.checkpoints)
            assertEquals(emptyList(), listed.checkpointDisk)
        }

    @Test
    fun goalRoundTripsWithTheSession() =
        runTest {
            val goal =
                AgentGoal(
                    objective = "Make tests pass",
                    status = AgentGoalStatus.PAUSED,
                    startedAtEpochMillis = 10,
                    elapsedMillis = 90_000,
                    iteration = 4,
                )
            store.save(
                AgentSessionRecord(
                    id = "goal",
                    title = "Make tests pass",
                    savedAtEpochMillis = 10,
                    goal = goal,
                ),
            )

            assertEquals(goal, store.list().single().goal)
        }

    @Test
    fun checkpointsRoundTripWithTheUserTurn() =
        runTest {
            store.save(
                AgentSessionRecord(
                    id = "checkpoint",
                    title = "Build it",
                    savedAtEpochMillis = 20,
                    messages =
                        listOf(
                            AgentHistoryMessage(AgentHistoryRole.USER, "Build it", id = "usr-1"),
                            AgentHistoryMessage(AgentHistoryRole.AGENT, "Done"),
                        ),
                    checkpoints =
                        listOf(
                            AgentMessageCheckpoint(
                                turnId = "usr-1",
                                files =
                                    listOf(
                                        AgentCheckpointFile(
                                            path = "/proj/src/Reader.kt",
                                            existed = true,
                                            content = "old",
                                        ),
                                    ),
                                commandRan = true,
                            ),
                        ),
                    checkpointDisk =
                        listOf(
                            AgentCheckpointFile(path = "/proj/src/Reader.kt", existed = true, content = "old"),
                            AgentCheckpointFile(path = "/proj/src/Reader.kt", existed = true, content = "new"),
                        ),
                ),
            )

            val loaded = store.list().single()
            assertEquals("usr-1", loaded.messages.first().id)
            assertNull(loaded.messages.last().id)
            assertEquals(
                "old",
                loaded.checkpoints
                    .single()
                    .files
                    .single()
                    .content,
            )
            assertTrue(loaded.checkpoints.single().commandRan)
            assertEquals(listOf("old", "new"), loaded.checkpointDisk.map { it.content })
        }
}
