package su.kidoz.jetaprog.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests for the pre-write snapshot stack used to revert agent edits. */
class AgentFileSnapshotsTest {
    @Test
    fun popsSnapshotsInReverseOrder() {
        val snapshots = AgentFileSnapshots()
        snapshots.push("/project/Main.kt", FileSnapshot(existed = true, content = "first"))
        snapshots.push("/project/Main.kt", FileSnapshot(existed = true, content = "second"))

        assertEquals("second", snapshots.pop("/project/Main.kt")?.content)
        assertEquals("first", snapshots.pop("/project/Main.kt")?.content)
        assertNull(snapshots.pop("/project/Main.kt"))
    }

    @Test
    fun tracksCreatedFiles() {
        val snapshots = AgentFileSnapshots()
        snapshots.push("/project/New.kt", FileSnapshot(existed = false, content = null))

        val snapshot = snapshots.pop("/project/New.kt")
        assertNotNull(snapshot)
        assertFalse(snapshot.existed)
        assertNull(snapshot.content)
    }

    @Test
    fun clearDropsEverything() {
        val snapshots = AgentFileSnapshots()
        snapshots.push("/project/Main.kt", FileSnapshot(existed = true, content = "first"))

        snapshots.clear()

        assertNull(snapshots.pop("/project/Main.kt"))
    }
}
