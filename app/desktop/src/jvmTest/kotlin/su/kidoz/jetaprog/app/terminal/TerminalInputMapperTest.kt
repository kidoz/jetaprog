package su.kidoz.jetaprog.app.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalInputMapperTest {
    @Test
    fun deleteUsesVtDeleteSequence() {
        val input = terminalInputForKey(Key.Delete, KeyEventType.KeyDown)

        assertEquals("\u001b[3~", input)
    }

    @Test
    fun backspaceUsesDelControlCharacter() {
        val input = terminalInputForKey(Key.Backspace, KeyEventType.KeyDown)

        assertEquals("\u007f", input)
    }

    @Test
    fun arrowsUseCsiCursorSequences() {
        assertEquals("\u001b[A", terminalInputForKey(Key.DirectionUp, KeyEventType.KeyDown))
        assertEquals("\u001b[B", terminalInputForKey(Key.DirectionDown, KeyEventType.KeyDown))
        assertEquals("\u001b[C", terminalInputForKey(Key.DirectionRight, KeyEventType.KeyDown))
        assertEquals("\u001b[D", terminalInputForKey(Key.DirectionLeft, KeyEventType.KeyDown))
    }

    @Test
    fun arrowsUseSs3SequencesInApplicationCursorMode() {
        val mode = TerminalInputMode(applicationCursorKeys = true)

        assertEquals("\u001bOA", terminalInputForKey(Key.DirectionUp, KeyEventType.KeyDown, mode = mode))
        assertEquals("\u001bOB", terminalInputForKey(Key.DirectionDown, KeyEventType.KeyDown, mode = mode))
        assertEquals("\u001bOC", terminalInputForKey(Key.DirectionRight, KeyEventType.KeyDown, mode = mode))
        assertEquals("\u001bOD", terminalInputForKey(Key.DirectionLeft, KeyEventType.KeyDown, mode = mode))
    }

    @Test
    fun ctrlLettersUseControlCharacters() {
        assertEquals("\u0003", terminalInputForKey(Key.C, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u0004", terminalInputForKey(Key.D, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u000c", terminalInputForKey(Key.L, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u0015", terminalInputForKey(Key.U, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u0017", terminalInputForKey(Key.W, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u001a", terminalInputForKey(Key.Z, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u0007", terminalInputForKey(Key.G, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u000f", terminalInputForKey(Key.O, KeyEventType.KeyDown, isCtrlPressed = true))
        assertEquals("\u0014", terminalInputForKey(Key.T, KeyEventType.KeyDown, isCtrlPressed = true))
    }

    @Test
    fun printableInputUsesCodePoint() {
        val input = terminalInputForKey(Key.A, KeyEventType.KeyDown, utf16CodePoint = 'a'.code)

        assertEquals("a", input)
    }

    @Test
    fun printableInputCanBeDeferredToTextField() {
        val input =
            terminalInputForKey(
                Key.W,
                KeyEventType.KeyDown,
                utf16CodePoint = 'w'.code,
                includePlainText = false,
            )

        assertNull(input)
    }

    @Test
    fun keyUpAndModifiedPrintableInputAreIgnored() {
        assertNull(terminalInputForKey(Key.A, KeyEventType.KeyUp, utf16CodePoint = 'a'.code))
        assertNull(terminalInputForKey(Key.A, KeyEventType.KeyDown, utf16CodePoint = 'a'.code, isMetaPressed = true))
    }

    @Test
    fun modifierOnlyKeysAreIgnoredEvenWithPlaceholderCodePoint() {
        assertNull(terminalInputForKey(Key.ShiftLeft, KeyEventType.KeyDown, utf16CodePoint = UNKNOWN_CODE_POINT))
        assertNull(terminalInputForKey(Key.ShiftRight, KeyEventType.KeyDown, utf16CodePoint = UNKNOWN_CODE_POINT))
    }

    @Test
    fun shiftTabAndModifiedArrowsUseXtermSequences() {
        assertEquals("\u001b[Z", terminalInputForKey(Key.Tab, KeyEventType.KeyDown, isShiftPressed = true))
        assertEquals(
            "\u001b[1;6A",
            terminalInputForKey(
                Key.DirectionUp,
                KeyEventType.KeyDown,
                isCtrlPressed = true,
                isShiftPressed = true,
            ),
        )
    }

    @Test
    fun altPrintableInputUsesEscapePrefix() {
        val input =
            terminalInputForKey(
                Key.A,
                KeyEventType.KeyDown,
                utf16CodePoint = 'a'.code,
                isAltPressed = true,
            )

        assertEquals("\u001ba", input)
    }

    @Test
    fun supplementaryInputPreservesSurrogatePair() {
        val input = terminalInputForKey(Key.Unknown, KeyEventType.KeyDown, utf16CodePoint = 0x1f680)

        assertEquals("🚀", input)
    }

    @Test
    fun bracketedPasteAndFocusReportsFollowTerminalModes() {
        val mode = TerminalInputMode(bracketedPaste = true, focusReporting = true)

        assertEquals("\u001b[200~one\ntwo\u001b[201~", mode.paste("one\ntwo"))
        assertEquals("\u001b[I", mode.focusChanged(focused = true))
        assertEquals("\u001b[O", mode.focusChanged(focused = false))
        assertNull(TerminalInputMode().focusChanged(focused = true))
    }

    private companion object {
        private const val UNKNOWN_CODE_POINT = 0xfffd
    }
}
