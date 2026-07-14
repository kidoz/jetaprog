package su.kidoz.jetaprog.app.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Terminal modes that change keyboard escape sequences.
 */
internal data class TerminalInputMode(
    val applicationCursorKeys: Boolean = false,
    val bracketedPaste: Boolean = false,
    val focusReporting: Boolean = false,
    val applicationKeypad: Boolean = false,
)

internal fun TerminalInputMode.paste(text: String): String = if (bracketedPaste) "\u001b[200~$text\u001b[201~" else text

internal fun TerminalInputMode.focusChanged(focused: Boolean): String? =
    if (focusReporting) {
        if (focused) "\u001b[I" else "\u001b[O"
    } else {
        null
    }

/**
 * Converts Compose key events into byte-oriented terminal input sequences.
 */
internal fun KeyEvent.toTerminalInput(mode: TerminalInputMode = TerminalInputMode()): String? =
    terminalInputForKey(
        key = key,
        type = type,
        utf16CodePoint = utf16CodePoint,
        isCtrlPressed = isCtrlPressed,
        isAltPressed = isAltPressed,
        isMetaPressed = isMetaPressed,
        isShiftPressed = isShiftPressed,
        mode = mode,
    )

internal fun terminalInputForKey(
    key: Key,
    type: KeyEventType,
    utf16CodePoint: Int = 0,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isShiftPressed: Boolean = false,
    mode: TerminalInputMode = TerminalInputMode(),
): String? {
    if (type != KeyEventType.KeyDown) return null
    if (key.isModifierOnlyKey()) return null

    if (isCtrlPressed && !isAltPressed && !isMetaPressed) {
        controlInput(key)?.let { return it }
    }

    return when (key) {
        Key.Enter -> "\r"
        Key.Backspace -> "\u007f"
        Key.Tab -> if (isShiftPressed) "\u001b[Z" else "\t"
        Key.Escape -> "\u001b"
        Key.DirectionUp -> cursorKey('A', mode, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.DirectionDown -> cursorKey('B', mode, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.DirectionRight -> cursorKey('C', mode, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.DirectionLeft -> cursorKey('D', mode, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.MoveHome -> navigationKey('H', isShiftPressed, isAltPressed, isCtrlPressed)
        Key.MoveEnd -> navigationKey('F', isShiftPressed, isAltPressed, isCtrlPressed)
        Key.Insert -> tildeKey(2, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.Delete -> tildeKey(3, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.PageUp -> tildeKey(5, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.PageDown -> tildeKey(6, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F1 -> functionKey("OP", 1, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F2 -> functionKey("OQ", 1, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F3 -> functionKey("OR", 1, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F4 -> functionKey("OS", 1, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F5 -> functionKey("[15~", 15, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F6 -> functionKey("[17~", 17, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F7 -> functionKey("[18~", 18, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F8 -> functionKey("[19~", 19, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F9 -> functionKey("[20~", 20, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F10 -> functionKey("[21~", 21, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F11 -> functionKey("[23~", 23, isShiftPressed, isAltPressed, isCtrlPressed)
        Key.F12 -> functionKey("[24~", 24, isShiftPressed, isAltPressed, isCtrlPressed)
        else -> printableInput(utf16CodePoint, isCtrlPressed, isAltPressed, isMetaPressed)
    }
}

private fun controlInput(key: Key): String? {
    val value =
        when (key) {
            Key.A -> 1
            Key.B -> 2
            Key.C -> 3
            Key.D -> 4
            Key.E -> 5
            Key.F -> 6
            Key.G -> 7
            Key.H -> 8
            Key.I -> 9
            Key.J -> 10
            Key.K -> 11
            Key.L -> 12
            Key.M -> 13
            Key.N -> 14
            Key.O -> 15
            Key.P -> 16
            Key.Q -> 17
            Key.R -> 18
            Key.S -> 19
            Key.T -> 20
            Key.U -> 21
            Key.V -> 22
            Key.W -> 23
            Key.X -> 24
            Key.Y -> 25
            Key.Z -> 26
            Key.Spacebar -> 0
            else -> return null
        }
    return value.toChar().toString()
}

private fun cursorKey(
    final: Char,
    mode: TerminalInputMode,
    shift: Boolean,
    alt: Boolean,
    control: Boolean,
): String =
    if (!shift && !alt && !control && mode.applicationCursorKeys) {
        "\u001bO$final"
    } else if (!shift && !alt && !control) {
        "\u001b[$final"
    } else {
        "\u001b[1;${modifierParameter(shift, alt, control)}$final"
    }

private fun navigationKey(
    final: Char,
    shift: Boolean,
    alt: Boolean,
    control: Boolean,
): String =
    if (!shift && !alt && !control) "\u001b[$final" else "\u001b[1;${modifierParameter(shift, alt, control)}$final"

private fun tildeKey(
    code: Int,
    shift: Boolean,
    alt: Boolean,
    control: Boolean,
): String =
    if (!shift && !alt && !control) "\u001b[$code~" else "\u001b[$code;${modifierParameter(shift, alt, control)}~"

private fun functionKey(
    plain: String,
    code: Int,
    shift: Boolean,
    alt: Boolean,
    control: Boolean,
): String =
    if (!shift && !alt && !control) {
        "\u001b$plain"
    } else if (code == 1) {
        "\u001b[1;${modifierParameter(shift, alt, control)}${plain.last()}"
    } else {
        "\u001b[$code;${modifierParameter(shift, alt, control)}~"
    }

private fun modifierParameter(
    shift: Boolean,
    alt: Boolean,
    control: Boolean,
): Int = 1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (control) 4 else 0)

private fun printableInput(
    utf16CodePoint: Int,
    isCtrlPressed: Boolean,
    isAltPressed: Boolean,
    isMetaPressed: Boolean,
): String? =
    when {
        isMetaPressed || isCtrlPressed -> null
        utf16CodePoint == 0 -> null
        else -> (if (isAltPressed) "\u001b" else "") + String(Character.toChars(utf16CodePoint))
    }

private fun Key.isModifierOnlyKey(): Boolean =
    this == Key.ShiftLeft ||
        this == Key.ShiftRight ||
        this == Key.CtrlLeft ||
        this == Key.CtrlRight ||
        this == Key.AltLeft ||
        this == Key.AltRight ||
        this == Key.MetaLeft ||
        this == Key.MetaRight
