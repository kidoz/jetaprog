package su.kidoz.jetaprog.app.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun wideSymbolsAdvanceCursorByTwoCells() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("✅|")

        assertEquals("✅|", snapshot.lines.first())
        assertEquals(3, snapshot.cursorColumn)
    }

    @Test
    fun overwritingWideSymbolClearsContinuationCell() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("✅|\r A")

        assertEquals(" A|", snapshot.lines.first())
    }

    @Test
    fun sgrStylesAndTrueColorArePreserved() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("\u001b[1;4;38;2;10;20;30;48;5;196mstyled\u001b[0m plain")
        val styled =
            snapshot.styledLines
                .first()
                .segments
                .first()

        assertEquals("styled", styled.text)
        assertTrue(styled.style.isBold)
        assertTrue(styled.style.isUnderlined)
        assertEquals(TerminalColor(10, 20, 30), styled.style.foreground)
        assertEquals(TerminalColor(255, 0, 0), styled.style.background)
        assertEquals(
            TerminalCellStyle(),
            snapshot.styledLines
                .first()
                .segments
                .last()
                .style,
        )
    }

    @Test
    fun codexStartupQueriesGenerateTerminalReplies() {
        val emulator = TerminalEmulator(columns = 80, rows = 24)

        emulator.accept("\u001b[4;7H\u001b[6n\u001b]10;?\u001b\\\u001b]11;?\u001b\\\u001b[?u\u001b[c")
        val responses = emulator.drainResponses()

        assertTrue("\u001b[4;7R" in responses)
        assertTrue(responses.any { it.startsWith("\u001b]10;rgb:") })
        assertTrue(responses.any { it.startsWith("\u001b]11;rgb:") })
        assertFalse(responses.any { it.endsWith("u") })
        assertTrue("\u001b[?1;2c" in responses)
    }

    @Test
    fun codexInputModesAreTracked() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val enabled = emulator.accept("\u001b[?2004h\u001b[?1004h\u001b=").inputMode
        assertTrue(enabled.bracketedPaste)
        assertTrue(enabled.focusReporting)
        assertTrue(enabled.applicationKeypad)

        val disabled = emulator.accept("\u001b[?2004l\u001b[?1004l\u001b>").inputMode
        assertFalse(disabled.bracketedPaste)
        assertFalse(disabled.focusReporting)
        assertFalse(disabled.applicationKeypad)
    }

    @Test
    fun supplementaryAndCombiningUnicodeRemainIntact() {
        val emulator = TerminalEmulator(columns = 20, rows = 5)

        val snapshot = emulator.accept("A\u0301 🧑\u200d💻")

        assertEquals("A\u0301 🧑\u200d💻", snapshot.lines.first())
        assertEquals(4, snapshot.cursorColumn)
    }

    @Test
    fun resizingAlternateScreenAlsoResizesPrimaryScreen() {
        val emulator = TerminalEmulator(columns = 10, rows = 3)
        emulator.accept("primary")
        emulator.accept("\u001b[?1049h")

        emulator.resize(columns = 4, rows = 2)
        emulator.accept("alt")
        val restored = emulator.accept("\u001b[?1049l\u001b[2;4HX")

        assertEquals("prim", restored.lines.first())
        assertEquals("   X", restored.lines[1])
    }

    @Test
    fun oscTitleAndHyperlinkMetadataAreRetained() {
        val emulator = TerminalEmulator(columns = 30, rows = 5)

        val snapshot =
            emulator.accept(
                "\u001b]0;Codex task\u0007\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\",
            )

        assertEquals("Codex task", snapshot.title)
        assertEquals(
            "https://example.com",
            snapshot.styledLines
                .first()
                .segments
                .first()
                .hyperlink,
        )
    }
}
