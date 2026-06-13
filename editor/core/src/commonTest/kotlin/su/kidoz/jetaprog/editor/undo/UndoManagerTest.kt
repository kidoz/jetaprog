package su.kidoz.jetaprog.editor.undo

import su.kidoz.jetaprog.common.text.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoManagerTest {
    private fun snapshot(content: String) = EditSnapshot(content, TextPosition.Zero)

    @Test
    fun undoOnEmptyStackReturnsNull() {
        val manager = UndoManager()
        assertNull(manager.undo(snapshot("current")))
        assertFalse(manager.canUndo)
    }

    @Test
    fun redoOnEmptyStackReturnsNull() {
        val manager = UndoManager()
        assertNull(manager.redo(snapshot("current")))
        assertFalse(manager.canRedo)
    }

    @Test
    fun undoRestoresRecordedSnapshot() {
        val manager = UndoManager()
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)

        val restored = manager.undo(snapshot("v2"))

        assertEquals("v1", restored?.content)
        assertTrue(manager.canRedo)
    }

    @Test
    fun redoRestoresUndoneSnapshot() {
        val manager = UndoManager()
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)
        manager.undo(snapshot("v2"))

        val restored = manager.redo(snapshot("v1"))

        assertEquals("v2", restored?.content)
        assertTrue(manager.canUndo)
    }

    @Test
    fun undoRedoRoundTripPreservesBothDirections() {
        val manager = UndoManager()
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)
        manager.recordBeforeEdit(snapshot("v2"), nowMs = 10_000)

        assertEquals("v2", manager.undo(snapshot("v3"))?.content)
        assertEquals("v1", manager.undo(snapshot("v2"))?.content)
        assertEquals("v2", manager.redo(snapshot("v1"))?.content)
        assertEquals("v3", manager.redo(snapshot("v2"))?.content)
    }

    @Test
    fun rapidEditsCoalesceIntoSingleUndoStep() {
        val manager = UndoManager(coalescingWindowMs = 500)
        manager.recordBeforeEdit(snapshot(""), nowMs = 0)
        manager.recordBeforeEdit(snapshot("a"), nowMs = 100)
        manager.recordBeforeEdit(snapshot("ab"), nowMs = 200)

        // The whole burst undoes to the earliest snapshot
        assertEquals("", manager.undo(snapshot("abc"))?.content)
        assertFalse(manager.canUndo)
    }

    @Test
    fun editsOutsideCoalescingWindowFormSeparateSteps() {
        val manager = UndoManager(coalescingWindowMs = 500)
        manager.recordBeforeEdit(snapshot(""), nowMs = 0)
        manager.recordBeforeEdit(snapshot("first"), nowMs = 10_000)

        assertEquals("first", manager.undo(snapshot("second"))?.content)
        assertEquals("", manager.undo(snapshot("first"))?.content)
    }

    @Test
    fun nonCoalescedEditAlwaysFormsItsOwnStep() {
        val manager = UndoManager(coalescingWindowMs = 500)
        manager.recordBeforeEdit(snapshot("typed"), nowMs = 0)
        manager.recordBeforeEdit(snapshot("formatted"), nowMs = 100, coalesce = false)

        assertEquals("formatted", manager.undo(snapshot("current"))?.content)
        assertEquals("typed", manager.undo(snapshot("formatted"))?.content)
    }

    @Test
    fun newEditClearsRedoHistory() {
        val manager = UndoManager()
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)
        manager.undo(snapshot("v2"))
        assertTrue(manager.canRedo)

        manager.recordBeforeEdit(snapshot("v1"), nowMs = 10_000)

        assertFalse(manager.canRedo)
    }

    @Test
    fun stackIsBoundedByMaxSize() {
        val manager = UndoManager(maxStackSize = 2, coalescingWindowMs = 0)
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)
        manager.recordBeforeEdit(snapshot("v2"), nowMs = 10_000)
        manager.recordBeforeEdit(snapshot("v3"), nowMs = 20_000)

        assertEquals("v3", manager.undo(snapshot("v4"))?.content)
        assertEquals("v2", manager.undo(snapshot("v3"))?.content)
        // v1 was evicted
        assertNull(manager.undo(snapshot("v2")))
    }

    @Test
    fun clearEmptiesBothStacks() {
        val manager = UndoManager()
        manager.recordBeforeEdit(snapshot("v1"), nowMs = 0)
        manager.undo(snapshot("v2"))

        manager.clear()

        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }
}
