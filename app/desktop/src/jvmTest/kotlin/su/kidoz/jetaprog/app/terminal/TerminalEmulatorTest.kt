package su.kidoz.jetaprog.app.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalEmulatorTest {
    @Test
    fun blankScreenSnapshotKeepsOnlyCursorRow() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.snapshot()

        assertEquals(listOf(""), snapshot.lines)
        assertEquals(0, snapshot.cursorLineIndex)
    }

    @Test
    fun carriageReturnOverwritesCurrentLine() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("hello\rHE")

        assertEquals("HEllo", snapshot.lines.first())
    }

    @Test
    fun csiCursorMovementWritesAtRequestedCell() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("a\u001b[2;4Hb")

        assertEquals("a", snapshot.lines[0])
        assertEquals("   b", snapshot.lines[1])
    }

    @Test
    fun csiEraseLineClearsFromCursorToEnd() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("abcdef\u001b[1G\u001b[K")

        assertEquals("", snapshot.lines.first())
    }

    @Test
    fun scrollbackKeepsLinesThatLeaveVisibleGrid() {
        val emulator = TerminalEmulator(columns = 20, rows = 3)

        val snapshot = emulator.accept("one\r\ntwo\r\nthree\r\nfour")

        assertEquals("one", snapshot.lines[0])
        assertEquals("two", snapshot.lines[1])
        assertEquals("three", snapshot.lines[2])
        assertEquals("four", snapshot.lines[3])
    }

    @Test
    fun oscTitleSequenceIsNotRendered() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("before\u001b]0;title\u0007after")

        assertEquals("beforeafter", snapshot.lines.first())
    }

    @Test
    fun oscHyperlinkSequenceWithStringTerminatorIsNotRendered() {
        val emulator = TerminalEmulator(columns = 40, rows = 5)

        val snapshot = emulator.accept("a\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\b")

        assertEquals("alinkb", snapshot.lines.first())
    }
}
