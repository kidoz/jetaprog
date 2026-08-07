package su.kidoz.jetaprog.editor.buffer

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for piece-table edits across piece boundaries. */
public class PieceTableTest {
    @Test
    public fun insertAtBoundaryIsAddedExactlyOnce() {
        val table =
            PieceTable
                .fromString("abcd")
                .insert(2, "X")
                .insert(3, "Y")

        assertEquals("abXYcd", table.getText())
        assertEquals(6, table.length)
    }

    @Test
    public fun insertIntoDocumentWithSeveralPiecesIsAddedExactlyOnce() {
        val table =
            PieceTable
                .fromString("abcdef")
                .insert(2, "X")
                .insert(5, "Y")
                .insert(1, "Z")

        assertEquals("aZbXcdYef", table.getText())
    }

    @Test
    public fun deleteAcrossOriginalAndAddedPiecesPreservesRemainingText() {
        val table =
            PieceTable
                .fromString("abcdef")
                .insert(3, "XYZ")
                .delete(2, 5)

        assertEquals("abef", table.getText())
        assertEquals("be", table.getText(1, 3))
    }

    @Test
    public fun emptyTableSupportsAppendAndDelete() {
        val table = PieceTable.empty().insert(0, "hello").delete(1, 3)

        assertEquals("ho", table.getText())
        assertEquals(1, table.lineCount)
    }
}
