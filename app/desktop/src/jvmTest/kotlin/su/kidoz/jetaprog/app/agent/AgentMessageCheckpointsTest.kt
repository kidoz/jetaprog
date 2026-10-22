package su.kidoz.jetaprog.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentMessageCheckpointsTest {
    @Test
    fun earlierCheckpointRestoresTheFirstBaselineAndLaterFiles() {
        val checkpoints = AgentMessageCheckpoints()
        checkpoints.begin("u1")
        checkpoints.noteWrite("/proj/A.kt", FileSnapshot(existed = true, content = "a0"), "a1")
        checkpoints.noteWrite("/proj/A.kt", FileSnapshot(existed = true, content = "a1"), "a2")
        checkpoints.begin("u2")
        checkpoints.noteWrite("/proj/A.kt", FileSnapshot(existed = true, content = "a2"), "a3")
        checkpoints.noteWrite("/proj/B.kt", FileSnapshot(existed = false, content = null), "b1")

        val first = checkpoints.plan("u1", current = atEnd(), dirty = { false })
        assertEquals(
            listOf(
                "/proj/A.kt" to FileSnapshot(existed = true, content = "a0"),
                "/proj/B.kt" to FileSnapshot(existed = false, content = null),
            ),
            first?.restore,
        )
        val second = checkpoints.plan("u2", current = atEnd(), dirty = { false })
        assertEquals(
            listOf(
                "/proj/A.kt" to FileSnapshot(existed = true, content = "a2"),
                "/proj/B.kt" to FileSnapshot(existed = false, content = null),
            ),
            second?.restore,
        )
        assertEquals(setOf("u1", "u2"), checkpoints.restorableTurnIds())
    }

    @Test
    fun handEditsDirtyBuffersAndUnreadableFilesAreSkipped() {
        val checkpoints = AgentMessageCheckpoints()
        checkpoints.begin("u1")
        checkpoints.noteWrite("/proj/A.kt", FileSnapshot(existed = true, content = "a0"), "a1")
        checkpoints.noteWrite("/proj/B.kt", FileSnapshot(existed = true, content = "b0"), "b1")
        checkpoints.noteWrite("/proj/C.kt", FileSnapshot(existed = true, content = "c0"), "c1")

        val plan =
            checkpoints.plan(
                "u1",
                current =
                    mapOf(
                        "/proj/A.kt" to FileSnapshot(existed = true, content = "hand"),
                        "/proj/B.kt" to FileSnapshot(existed = true, content = "b1"),
                        "/proj/C.kt" to null,
                    ),
                dirty = { it == "/proj/B.kt" },
            )

        assertEquals(emptyList(), plan?.restore)
        assertEquals(
            listOf(
                "/proj/A.kt" to CheckpointSkip.CHANGED,
                "/proj/B.kt" to CheckpointSkip.CHANGED,
                "/proj/C.kt" to CheckpointSkip.UNREADABLE,
            ),
            plan?.skipped,
        )
    }

    @Test
    fun aCommandMakesTheCheckpointRestorableWithoutAFile() {
        val checkpoints = AgentMessageCheckpoints()
        checkpoints.begin("u1")
        checkpoints.noteCommand()
        val plan = checkpoints.plan("u1", current = emptyMap(), dirty = { false })
        assertEquals(emptyList(), plan?.restore)
        assertTrue(plan?.commandsRan == true)
        assertEquals(setOf("u1"), checkpoints.restorableTurnIds())
        checkpoints.clear()
        assertNull(checkpoints.plan("u1", current = emptyMap(), dirty = { false }))
        assertFalse("u1" in checkpoints.restorableTurnIds())
    }

    @Test
    fun noticeNamesRestoredSkippedAndCommands() {
        assertEquals(
            "Restored A.kt, B.kt. Skipped C.kt because it changed after the agent wrote it. " +
                "Skipped D.kt because its current contents could not be read. " +
                "Skipped E.kt because the file could not be restored. " +
                "Commands ran after this message. Files they changed were left in place.",
            checkpointNotice(
                restored = listOf("A.kt", "B.kt"),
                skipped =
                    listOf(
                        "C.kt" to CheckpointSkip.CHANGED,
                        "D.kt" to CheckpointSkip.UNREADABLE,
                        "E.kt" to CheckpointSkip.FAILED,
                    ),
                commandsRan = true,
            ),
        )
    }

    private fun atEnd(): Map<String, FileSnapshot> =
        mapOf(
            "/proj/A.kt" to FileSnapshot(existed = true, content = "a3"),
            "/proj/B.kt" to FileSnapshot(existed = true, content = "b1"),
        )
}
