package su.kidoz.jetaprog.app.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Converts Compose key events into byte-oriented terminal input sequences.
 */
internal fun KeyEvent.toTerminalInput(): String? =
    terminalInputForKey(
        key = key,
        type = type,
        utf16CodePoint = utf16CodePoint,
        isCtrlPressed = isCtrlPressed,
        isAltPressed = isAltPressed,
        isMetaPressed = isMetaPressed,
    )

internal fun terminalInputForKey(
    key: Key,
    type: KeyEventType,
    utf16CodePoint: Int = 0,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isMetaPressed: Boolean = false,
): String? {
    if (type != KeyEventType.KeyDown) return null

    if (isCtrlPressed && !isAltPressed && !isMetaPressed) {
        return when (key) {
            Key.A -> "\u0001"
            Key.B -> "\u0002"
            Key.C -> "\u0003"
            Key.D -> "\u0004"
            Key.E -> "\u0005"
            Key.F -> "\u0006"
            Key.K -> "\u000b"
            Key.L -> "\u000c"
            Key.R -> "\u0012"
            Key.U -> "\u0015"
            Key.W -> "\u0017"
            Key.Z -> "\u001a"
            else -> null
        }
    }

    return when (key) {
        Key.Enter -> "\r"
        Key.Backspace -> "\u007f"
        Key.Tab -> "\t"
        Key.Escape -> "\u001b"
        Key.DirectionUp -> "\u001b[A"
        Key.DirectionDown -> "\u001b[B"
        Key.DirectionRight -> "\u001b[C"
        Key.DirectionLeft -> "\u001b[D"
        Key.MoveHome -> "\u001b[H"
        Key.MoveEnd -> "\u001b[F"
        Key.Delete -> "\u001b[3~"
        Key.PageUp -> "\u001b[5~"
        Key.PageDown -> "\u001b[6~"
        else -> printableInput(utf16CodePoint, isCtrlPressed, isMetaPressed)
    }
}

private fun printableInput(
    utf16CodePoint: Int,
    isCtrlPressed: Boolean,
    isMetaPressed: Boolean,
): String? =
    when {
        isMetaPressed || isCtrlPressed -> null
        utf16CodePoint == 0 -> null
        else -> utf16CodePoint.toChar().toString()
    }
