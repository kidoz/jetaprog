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

    @Test
    fun alternateScreenRestoresPrimaryScreen() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        emulator.accept("prompt")
        emulator.accept("\u001b[?1049h")
        assertEquals("alternate", emulator.accept("alternate").lines.first())

        val snapshot = emulator.accept("\u001b[?1049l")

        assertEquals("prompt", snapshot.lines.first())
    }

    @Test
    fun alternateScreenDoesNotAppendToScrollback() {
        val emulator = TerminalEmulator(columns = 20, rows = 2)

        emulator.accept("main")
        emulator.accept("\u001b[?1049h")
        emulator.accept("one\r\ntwo\r\nthree")

        val snapshot = emulator.accept("\u001b[?1049l")

        assertEquals(listOf("main"), snapshot.lines)
    }

    @Test
    fun privateModeTracksApplicationCursorKeys() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        assertEquals(false, emulator.snapshot().inputMode.applicationCursorKeys)
        assertEquals(true, emulator.accept("\u001b[?1h").inputMode.applicationCursorKeys)
        assertEquals(false, emulator.accept("\u001b[?1l").inputMode.applicationCursorKeys)
    }

    @Test
    fun scrollRegionKeepsRowsOutsideMarginsStable() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        emulator.accept("top\u001b[2;1Hone\u001b[3;1Htwo\u001b[4;1Hthree\u001b[5;1Hbottom")
        val snapshot = emulator.accept("\u001b[2;4r\u001b[4;1H\n")

        assertEquals(listOf("top", "two", "three", "", "bottom"), snapshot.lines)
    }

    @Test
    fun insertLinesRespectScrollRegionBottom() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        emulator.accept("top\u001b[2;1Hone\u001b[3;1Htwo\u001b[4;1Hthree\u001b[5;1Hbottom")
        val snapshot = emulator.accept("\u001b[2;4r\u001b[3;1H\u001b[L")

        assertEquals(listOf("top", "one", "", "two", "bottom"), snapshot.lines)
    }

    @Test
    fun privateModeTracksCursorVisibility() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        assertEquals(true, emulator.snapshot().isCursorVisible)
        assertEquals(false, emulator.accept("\u001b[?25l").isCursorVisible)
        assertEquals(true, emulator.accept("\u001b[?25h").isCursorVisible)
    }
}
