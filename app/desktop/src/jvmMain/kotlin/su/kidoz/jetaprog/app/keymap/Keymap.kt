package su.kidoz.jetaprog.app.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/**
 * Keyboard shortcut definition.
 */
public data class KeyboardShortcut(
    /**
     * The key.
     */
    val key: Key,
    /**
     * Whether Ctrl (or Cmd on macOS) is required.
     */
    val ctrl: Boolean = false,
    /**
     * Whether Shift is required.
     */
    val shift: Boolean = false,
    /**
     * Whether Alt (or Option on macOS) is required.
     */
    val alt: Boolean = false,
    /**
     * Whether Meta (Cmd on macOS, Win on Windows) is required.
     */
    val meta: Boolean = false,
) {
    /**
     * Check if a key event matches this shortcut.
     */
    public fun matches(event: KeyEvent): Boolean =
        event.key == key &&
            event.isCtrlPressed == ctrl &&
            event.isShiftPressed == shift &&
            event.isAltPressed == alt &&
            event.isMetaPressed == meta

    /**
     * Get display string for the shortcut.
     */
    public fun toDisplayString(isMac: Boolean = false): String =
        buildString {
            if (isMac) {
                if (ctrl) append("⌃")
                if (alt) append("⌥")
                if (shift) append("⇧")
                if (meta) append("⌘")
            } else {
                if (ctrl) append("Ctrl+")
                if (alt) append("Alt+")
                if (shift) append("Shift+")
                if (meta) append("Win+")
            }
            append(key.toDisplayString())
        }
}

/**
 * Convert a Key to a display string.
 */
private fun Key.toDisplayString(): String =
    when (this) {
        Key.A -> "A"
        Key.B -> "B"
        Key.C -> "C"
        Key.D -> "D"
        Key.E -> "E"
        Key.F -> "F"
        Key.G -> "G"
        Key.H -> "H"
        Key.I -> "I"
        Key.J -> "J"
        Key.K -> "K"
        Key.L -> "L"
        Key.M -> "M"
        Key.N -> "N"
        Key.O -> "O"
        Key.P -> "P"
        Key.Q -> "Q"
        Key.R -> "R"
        Key.S -> "S"
        Key.T -> "T"
        Key.U -> "U"
        Key.V -> "V"
        Key.W -> "W"
        Key.X -> "X"
        Key.Y -> "Y"
        Key.Z -> "Z"
        Key.Zero -> "0"
        Key.One -> "1"
        Key.Two -> "2"
        Key.Three -> "3"
        Key.Four -> "4"
        Key.Five -> "5"
        Key.Six -> "6"
        Key.Seven -> "7"
        Key.Eight -> "8"
        Key.Nine -> "9"
        Key.F1 -> "F1"
        Key.F2 -> "F2"
        Key.F3 -> "F3"
        Key.F4 -> "F4"
        Key.F5 -> "F5"
        Key.F6 -> "F6"
        Key.F7 -> "F7"
        Key.F8 -> "F8"
        Key.F9 -> "F9"
        Key.F10 -> "F10"
        Key.F11 -> "F11"
        Key.F12 -> "F12"
        Key.Escape -> "Esc"
        Key.Enter -> "Enter"
        Key.Tab -> "Tab"
        Key.Backspace -> "Backspace"
        Key.Delete -> "Delete"
        Key.Insert -> "Insert"
        Key.MoveHome -> "Home"
        Key.MoveEnd -> "End"
        Key.PageUp -> "Page Up"
        Key.PageDown -> "Page Down"
        Key.DirectionUp -> "↑"
        Key.DirectionDown -> "↓"
        Key.DirectionLeft -> "←"
        Key.DirectionRight -> "→"
        Key.Spacebar -> "Space"
        else -> this.toString().substringAfter("Key: ")
    }

/**
 * Navigation action identifiers.
 */
public object NavigationActions {
    // Symbol Search
    public const val GOTO_CLASS: String = "navigation.gotoClass"
    public const val GOTO_FILE: String = "navigation.gotoFile"
    public const val GOTO_SYMBOL: String = "navigation.gotoSymbol"
    public const val SEARCH_EVERYWHERE: String = "navigation.searchEverywhere"

    // Definition Navigation
    public const val GOTO_DECLARATION: String = "navigation.gotoDeclaration"
    public const val GOTO_TYPE_DECLARATION: String = "navigation.gotoTypeDeclaration"
    public const val GOTO_IMPLEMENTATION: String = "navigation.gotoImplementation"
    public const val GOTO_SUPER: String = "navigation.gotoSuper"
    public const val QUICK_DEFINITION: String = "navigation.quickDefinition"

    // Usage Navigation
    public const val FIND_USAGES: String = "navigation.findUsages"
    public const val SHOW_USAGES: String = "navigation.showUsages"
    public const val HIGHLIGHT_USAGES: String = "navigation.highlightUsages"
    public const val NEXT_HIGHLIGHTED: String = "navigation.nextHighlighted"
    public const val PREV_HIGHLIGHTED: String = "navigation.prevHighlighted"

    // Structure Navigation
    public const val FILE_STRUCTURE: String = "navigation.fileStructure"
    public const val SELECT_IN: String = "navigation.selectIn"
    public const val NAVIGATION_BAR: String = "navigation.navigationBar"

    // History Navigation
    public const val RECENT_FILES: String = "navigation.recentFiles"
    public const val RECENT_LOCATIONS: String = "navigation.recentLocations"
    public const val BACK: String = "navigation.back"
    public const val FORWARD: String = "navigation.forward"
    public const val LAST_EDIT_LOCATION: String = "navigation.lastEditLocation"

    // Hierarchy
    public const val CALL_HIERARCHY: String = "navigation.callHierarchy"
    public const val TYPE_HIERARCHY: String = "navigation.typeHierarchy"

    // Editor Actions
    public const val NEXT_ERROR: String = "navigation.nextError"
    public const val PREV_ERROR: String = "navigation.prevError"
    public const val NEXT_METHOD: String = "navigation.nextMethod"
    public const val PREV_METHOD: String = "navigation.prevMethod"
}

/**
 * Default keymap with IntelliJ-style shortcuts.
 */
public object DefaultKeymap {
    /**
     * All default shortcuts.
     */
    public val shortcuts: Map<String, KeyboardShortcut> =
        mapOf(
            // Symbol Search
            NavigationActions.GOTO_CLASS to KeyboardShortcut(Key.N, ctrl = true),
            NavigationActions.GOTO_FILE to KeyboardShortcut(Key.N, ctrl = true, shift = true),
            NavigationActions.GOTO_SYMBOL to KeyboardShortcut(Key.N, ctrl = true, shift = true, alt = true),
            NavigationActions.SEARCH_EVERYWHERE to KeyboardShortcut(Key.E, ctrl = true, shift = true, alt = true),
            // Definition Navigation
            NavigationActions.GOTO_DECLARATION to KeyboardShortcut(Key.B, ctrl = true),
            NavigationActions.GOTO_TYPE_DECLARATION to KeyboardShortcut(Key.B, ctrl = true, shift = true),
            NavigationActions.GOTO_IMPLEMENTATION to KeyboardShortcut(Key.B, ctrl = true, alt = true),
            NavigationActions.GOTO_SUPER to KeyboardShortcut(Key.U, ctrl = true),
            NavigationActions.QUICK_DEFINITION to KeyboardShortcut(Key.I, ctrl = true, shift = true),
            // Usage Navigation
            NavigationActions.FIND_USAGES to KeyboardShortcut(Key.F7, alt = true),
            NavigationActions.SHOW_USAGES to KeyboardShortcut(Key.F7, ctrl = true, alt = true),
            NavigationActions.HIGHLIGHT_USAGES to KeyboardShortcut(Key.F7, ctrl = true, shift = true),
            NavigationActions.NEXT_HIGHLIGHTED to KeyboardShortcut(Key.F3),
            NavigationActions.PREV_HIGHLIGHTED to KeyboardShortcut(Key.F3, shift = true),
            // Structure Navigation
            NavigationActions.FILE_STRUCTURE to KeyboardShortcut(Key.F12, ctrl = true),
            NavigationActions.SELECT_IN to KeyboardShortcut(Key.F1, alt = true),
            NavigationActions.NAVIGATION_BAR to KeyboardShortcut(Key.MoveHome, alt = true),
            // History Navigation
            NavigationActions.RECENT_FILES to KeyboardShortcut(Key.E, ctrl = true),
            NavigationActions.RECENT_LOCATIONS to KeyboardShortcut(Key.E, ctrl = true, shift = true),
            NavigationActions.BACK to KeyboardShortcut(Key.DirectionLeft, ctrl = true, alt = true),
            NavigationActions.FORWARD to KeyboardShortcut(Key.DirectionRight, ctrl = true, alt = true),
            NavigationActions.LAST_EDIT_LOCATION to KeyboardShortcut(Key.Backspace, ctrl = true, shift = true),
            // Hierarchy
            NavigationActions.CALL_HIERARCHY to KeyboardShortcut(Key.H, ctrl = true, alt = true),
            NavigationActions.TYPE_HIERARCHY to KeyboardShortcut(Key.H, ctrl = true),
            // Editor Navigation
            NavigationActions.NEXT_ERROR to KeyboardShortcut(Key.F2),
            NavigationActions.PREV_ERROR to KeyboardShortcut(Key.F2, shift = true),
            NavigationActions.NEXT_METHOD to KeyboardShortcut(Key.DirectionDown, alt = true),
            NavigationActions.PREV_METHOD to KeyboardShortcut(Key.DirectionUp, alt = true),
        )

    /**
     * Get the shortcut for an action.
     */
    public fun getShortcut(action: String): KeyboardShortcut? = shortcuts[action]

    /**
     * Find the action that matches a key event.
     */
    public fun findAction(event: KeyEvent): String? = shortcuts.entries.find { it.value.matches(event) }?.key
}

/**
 * Keymap manager that supports custom keymaps.
 */
public class KeymapManager {
    private val customShortcuts = mutableMapOf<String, KeyboardShortcut>()

    /**
     * Get the effective shortcut for an action.
     */
    public fun getShortcut(action: String): KeyboardShortcut? =
        customShortcuts[action] ?: DefaultKeymap.getShortcut(action)

    /**
     * Set a custom shortcut for an action.
     */
    public fun setShortcut(
        action: String,
        shortcut: KeyboardShortcut,
    ) {
        customShortcuts[action] = shortcut
    }

    /**
     * Remove a custom shortcut (revert to default).
     */
    public fun removeCustomShortcut(action: String) {
        customShortcuts.remove(action)
    }

    /**
     * Find the action that matches a key event.
     */
    public fun findAction(event: KeyEvent): String? {
        // Check custom shortcuts first
        customShortcuts.entries.find { it.value.matches(event) }?.let { return it.key }
        // Fall back to default
        return DefaultKeymap.findAction(event)
    }

    /**
     * Get all actions with their shortcuts.
     */
    public fun getAllShortcuts(): Map<String, KeyboardShortcut> = DefaultKeymap.shortcuts + customShortcuts
}
