package su.kidoz.jetaprog.app.keymap

import androidx.compose.ui.input.key.Key
import su.kidoz.jetaprog.settings.model.KeymapSettings
import su.kidoz.jetaprog.settings.model.ShortcutSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the observable [KeymapManager] and the
 * [KeyboardShortcut]/[ShortcutSpec] persistence round-trip.
 */
class KeymapManagerTest {
    @Test
    fun shortcutSpecRoundTriipsThroughSettings() {
        val shortcut = KeyboardShortcut(Key.N, ctrl = true, shift = true)

        assertEquals(shortcut, shortcut.toSpec().toKeyboardShortcut())
    }

    @Test
    fun unknownKeyCodeYieldsNullShortcut() {
        val spec = ShortcutSpec(keyCode = Long.MAX_VALUE, ctrl = true)

        assertNull(spec.toKeyboardShortcut())
    }

    @Test
    fun customShortcutsOverrideDefaultsAndRevertOnReset() {
        val manager = KeymapManager()
        assertEquals(
            DefaultKeymap.getShortcut(NavigationActions.GOTO_FILE),
            manager.getShortcut(NavigationActions.GOTO_FILE),
        )

        val custom = KeyboardShortcut(Key.P, ctrl = true)
        manager.setShortcut(NavigationActions.GOTO_FILE, custom)
        assertEquals(custom, manager.getShortcut(NavigationActions.GOTO_FILE))
        assertTrue(manager.isCustomized(NavigationActions.GOTO_FILE))

        manager.removeCustomShortcut(NavigationActions.GOTO_FILE)
        assertEquals(
            DefaultKeymap.getShortcut(NavigationActions.GOTO_FILE),
            manager.getShortcut(NavigationActions.GOTO_FILE),
        )
        assertFalse(manager.isCustomized(NavigationActions.GOTO_FILE))
    }

    @Test
    fun applyFromSettingsReplacesAllOverrides() {
        val manager = KeymapManager()
        val override =
            KeymapSettings(
                customShortcuts =
                    mapOf(
                        NavigationActions.RENAME to KeyboardShortcut(Key.R, alt = true).toSpec(),
                        NavigationActions.BACK to ShortcutSpec(keyCode = Long.MAX_VALUE),
                    ),
            )

        manager.applyFromSettings(override)

        assertEquals(
            KeyboardShortcut(Key.R, alt = true),
            manager.getShortcut(NavigationActions.RENAME),
            "valid persisted overrides must be applied",
        )
        assertNull(manager.customShortcuts.value[NavigationActions.BACK], "unknown key codes must be dropped")
        assertEquals(
            DefaultKeymap.getShortcut(NavigationActions.GOTO_FILE),
            manager.getShortcut(NavigationActions.GOTO_FILE),
        )

        manager.applyFromSettings(KeymapSettings.DEFAULT)
        assertTrue(manager.customShortcuts.value.isEmpty())
        assertEquals(
            DefaultKeymap.getShortcut(NavigationActions.RENAME),
            manager.getShortcut(NavigationActions.RENAME),
        )
    }

    @Test
    fun toSettingsSerializesCurrentOverrides() {
        val manager = KeymapManager()
        manager.setShortcut(CommandActions.COMMAND_PALETTE, KeyboardShortcut(Key.K, ctrl = true, shift = true))

        val persisted = manager.toSettings()

        assertEquals(
            KeyboardShortcut(Key.K, ctrl = true, shift = true),
            persisted.customShortcuts[CommandActions.COMMAND_PALETTE]?.toKeyboardShortcut(),
        )
    }
}
