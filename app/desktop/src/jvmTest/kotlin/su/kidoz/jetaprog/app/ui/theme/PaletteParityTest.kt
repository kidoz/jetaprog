package su.kidoz.jetaprog.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the dark/light palette contract: key parity between the two token
 * objects is enforced by the compiler (both implement [IntelliJPalette]); these
 * tests pin the identity flags and the semantic distinctness of key slots so an
 * accidental copy-paste between palettes cannot slip through.
 */
public class PaletteParityTest {
    @Test
    fun `dark palette identifies itself as dark`() {
        assertTrue(IntelliJColors.isDark)
        assertFalse(IntelliJLightColors.isDark)
    }

    @Test
    fun `key surface slots differ between palettes`() {
        assertNotEquals(IntelliJColors.background, IntelliJLightColors.background)
        assertNotEquals(IntelliJColors.surface, IntelliJLightColors.surface)
        assertNotEquals(IntelliJColors.textPrimary, IntelliJLightColors.textPrimary)
        assertNotEquals(IntelliJColors.accent, IntelliJLightColors.accent)
        assertNotEquals(IntelliJColors.popupBackground, IntelliJLightColors.popupBackground)
        assertNotEquals(
            IntelliJColors.treeSelectionBackground,
            IntelliJLightColors.treeSelectionBackground,
        )
    }

    @Test
    fun `os window chrome colors are shared constants`() {
        // Traffic lights are OS constants, identical in both palettes by design.
        assertEquals(IntelliJColors.windowCloseButton, IntelliJLightColors.windowCloseButton)
        assertEquals(IntelliJColors.windowMinimizeButton, IntelliJLightColors.windowMinimizeButton)
        assertEquals(IntelliJColors.windowZoomButton, IntelliJLightColors.windowZoomButton)
    }
}
